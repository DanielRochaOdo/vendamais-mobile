import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import { corsHeaders, resolveRequestUser, saveLog } from "../_shared/api-utils.ts";

const DEFAULT_ERP_UPLOAD_TIMEOUT_MS = 12_000;
const MAX_ERP_FILE_BYTES = 5 * 1024 * 1024;
const MIN_ERP_UPLOAD_TIMEOUT_MS = 3_000;
const MAX_ERP_UPLOAD_TIMEOUT_MS = 45_000;
const RETRYABLE_QUEUE_STATUSES = ["queued", "retry_wait", "processing", "failed"];

function resolveErpUploadTimeoutMs(): number {
  const configured = Number(Deno.env.get("ERP_UPLOAD_TIMEOUT_MS"));
  if (!Number.isFinite(configured)) return DEFAULT_ERP_UPLOAD_TIMEOUT_MS;
  return Math.min(MAX_ERP_UPLOAD_TIMEOUT_MS, Math.max(MIN_ERP_UPLOAD_TIMEOUT_MS, configured));
}

function bytesToBase64(bytes: Uint8Array): string {
  let binaryString = "";
  const chunkSize = 8192;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const chunk = bytes.subarray(i, Math.min(i + chunkSize, bytes.length));
    binaryString += String.fromCharCode(...chunk);
  }
  return btoa(binaryString);
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
  const normalized = message.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
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

async function enqueueForRetry(
  supabase: any,
  requestBody: any,
  userId: string | undefined,
  arquivoNome: string,
  reason: string,
): Promise<{ queued: boolean; queueId?: string; reused?: boolean }> {
  const arquivoPath = requestBody.arquivoPath?.trim();
  if (!arquivoPath || !requestBody.idFuncionario || !requestBody.idDependente) {
    return { queued: false };
  }

  const bucket = requestBody.bucket?.trim() || "cadastros-temp-files";
  let existingQuery = supabase
    .from("erp_upload_queue")
    .select("*")
    .eq("id_funcionario", requestBody.idFuncionario)
    .eq("id_dependente", requestBody.idDependente)
    .eq("arquivo_path", arquivoPath)
    .eq("bucket", bucket)
    .in("status", RETRYABLE_QUEUE_STATUSES)
    .order("created_at", { ascending: false })
    .limit(1);

  existingQuery = requestBody.cadastroId
    ? existingQuery.eq("cadastro_id", requestBody.cadastroId)
    : existingQuery.is("cadastro_id", null);

  const { data: existingItems, error: existingError } = await existingQuery;
  if (existingError) {
    console.warn("Falha ao consultar retry existente no upload direto:", existingError);
  }

  const existingItem = existingItems?.[0];
  if (existingItem) {
    if (existingItem.status === "processing") {
      return { queued: true, queueId: existingItem.id, reused: true };
    }

    const { data: reusedItem, error: reuseError } = await supabase
      .from("erp_upload_queue")
      .update({
        status: "queued",
        attempts: existingItem.status === "failed" ? 0 : existingItem.attempts,
        next_attempt_at: new Date().toISOString(),
        last_error: reason,
        arquivo_nome: arquivoNome,
      })
      .eq("id", existingItem.id)
      .select("id")
      .single();

    if (!reuseError && reusedItem) {
      return { queued: true, queueId: reusedItem.id, reused: true };
    }
    console.warn("Falha ao reativar retry existente no upload direto:", reuseError);
  }

  const { data: queueData, error: queueError } = await supabase
    .from("erp_upload_queue")
    .insert({
      cadastro_id: requestBody.cadastroId || null,
      created_by: userId || null,
      id_funcionario: requestBody.idFuncionario,
      id_dependente: requestBody.idDependente,
      arquivo_path: arquivoPath,
      arquivo_nome: arquivoNome,
      bucket,
      tipo: requestBody.tipo === "dependente" ? "dependente" : "titular",
      status: "queued",
      attempts: 0,
      next_attempt_at: new Date().toISOString(),
      last_error: reason,
    })
    .select("id")
    .single();

  if (queueError || !queueData) {
    console.error("Falha ao enfileirar retry dentro do upload direto:", queueError);
    return { queued: false };
  }

  return { queued: true, queueId: queueData.id, reused: false };
}

