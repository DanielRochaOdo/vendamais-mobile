import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey, X-Queue-Worker-Token",
};

const MAX_ATTEMPTS = 5;
const ERP_UPLOAD_TIMEOUT_MS = 30_000;
const MAX_ERP_FILE_BYTES = 5 * 1024 * 1024;
const BATCH_LIMIT = 12;
const CONCURRENCY = 4;
const STUCK_THRESHOLD_MINUTES = 10;
const ADMIN_ROLES = new Set(["ADMINISTRADOR", "ADMIN", "GERENTE", "GESTOR"]);

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

type UploadResult = {
  success: boolean;
  error?: string;
  statusCode?: number;
  response?: unknown;
  retryable?: boolean;
};

type ItemOutcome = {
  id: string;
  state: "success" | "retry" | "failed" | "skipped" | "commit_error";
  error?: string;
  attempts?: number;
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
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

function normalizeErpMessage(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function extractErpSemanticError(payload: any): { message: string; retryable: boolean } | null {
  if (!payload || typeof payload !== "object") return null;

  const message = normalizeErpMessage(payload.mensagem || payload.message || payload.error);
  const normalized = message
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
  const codigo = Number(payload.codigo);
  const erros = payload.erros ?? payload.errors;
  const hasErrors = Array.isArray(erros)
    ? erros.length > 0
    : Boolean(erros && (typeof erros !== "object" || Object.keys(erros).length > 0));
  const messageLooksLikeError = /(erro|falha|excede|limite|maxim|inval|nao|obrigat|indisponivel)/.test(normalized);
  const codeLooksLikeError = Number.isFinite(codigo) && codigo >= 2;

  if (!hasErrors && !messageLooksLikeError && !codeLooksLikeError) return null;

  const tooLarge = /5\s*mb/.test(normalized) && /(excede|limite|maxim)/.test(normalized);
  return {
    message: message || `ERP recusou o anexo${Number.isFinite(codigo) ? ` (codigo ${codigo})` : ""}.`,
    retryable: !tooLarge,
  };
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
}

function retryDelayMinutes(attempts: number): number {
  const schedule = [1, 2, 5, 10];
  return schedule[Math.min(Math.max(attempts - 1, 0), schedule.length - 1)];
}

async function authorizeRequest(
  req: Request,
  supabase: any,
  serviceRoleKey: string,
): Promise<{ allowed: boolean; source?: string; reason?: string }> {
  const authHeader = req.headers.get("Authorization")?.trim() || "";
  const bearerToken = authHeader.toLowerCase().startsWith("bearer ")
    ? authHeader.slice(7).trim()
    : "";

  if (bearerToken && bearerToken === serviceRoleKey) {
    return { allowed: true, source: "service_role" };
  }

  const workerToken = req.headers.get("X-Queue-Worker-Token")?.trim() || "";
  if (workerToken) {
    const { data: control, error } = await supabase
      .from("erp_upload_worker_control")
      .select("worker_token")
      .eq("id", true)
      .maybeSingle();

    if (!error && control?.worker_token && String(control.worker_token) === workerToken) {
      return { allowed: true, source: "cron" };
    }
  }

  if (!bearerToken) {
    return { allowed: false, reason: "Autorizacao necessaria" };
  }

  const { data: { user }, error: authError } = await supabase.auth.getUser(bearerToken);
  if (authError || !user) {
    return { allowed: false, reason: "Token invalido" };
  }

  const { data: profile } = await supabase
    .from("profiles")
    .select("role")
    .eq("id", user.id)
    .maybeSingle();

  const role = String(profile?.role || "").toUpperCase();
  if (!ADMIN_ROLES.has(role)) {
    return { allowed: false, reason: "Usuario sem permissao para processar a fila" };
  }

  return { allowed: true, source: `user:${role}` };
}

async function uploadToErp(supabase: any, item: QueueItem): Promise<UploadResult> {
  const { data: fileData, error: downloadError } = await supabase.storage
    .from(item.bucket)
    .download(item.arquivo_path);

  if (downloadError || !fileData) {
    return {
      success: false,
      error: `Erro ao baixar arquivo: ${downloadError?.message || "Arquivo nao encontrado"}`,
      statusCode: 404,
    };
  }

  const ERP_TOKEN = Deno.env.get("ERP_TOKEN");
  const ERP_ENDPOINT = Deno.env.get("ERP_ENDPOINT") || "https://odontoart.s4e.com.br";
  if (!ERP_TOKEN) {
    return { success: false, error: "ERP_TOKEN nao configurado", statusCode: 500 };
  }

  const bytes = new Uint8Array(await fileData.arrayBuffer());
  if (bytes.byteLength > MAX_ERP_FILE_BYTES) {
    return {
      success: false,
      retryable: false,
      error: `Arquivo excede o limite maximo de 5 MB aceito pelo ERP (${(bytes.byteLength / 1024 / 1024).toFixed(2)} MB).`,
      statusCode: 413,
    };
  }
  const ERP_URL = `${ERP_ENDPOINT}/api/dependente/UploadDocDependente?token=${ERP_TOKEN}`;

  let erpResponse: Response;
  try {
    erpResponse = await fetchWithTimeout(
      ERP_URL,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          idFuncionario: item.id_funcionario,
          idDependente: item.id_dependente,
          arquivo: bytesToBase64(bytes),
          arquivoNome: item.arquivo_nome,
        }),
      },
      ERP_UPLOAD_TIMEOUT_MS,
    );
  } catch (error) {
    const timedOut = error instanceof DOMException && error.name === "AbortError";
    return {
      success: false,
      error: timedOut
        ? `Timeout no ERP apos ${ERP_UPLOAD_TIMEOUT_MS}ms`
        : `Falha de rede no ERP: ${error instanceof Error ? error.message : String(error)}`,
      statusCode: timedOut ? 504 : 502,
    };
  }

  const responseData = await readResponsePayload(erpResponse);
  if (!erpResponse.ok) {
    return {
      success: false,
      error: responseData?.message || responseData?.mensagem || responseData?.error || "Erro ao enviar documento para o ERP",
      statusCode: erpResponse.status,
      response: responseData,
    };
  }

  const semanticError = extractErpSemanticError(responseData);
  if (semanticError) {
    return {
      success: false,
      retryable: semanticError.retryable,
      error: semanticError.message,
      statusCode: erpResponse.status,
      response: responseData,
    };
  }

  return {
    success: true,
    statusCode: erpResponse.status,
    response: responseData,
  };
}

