import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

interface EnqueueRequest {
  cadastroId: string | null;
  idFuncionario: number;
  idDependente: number;
  arquivoPath: string;
  arquivoNome: string;
  tipo: "titular" | "dependente";
  bucket?: string;
}

const KNOWN_QUEUE_STATUSES = ["queued", "retry_wait", "processing", "failed", "success"];
const WAITING_MESSAGE = "Aguardando envio do anexo ao ERP.";

function triggerQueueProcessor(supabaseUrl: string, serviceRoleKey: string) {
  const promise = fetch(`${supabaseUrl}/functions/v1/erp-process-upload-queue`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${serviceRoleKey}`,
    },
    body: "{}",
  }).catch((error) => {
    console.warn("Falha ao disparar processamento imediato da fila:", error);
  });

  const edgeRuntime = (globalThis as any).EdgeRuntime;
  if (edgeRuntime?.waitUntil) edgeRuntime.waitUntil(promise);
}

async function keepCadastroPending(supabase: any, cadastroId: string | null) {
  if (!cadastroId) return;
  const { error } = await supabase
    .from("cadastros")
    .update({ status: "incompleto", motivo_bloqueio: WAITING_MESSAGE })
    .eq("id", cadastroId);
  if (error) console.warn(`Falha ao manter cadastro ${cadastroId} pendente ate o anexo:`, error.message);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const supabaseClient = createClient(supabaseUrl, serviceRoleKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Autorização necessária" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const token = authHeader.replace("Bearer ", "");
    const { data: { user }, error: authError } = await supabaseClient.auth.getUser(token);
    if (authError || !user) {
      return new Response(JSON.stringify({ error: "Token inválido" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: profile } = await supabaseClient
      .from("profiles")
      .select("id, role")
      .eq("id", user.id)
      .single();
    if (!profile) {
      return new Response(JSON.stringify({ error: "Perfil não encontrado" }), {
        status: 404,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const body: EnqueueRequest = await req.json();
    const bucket = body.bucket?.trim() || "cadastros-temp-files";
    const arquivoPath = body.arquivoPath?.trim();
    const arquivoNome = body.arquivoNome?.trim();

    if (!body.idFuncionario || !body.idDependente || !arquivoPath || !arquivoNome || !["titular", "dependente"].includes(body.tipo)) {
      return new Response(JSON.stringify({ error: "Parâmetros obrigatórios faltando ou inválidos" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    let createdBy = user.id;
    if (body.cadastroId) {
      const { data: cadastro } = await supabaseClient
        .from("cadastros")
        .select("id, created_by")
        .eq("id", body.cadastroId)
        .single();
      if (cadastro) createdBy = cadastro.created_by || user.id;
    }

    const { data: existingItems, error: existingError } = await supabaseClient
      .from("erp_upload_queue")
      .select("*")
      .eq("id_funcionario", body.idFuncionario)
      .eq("id_dependente", body.idDependente)
      .eq("arquivo_path", arquivoPath)
      .eq("bucket", bucket)
      .in("status", KNOWN_QUEUE_STATUSES)
      .order("created_at", { ascending: false })
      .limit(1);

    if (existingError) console.warn("Não foi possível consultar upload existente:", existingError.message);

    const existingItem = existingItems?.[0];
    if (existingItem) {
      if (existingItem.status === "success") {
        if (body.cadastroId && !existingItem.cadastro_id) {
          await supabaseClient
            .from("erp_upload_queue")
            .update({ cadastro_id: body.cadastroId, created_by: createdBy })
            .eq("id", existingItem.id);
        }

        return new Response(JSON.stringify({
          queued: true,
          reused: true,
          delivered: true,
          queue_id: existingItem.id,
          message: "Este contrato já foi entregue ao ERP. Nenhum novo envio foi criado.",
        }), {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      if (existingItem.status !== "processing") {
        const isManualRestartAfterFinalFailure = existingItem.status === "failed";
        const linkedCadastroId = body.cadastroId || existingItem.cadastro_id;
        const { data: reusedItem, error: reuseError } = await supabaseClient
          .from("erp_upload_queue")
          .update({
            cadastro_id: linkedCadastroId,
            status: "queued",
            attempts: isManualRestartAfterFinalFailure ? 0 : existingItem.attempts,
            next_attempt_at: new Date().toISOString(),
            last_error: null,
            last_status_code: null,
            arquivo_nome: arquivoNome,
            tipo: body.tipo,
            created_by: createdBy,
          })
          .eq("id", existingItem.id)
          .select()
          .single();

        if (reuseError || !reusedItem) {
          return new Response(JSON.stringify({ error: "Erro ao reativar upload", details: reuseError?.message }), {
            status: 500,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
          });
        }

        await keepCadastroPending(supabaseClient, linkedCadastroId);
        triggerQueueProcessor(supabaseUrl, serviceRoleKey);
        return new Response(JSON.stringify({
          queued: true,
          reused: true,
          delivered: false,
          queue_id: reusedItem.id,
          message: "Contrato armazenado e retry reativado. O cadastro permanecera pendente ate a confirmacao do ERP.",
        }), {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const linkedCadastroId = body.cadastroId || existingItem.cadastro_id;
      if (body.cadastroId && !existingItem.cadastro_id) {
        await supabaseClient
          .from("erp_upload_queue")
          .update({ cadastro_id: body.cadastroId, created_by: createdBy })
          .eq("id", existingItem.id);
      }
      await keepCadastroPending(supabaseClient, linkedCadastroId);

      return new Response(JSON.stringify({
        queued: true,
        reused: true,
        processing: true,
        delivered: false,
        queue_id: existingItem.id,
        message: "Este contrato já está sendo enviado. O cadastro permanecera pendente ate a confirmacao do ERP.",
      }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const queueItem = {
      cadastro_id: body.cadastroId,
      created_by: createdBy,
      id_funcionario: body.idFuncionario,
      id_dependente: body.idDependente,
      arquivo_path: arquivoPath,
      arquivo_nome: arquivoNome,
      bucket,
      tipo: body.tipo,
      status: "queued",
      attempts: 0,
      next_attempt_at: new Date().toISOString(),
    };

    const { data: queueData, error: queueError } = await supabaseClient
      .from("erp_upload_queue")
      .insert(queueItem)
      .select()
      .single();

    if (queueError) {
      return new Response(JSON.stringify({ error: "Erro ao enfileirar upload", details: queueError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    await keepCadastroPending(supabaseClient, body.cadastroId);
    triggerQueueProcessor(supabaseUrl, serviceRoleKey);

    return new Response(JSON.stringify({
      queued: true,
      reused: false,
      delivered: false,
      queue_id: queueData.id,
      message: "Contrato enfileirado. O cadastro so sera concluido apos a confirmacao do anexo no ERP.",
    }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("Erro na função erp-enqueue-upload:", error);
    return new Response(JSON.stringify({
      error: "Erro interno",
      details: error instanceof Error ? error.message : String(error),
    }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
