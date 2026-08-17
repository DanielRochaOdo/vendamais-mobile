import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

const MAX_ATTEMPTS = 5;
const DEFAULT_ERP_UPLOAD_TIMEOUT_MS = 12_000;
const BATCH_LIMIT = 8;

interface QueueItem {
  id: string;
  cadastro_id: string | null;
  id_funcionario: number;
  id_dependente: number;
  arquivo_path: string;
  arquivo_nome: string;
  bucket: string;
  tipo: string;
  attempts: number;
  status: string;
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunkSize = 8192;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const chunk = bytes.subarray(i, Math.min(i + chunkSize, bytes.length));
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

async function readResponsePayload(response: Response): Promise<any> {
  const text = await response.text();
  if (!text.trim()) return {};
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
}

function retryDelayMinutes(attempts: number): number {
  const schedule = [2, 4, 8, 16];
  return schedule[Math.min(Math.max(attempts - 1, 0), schedule.length - 1)];
}

async function processQueueItem(
  supabase: any,
  item: QueueItem,
): Promise<{ success: boolean; error?: string; statusCode?: number; response?: any }> {
  try {
    console.log(`Processando item ${item.id} - tentativa ${item.attempts + 1}/${MAX_ATTEMPTS}`);

    const { data: fileData, error: downloadError } = await supabase.storage
      .from(item.bucket)
      .download(item.arquivo_path);

    if (downloadError || !fileData) {
      console.error(`Erro ao baixar arquivo ${item.arquivo_path}:`, downloadError);
      return {
        success: false,
        error: `Erro ao baixar arquivo: ${downloadError?.message || "Arquivo não encontrado"}`,
        statusCode: 404,
      };
    }

    const bytes = new Uint8Array(await fileData.arrayBuffer());
    const base64 = bytesToBase64(bytes);

    const ERP_TOKEN = Deno.env.get("ERP_TOKEN");
    const ERP_ENDPOINT = Deno.env.get("ERP_ENDPOINT") || "https://odontoart.s4e.com.br";
    const ERP_URL = `${ERP_ENDPOINT}/api/dependente/UploadDocDependente?token=${ERP_TOKEN}`;

    if (!ERP_TOKEN) {
      return {
        success: false,
        error: "ERP_TOKEN não configurado",
        statusCode: 500,
      };
    }

    const erpPayload = {
      idFuncionario: item.id_funcionario,
      idDependente: item.id_dependente,
      arquivo: base64,
      arquivoNome: item.arquivo_nome,
    };

    console.log(`Enviando documento para ERP: ${item.arquivo_nome}`);

    let erpResponse: Response;
    try {
      erpResponse = await fetchWithTimeout(
        ERP_URL,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(erpPayload),
        },
        DEFAULT_ERP_UPLOAD_TIMEOUT_MS,
      );
    } catch (error) {
      const timedOut = error instanceof DOMException && error.name === "AbortError";
      return {
        success: false,
        error: timedOut
          ? `Timeout no ERP após ${DEFAULT_ERP_UPLOAD_TIMEOUT_MS}ms`
          : `Falha de rede no ERP: ${error instanceof Error ? error.message : String(error)}`,
        statusCode: timedOut ? 504 : 502,
      };
    }

    const responseData = await readResponsePayload(erpResponse);
    const statusCode = erpResponse.status;

    if (!erpResponse.ok) {
      const errorMsg = responseData.message || responseData?.mensagem || responseData?.error || "Erro ao enviar documento para o ERP";
      console.error(`Erro no ERP (${statusCode}):`, errorMsg);
      return {
        success: false,
        error: errorMsg,
        statusCode,
        response: responseData,
      };
    }

    console.log(`Documento enviado com sucesso. Removendo ${item.arquivo_path} do bucket...`);

    const { error: deleteError } = await supabase.storage
      .from(item.bucket)
      .remove([item.arquivo_path]);

    if (deleteError) {
      console.warn(`Não foi possível deletar arquivo ${item.arquivo_path}:`, deleteError);
    }

    return {
      success: true,
      statusCode: 200,
      response: responseData,
    };
  } catch (error) {
    console.error(`Erro ao processar item ${item.id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : "Erro desconhecido",
      statusCode: 500,
    };
  }
}

async function processQueueInBackground(supabaseClient: any, queueItems: QueueItem[]) {
  console.log(`Processando ${queueItems.length} item(ns) da fila...`);

  const results = {
    processed: 0,
    success: 0,
    failed: 0,
    retry: 0,
    skipped: 0,
    errors: [] as any[],
  };

  for (const item of queueItems) {
    const { data: lockedRows, error: lockError } = await supabaseClient
      .from("erp_upload_queue")
      .update({ status: "processing", last_attempt_at: new Date().toISOString() })
      .eq("id", item.id)
      .eq("status", item.status)
      .select("id");

    if (lockError) {
      console.warn(`Falha ao adquirir lock do item ${item.id}:`, lockError);
      results.skipped++;
      continue;
    }

    if (!lockedRows || lockedRows.length === 0) {
      console.log(`Item ${item.id} já foi adquirido por outro worker.`);
      results.skipped++;
      continue;
    }

    results.processed++;
    const result = await processQueueItem(supabaseClient, item);
    const newAttempts = item.attempts + 1;

    if (result.success) {
      const { error: successUpdateError } = await supabaseClient
        .from("erp_upload_queue")
        .update({
          status: "success",
          attempts: newAttempts,
          next_attempt_at: new Date().toISOString(),
          last_attempt_at: new Date().toISOString(),
          last_error: null,
          erp_response: result.response,
          last_status_code: result.statusCode,
        })
        .eq("id", item.id)
        .eq("status", "processing");

      if (successUpdateError) {
        console.error(`Documento enviado, mas falhou ao marcar item ${item.id} como success:`, successUpdateError);
      }

      results.success++;
      console.log(`✓ Item ${item.id} processado com sucesso`);
      continue;
    }

    const isFinalFailure = newAttempts >= MAX_ATTEMPTS;
    const newStatus = isFinalFailure ? "failed" : "retry_wait";
    const nextAttempt = new Date();
    if (!isFinalFailure) {
      nextAttempt.setMinutes(nextAttempt.getMinutes() + retryDelayMinutes(newAttempts));
    }

    const { error: retryUpdateError } = await supabaseClient
      .from("erp_upload_queue")
      .update({
        status: newStatus,
        attempts: newAttempts,
        last_attempt_at: new Date().toISOString(),
        next_attempt_at: isFinalFailure ? new Date().toISOString() : nextAttempt.toISOString(),
        last_error: result.error,
        last_status_code: result.statusCode,
        erp_response: result.response,
      })
      .eq("id", item.id)
      .eq("status", "processing");

    if (retryUpdateError) {
      console.error(`Falha ao atualizar estado de retry do item ${item.id}:`, retryUpdateError);
    }

    if (isFinalFailure) {
      results.failed++;
      console.error(`✗ Item ${item.id} falhou após ${MAX_ATTEMPTS} tentativas`);
    } else {
      results.retry++;
      console.warn(`⟳ Item ${item.id} terá nova tentativa em ${retryDelayMinutes(newAttempts)} minuto(s)`);
    }

    results.errors.push({
      id: item.id,
      error: result.error,
      attempts: newAttempts,
      final_failure: isFinalFailure,
    });
  }

  console.log("Processamento da fila concluído:", results);
  return results;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 200,
      headers: corsHeaders,
    });
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
      {
        auth: {
          autoRefreshToken: false,
          persistSession: false,
        },
      },
    );

    console.log("Verificando itens travados...");
    const { data: resetResult, error: resetError } = await supabaseClient
      .rpc("reset_stuck_queue_items", { stuck_threshold_minutes: 15 });

    if (resetError) {
      console.warn("Erro ao resetar itens travados:", resetError);
    } else if (resetResult && resetResult.length > 0) {
      const { reset_count } = resetResult[0];
      if (reset_count > 0) {
        console.log(`✓ ${reset_count} item(ns) travado(s) foram resetados`);
      }
    }

    const now = new Date().toISOString();
    const { data: queueItems, error: fetchError } = await supabaseClient
      .from("erp_upload_queue")
      .select("*")
      .in("status", ["queued", "retry_wait"])
      .lt("attempts", MAX_ATTEMPTS)
      .lte("next_attempt_at", now)
      .order("created_at", { ascending: true })
      .limit(BATCH_LIMIT);

    if (fetchError) {
      console.error("Erro ao buscar itens da fila:", fetchError);
      return new Response(
        JSON.stringify({ error: "Erro ao buscar fila", details: fetchError.message }),
        {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        },
      );
    }

    if (!queueItems || queueItems.length === 0) {
      return new Response(
        JSON.stringify({
          message: "Nenhum item na fila para processar",
          queued_count: 0,
        }),
        {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        },
      );
    }

    const backgroundPromise = processQueueInBackground(supabaseClient, queueItems as QueueItem[]);
    const edgeRuntime = (globalThis as any).EdgeRuntime;

    if (edgeRuntime?.waitUntil) {
      edgeRuntime.waitUntil(backgroundPromise);
      return new Response(
        JSON.stringify({
          message: "Processamento iniciado em background",
          queued_count: queueItems.length,
          note: "O worker foi registrado com waitUntil e continuará após esta resposta.",
        }),
        {
          status: 202,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        },
      );
    }

    const results = await backgroundPromise;
    return new Response(
      JSON.stringify({
        message: "Processamento concluído",
        queued_count: queueItems.length,
        results,
      }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  } catch (error) {
    console.error("Erro no worker de processamento:", error);
    return new Response(
      JSON.stringify({
        error: "Erro no worker",
        details: error instanceof Error ? error.message : "Erro desconhecido",
      }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  }
});