async function markCadastroWaiting(supabase: any, cadastroId: string | null, message: string) {
  if (!cadastroId) return;
  const { error } = await supabase
    .from("cadastros")
    .update({ status: "incompleto", motivo_bloqueio: message })
    .eq("id", cadastroId);
  if (error) console.warn(`Falha ao manter cadastro ${cadastroId} pendente:`, error.message);
}

async function markCadastroFailed(supabase: any, cadastroId: string | null, message: string) {
  if (!cadastroId) return;
  const { error } = await supabase
    .from("cadastros")
    .update({ status: "erro_envio", motivo_bloqueio: message })
    .eq("id", cadastroId);
  if (error) console.warn(`Falha ao marcar cadastro ${cadastroId} com erro:`, error.message);
}

async function reconcileCadastroIfComplete(supabase: any, cadastroId: string | null) {
  if (!cadastroId) return;

  const { data: pendingRows, error: pendingError } = await supabase
    .from("erp_upload_queue")
    .select("id,status")
    .eq("cadastro_id", cadastroId)
    .neq("status", "success")
    .limit(1);

  if (pendingError) {
    console.warn(`Falha ao reconciliar fila do cadastro ${cadastroId}:`, pendingError.message);
    return;
  }

  if ((pendingRows || []).length > 0) return;

  const sentAt = new Date().toISOString();
  let { error } = await supabase
    .from("cadastros")
    .update({
      status: "enviado",
      motivo_bloqueio: null,
      data_envio: sentAt,
    })
    .eq("id", cadastroId);

  if (error?.message?.includes("data_envio")) {
    const retry = await supabase
      .from("cadastros")
      .update({ status: "enviado", motivo_bloqueio: null })
      .eq("id", cadastroId);
    error = retry.error;
  }

  if (error) {
    console.warn(`Documento entregue, mas falhou ao reconciliar cadastro ${cadastroId}:`, error.message);
  }
}