function triggerQueueProcessor(supabaseUrl: string, serviceRoleKey: string) {
  const promise = fetch(`${supabaseUrl}/functions/v1/erp-process-upload-queue`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${serviceRoleKey}`,
    },
    body: "{}",
  }).catch((error) => {
    console.warn("Falha ao disparar worker após enqueue interno:", error);
  });

  const edgeRuntime = (globalThis as any).EdgeRuntime;
  if (edgeRuntime?.waitUntil) {
    edgeRuntime.waitUntil(promise);
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 200,
      headers: corsHeaders,
    });
  }

  const startTime = Date.now();
  let userId: string | undefined;
  let userEmail: string | undefined;
  let requestBody: any = {};
  let responseBody: any;
  let statusCode = 200;
  let errorMessage: string | undefined;

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supabase = createClient(supabaseUrl, supabaseServiceKey);

  try {
    ({ userId, userEmail } = await resolveRequestUser(supabase, req));

    const ERP_TOKEN = Deno.env.get("ERP_TOKEN");
    const ERP_ENDPOINT = Deno.env.get("ERP_ENDPOINT") || "https://odontoart.s4e.com.br";
    const ERP_URL = `${ERP_ENDPOINT}/api/dependente/UploadDocDependente?token=${ERP_TOKEN}`;

    if (!ERP_TOKEN) {
      throw new Error("ERP_TOKEN not configured");
    }

    requestBody = await req.json();

    if (!requestBody.idFuncionario || !requestBody.idDependente) {
      statusCode = 400;
      errorMessage = "Campos obrigatórios: idFuncionario, idDependente";
      responseBody = { error: errorMessage };

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: "erp-upload-documento",
        method: "POST",
        request_body: requestBody,
        response_body: responseBody,
        status_code: statusCode,
        success: false,
        error_message: errorMessage,
        duration_ms: Date.now() - startTime,
      });

      return new Response(JSON.stringify(responseBody), {
        status: statusCode,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    let arquivoBase64: string;
    let arquivoNome: string;

    if (requestBody.arquivoPath) {
      const bucket = requestBody.bucket || "cadastros-temp-files";
      const { data: fileData, error: downloadError } = await supabase.storage
        .from(bucket)
        .download(requestBody.arquivoPath);

      if (downloadError || !fileData) {
        errorMessage = `Erro ao baixar arquivo do storage: ${downloadError?.message || "Arquivo não encontrado"}`;
        const queued = await enqueueForRetry(
          supabase,
          requestBody,
          userId,
          requestBody.arquivoNome || requestBody.arquivoPath.split("/").pop() || "documento.pdf",
          errorMessage,
        );

        if (queued.queued) {
          triggerQueueProcessor(supabaseUrl, supabaseServiceKey);
          statusCode = 202;
          responseBody = {
            success: true,
            attached: false,
            queued: true,
            queue_id: queued.queueId,
            message: "Documento ainda não foi anexado ao ERP, mas ficou salvo para retry automático.",
          };
        } else {
          statusCode = 400;
          responseBody = { error: errorMessage, retryable: true };
        }

        await saveLog(supabase, {
          user_id: userId,
          user_email: userEmail,
          endpoint: "erp-upload-documento",
          method: "POST",
          request_body: requestBody,
          response_body: responseBody,
          status_code: statusCode,
          success: false,
          error_message: errorMessage,
          duration_ms: Date.now() - startTime,
        });

        return new Response(JSON.stringify(responseBody), {
          status: statusCode,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const bytes = new Uint8Array(await fileData.arrayBuffer());
      if (bytes.byteLength > MAX_ERP_FILE_BYTES) {
        errorMessage = `Arquivo excede o limite maximo de 5 MB aceito pelo ERP (${(bytes.byteLength / 1024 / 1024).toFixed(2)} MB).`;
        responseBody = { error: errorMessage, attached: false, retryable: false };
        await saveLog(supabase, {
          user_id: userId,
          user_email: userEmail,
          endpoint: "erp-upload-documento",
          method: "POST",
          request_body: requestBody,
          response_body: responseBody,
          status_code: 413,
          success: false,
          error_message: errorMessage,
          duration_ms: Date.now() - startTime,
        });
        return new Response(JSON.stringify(responseBody), {
          status: 413,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
      arquivoBase64 = bytesToBase64(bytes);
      arquivoNome = requestBody.arquivoNome || requestBody.arquivoPath.split("/").pop() || "documento.pdf";
    } else if (requestBody.arquivo) {
      arquivoBase64 = requestBody.arquivo;
      arquivoNome = requestBody.arquivoNome;

      if (!arquivoNome) {
        statusCode = 400;
        errorMessage = "Campo obrigatório: arquivoNome (quando usar arquivo base64)";
        responseBody = { error: errorMessage };

        await saveLog(supabase, {
          user_id: userId,
          user_email: userEmail,
          endpoint: "erp-upload-documento",
          method: "POST",
          request_body: requestBody,
          response_body: responseBody,
          status_code: statusCode,
          success: false,
          error_message: errorMessage,
          duration_ms: Date.now() - startTime,
        });

        return new Response(JSON.stringify(responseBody), {
          status: statusCode,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
    } else {
      statusCode = 400;
      errorMessage = "É necessário fornecer 'arquivo' (base64) ou 'arquivoPath' (storage path)";
      responseBody = { error: errorMessage };

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: "erp-upload-documento",
        method: "POST",
        request_body: requestBody,
        response_body: responseBody,
        status_code: statusCode,
        success: false,
        error_message: errorMessage,
        duration_ms: Date.now() - startTime,
      });

      return new Response(JSON.stringify(responseBody), {
        status: statusCode,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const erpPayload = {
      idFuncionario: requestBody.idFuncionario,
      idDependente: requestBody.idDependente,
      arquivo: arquivoBase64,
      arquivoNome,
    };

    const timeoutMs = resolveErpUploadTimeoutMs();
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
        timeoutMs,
      );
    } catch (error) {
      const timedOut = error instanceof DOMException && error.name === "AbortError";
      errorMessage = timedOut
        ? `Upload do documento no ERP excedeu ${timeoutMs}ms.`
        : `Falha de rede ao enviar documento para o ERP: ${error instanceof Error ? error.message : "erro desconhecido"}`;
      const queued = await enqueueForRetry(supabase, requestBody, userId, arquivoNome, errorMessage || "Falha no envio do anexo ao ERP");

      if (queued.queued) {
        triggerQueueProcessor(supabaseUrl, supabaseServiceKey);
        statusCode = 202;
        responseBody = {
          success: true,
          attached: false,
          queued: true,
          queue_id: queued.queueId,
          timeout: timedOut,
          message: "Cadastro pode continuar: o contrato ficou salvo e será reintegrado automaticamente ao ERP.",
        };
      } else {
        statusCode = timedOut ? 504 : 502;
        responseBody = {
          error: `${errorMessage} Não foi possível registrar o retry automático.`,
          retryable: true,
          timeout: timedOut,
        };
      }

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: "erp-upload-documento",
        method: "POST",
        request_body: requestBody,
        response_body: responseBody,
        status_code: statusCode,
        success: false,
        error_message: errorMessage,
        duration_ms: Date.now() - startTime,
      });

      return new Response(JSON.stringify(responseBody), {
        status: statusCode,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const responseData = await readResponsePayload(erpResponse);
    statusCode = erpResponse.status;

    if (!erpResponse.ok) {
      errorMessage = responseData.message || responseData?.mensagem || responseData?.error || "Erro ao enviar documento para o ERP";
      const queued = await enqueueForRetry(supabase, requestBody, userId, arquivoNome, errorMessage || "Falha no envio do anexo ao ERP");

      if (queued.queued) {
        triggerQueueProcessor(supabaseUrl, supabaseServiceKey);
        statusCode = 202;
        responseBody = {
          success: true,
          attached: false,
          queued: true,
          queue_id: queued.queueId,
          details: responseData,
          message: "O ERP não confirmou o anexo agora. O contrato ficou salvo para retry automático.",
        };
      } else {
        responseBody = {
          error: errorMessage,
          details: responseData,
          status: statusCode,
          retryable: true,
        };
      }

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: "erp-upload-documento",
        method: "POST",
        request_body: requestBody,
        response_body: responseBody,
        status_code: statusCode,
        success: false,
        error_message: errorMessage,
        duration_ms: Date.now() - startTime,
      });

      return new Response(JSON.stringify(responseBody), {
        status: statusCode,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const semanticError = extractErpSemanticError(responseData);
    if (semanticError) {
      errorMessage = semanticError.message;
      if (semanticError.retryable) {
        const queued = await enqueueForRetry(supabase, requestBody, userId, arquivoNome, errorMessage || "Falha no envio do anexo ao ERP");
        if (queued.queued) {
          triggerQueueProcessor(supabaseUrl, supabaseServiceKey);
          statusCode = 202;
          responseBody = {
            success: true,
            attached: false,
            queued: true,
            queue_id: queued.queueId,
            details: responseData,
            message: "O ERP respondeu HTTP 200, mas nao confirmou o anexo. O contrato ficou salvo para retry automatico.",
          };
        } else {
          statusCode = 422;
          responseBody = { error: errorMessage, attached: false, retryable: true, details: responseData };
        }
      } else {
        statusCode = 422;
        responseBody = { error: errorMessage, attached: false, retryable: false, details: responseData };
      }

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: "erp-upload-documento",
        method: "POST",
        request_body: requestBody,
        response_body: responseBody,
        status_code: statusCode,
        success: false,
        error_message: errorMessage,
        duration_ms: Date.now() - startTime,
      });

      return new Response(JSON.stringify(responseBody), {
        status: statusCode,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    responseBody = {
      success: true,
      attached: true,
      queued: false,
      data: responseData,
    };

    await saveLog(supabase, {
      user_id: userId,
      user_email: userEmail,
      endpoint: "erp-upload-documento",
      method: "POST",
      request_body: requestBody,
      response_body: responseBody,
      status_code: 200,
      success: true,
      duration_ms: Date.now() - startTime,
    });

    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("Error in erp-upload-documento:", error);
    statusCode = 500;
    errorMessage = error instanceof Error ? error.message : "Erro interno do servidor";
    responseBody = {
      error: errorMessage,
    };

    await saveLog(supabase, {
      user_id: userId,
      user_email: userEmail,
      endpoint: "erp-upload-documento",
      method: "POST",
      request_body: requestBody,
      response_body: responseBody,
      status_code: statusCode,
      success: false,
      error_message: errorMessage,
      duration_ms: Date.now() - startTime,
    });

    return new Response(JSON.stringify(responseBody), {
      status: statusCode,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
