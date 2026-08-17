import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import { corsHeaders, resolveRequestUser, saveLog } from "../_shared/api-utils.ts";

const DEFAULT_ERP_UPLOAD_TIMEOUT_MS = 12_000;
const MIN_ERP_UPLOAD_TIMEOUT_MS = 3_000;
const MAX_ERP_UPLOAD_TIMEOUT_MS = 45_000;

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
        statusCode = 400;
        errorMessage = `Erro ao baixar arquivo do storage: ${downloadError?.message || "Arquivo não encontrado"}`;
        responseBody = { error: errorMessage, retryable: true };

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
      statusCode = timedOut ? 504 : 502;
      errorMessage = timedOut
        ? `Upload do documento no ERP excedeu ${timeoutMs}ms. O aplicativo pode enfileirar o contrato para retry.`
        : `Falha de rede ao enviar documento para o ERP: ${error instanceof Error ? error.message : "erro desconhecido"}`;
      responseBody = {
        error: errorMessage,
        retryable: true,
        timeout: timedOut,
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

    const responseData = await readResponsePayload(erpResponse);
    statusCode = erpResponse.status;

    if (!erpResponse.ok) {
      errorMessage = responseData.message || responseData?.mensagem || responseData?.error || "Erro ao enviar documento para o ERP";
      responseBody = {
        error: errorMessage,
        details: responseData,
        status: statusCode,
        retryable: statusCode >= 408 || statusCode === 429,
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

    responseBody = {
      success: true,
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