async function processOne(supabase: any, item: QueueItem): Promise<ItemOutcome> {
  const lockedAt = new Date().toISOString();
  const { data: lockedRows, error: lockError } = await supabase
    .from("erp_upload_queue")
    .update({ status: "processing", last_attempt_at: lockedAt })
    .eq("id", item.id)
    .eq("status", item.status)
    .select("id");

  if (lockError || !lockedRows || lockedRows.length === 0) {
    return { id: item.id, state: "skipped", error: lockError?.message };
  }

  const result = await uploadToErp(supabase, item);
  const newAttempts = item.attempts + 1;
  const now = new Date().toISOString();

  if (result.success) {
    // IMPORTANT: primeiro confirma success no banco. O arquivo so e removido do Storage depois.
    const { data: committedRows, error: commitError } = await supabase
      .from("erp_upload_queue")
      .update({
        status: "success",
        attempts: newAttempts,
        next_attempt_at: now,
        last_attempt_at: now,
        last_error: null,
        last_status_code: result.statusCode || 200,
        erp_response: result.response || {},
      })
      .eq("id", item.id)
      .eq("status", "processing")
      .select("id");

    if (commitError || !committedRows || committedRows.length === 0) {
      console.error(`ERP recebeu o documento, mas o commit da fila falhou para ${item.id}:`, commitError);
      return {
        id: item.id,
        state: "commit_error",
        attempts: newAttempts,
        error: commitError?.message || "Nao foi possivel confirmar success no banco",
      };
    }

    await reconcileCadastroIfComplete(supabase, item.cadastro_id);

    const { error: deleteError } = await supabase.storage
      .from(item.bucket)
      .remove([item.arquivo_path]);
    if (deleteError) {
      console.warn(`Upload ${item.id} confirmado; arquivo temporario nao pode ser removido:`, deleteError.message);
    }

    return { id: item.id, state: "success", attempts: newAttempts };
  }

  const isFinalFailure = result.retryable === false || newAttempts >= MAX_ATTEMPTS;
  const nextAttempt = new Date();
  if (!isFinalFailure) nextAttempt.setMinutes(nextAttempt.getMinutes() + retryDelayMinutes(newAttempts));

  const nextStatus = isFinalFailure ? "failed" : "retry_wait";
  const { error: updateError } = await supabase
    .from("erp_upload_queue")
    .update({
      status: nextStatus,
      attempts: newAttempts,
      last_attempt_at: now,
      next_attempt_at: isFinalFailure ? now : nextAttempt.toISOString(),
      last_error: result.error || "Falha desconhecida no ERP",
      last_status_code: result.statusCode || 500,
      erp_response: result.response || {},
    })
    .eq("id", item.id)
    .eq("status", "processing");

  if (updateError) {
    console.error(`Falha ao persistir retry do item ${item.id}:`, updateError.message);
  }

  const cadastroMessage = isFinalFailure
    ? `Falha definitiva no envio do anexo ao ERP apos ${MAX_ATTEMPTS} tentativas: ${result.error || "erro desconhecido"}`
    : `Aguardando envio do anexo ao ERP. Tentativa ${newAttempts}/${MAX_ATTEMPTS}.`;

  if (isFinalFailure) {
    await markCadastroFailed(supabase, item.cadastro_id, cadastroMessage);
  } else {
    await markCadastroWaiting(supabase, item.cadastro_id, cadastroMessage);
  }

  return {
    id: item.id,
    state: isFinalFailure ? "failed" : "retry",
    attempts: newAttempts,
    error: result.error,
  };
}

async function processWithConcurrency(supabase: any, items: QueueItem[]): Promise<ItemOutcome[]> {
  const outcomes: ItemOutcome[] = new Array(items.length);
  let nextIndex = 0;

  const runners = Array.from({ length: Math.min(CONCURRENCY, items.length) }, async () => {
    while (true) {
      const index = nextIndex++;
      if (index >= items.length) return;
      outcomes[index] = await processOne(supabase, items[index]);
    }
  });

  await Promise.all(runners);
  return outcomes;
}

async function countDueItems(supabase: any): Promise<number> {
  const now = new Date().toISOString();
  const { count, error } = await supabase
    .from("erp_upload_queue")
    .select("id", { count: "exact", head: true })
    .in("status", ["queued", "retry_wait"])
    .lt("attempts", MAX_ATTEMPTS)
    .lte("next_attempt_at", now);
  if (error) {
    console.warn("Falha ao contar itens pendentes:", error.message);
    return 0;
  }
  return count || 0;
}

function triggerNextBatch(supabaseUrl: string, serviceRoleKey: string) {
  const promise = fetch(`${supabaseUrl}/functions/v1/erp-process-upload-queue`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${serviceRoleKey}`,
    },
    body: JSON.stringify({ chained: true }),
  }).catch((error) => console.warn("Falha ao encadear proximo lote da fila:", error));

  const edgeRuntime = (globalThis as any).EdgeRuntime;
  if (edgeRuntime?.waitUntil) edgeRuntime.waitUntil(promise);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: corsHeaders });
  }
  if (req.method !== "POST") return jsonResponse({ error: "Metodo nao permitido" }, 405);

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    if (!supabaseUrl || !serviceRoleKey) {
      return jsonResponse({ error: "Configuracao interna do Supabase ausente" }, 500);
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const auth = await authorizeRequest(req, supabase, serviceRoleKey);
    if (!auth.allowed) return jsonResponse({ error: auth.reason || "Nao autorizado" }, 403);

    const { data: resetResult, error: resetError } = await supabase
      .rpc("reset_stuck_queue_items", { stuck_threshold_minutes: STUCK_THRESHOLD_MINUTES });
    if (resetError) console.warn("Falha ao resetar itens travados:", resetError.message);
    const resetCount = Array.isArray(resetResult) ? Number(resetResult[0]?.reset_count || 0) : 0;

    const now = new Date().toISOString();
    const { data: queueItems, error: fetchError } = await supabase
      .from("erp_upload_queue")
      .select("*")
      .in("status", ["queued", "retry_wait"])
      .lt("attempts", MAX_ATTEMPTS)
      .lte("next_attempt_at", now)
      .order("created_at", { ascending: true })
      .limit(BATCH_LIMIT);

    if (fetchError) {
      return jsonResponse({ error: "Erro ao buscar fila", details: fetchError.message }, 500);
    }

    const items = (queueItems || []) as QueueItem[];
    if (items.length === 0) {
      return jsonResponse({
        message: "Nenhum item devido para processamento",
        queued_count: 0,
        reset_count: resetCount,
        remaining_due: 0,
        results: { processed: 0, success: 0, retry: 0, failed: 0, skipped: 0, commit_error: 0 },
      });
    }

    const outcomes = await processWithConcurrency(supabase, items);
    const summary = {
      processed: outcomes.filter((item) => item.state !== "skipped").length,
      success: outcomes.filter((item) => item.state === "success").length,
      retry: outcomes.filter((item) => item.state === "retry").length,
      failed: outcomes.filter((item) => item.state === "failed").length,
      skipped: outcomes.filter((item) => item.state === "skipped").length,
      commit_error: outcomes.filter((item) => item.state === "commit_error").length,
    };

    const remainingDue = await countDueItems(supabase);
    if (remainingDue > 0) triggerNextBatch(supabaseUrl, serviceRoleKey);

    return jsonResponse({
      message: "Lote processado",
      auth_source: auth.source,
      queued_count: items.length,
      reset_count: resetCount,
      remaining_due: remainingDue,
      results: summary,
      errors: outcomes.filter((item) => item.error),
    });
  } catch (error) {
    console.error("Erro no worker de processamento:", error);
    return jsonResponse({
      error: "Erro no worker",
      details: error instanceof Error ? error.message : String(error),
    }, 500);
  }
});
