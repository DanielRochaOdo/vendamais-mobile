import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers":
    "Content-Type, Authorization, X-Client-Info, Apikey, X-Idempotency-Key, X-Cadastro-Id",
};

const ENDPOINT_NAME = "erp-novo-dependente";

type IdempotencyRow = {
  endpoint: string;
  idempotency_key: string;
  lock_token: string | null;
  status: "processing" | "completed" | "failed";
  response_body: unknown;
  status_code: number | null;
  error_message: string | null;
};

async function saveLog(
  supabase: any,
  logData: {
    user_id?: string;
    user_email?: string;
    endpoint: string;
    method: string;
    request_body: any;
    response_body?: any;
    status_code?: number;
    success: boolean;
    error_message?: string;
    duration_ms: number;
  },
) {
  try {
    await supabase.from("api_logs").insert(logData);
  } catch (error) {
    console.error("Error saving log:", error);
  }
}

const extractErpMessage = (payload: any): string | null => {
  if (!payload || typeof payload !== "object") return null;

  const candidates = [
    payload.message,
    payload.mensagem,
    payload.error,
    payload.data?.message,
    payload.data?.mensagem,
    payload.details?.message,
    payload.details?.mensagem,
    Array.isArray(payload.errors) ? payload.errors[0] : null,
    Array.isArray(payload.data?.errors) ? payload.data.errors[0] : null,
    Array.isArray(payload.details?.errors) ? payload.details.errors[0] : null,
  ];

  for (const candidate of candidates) {
    if (typeof candidate === "string" && candidate.trim()) {
      return candidate.trim();
    }
  }

  return null;
};

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

  const explicitIdempotencyKey =
    req.headers.get("X-Idempotency-Key")?.trim() ||
    req.headers.get("x-idempotency-key")?.trim() ||
    "";
  const cadastroIdHeader =
    req.headers.get("X-Cadastro-Id")?.trim() ||
    req.headers.get("x-cadastro-id")?.trim() ||
    "";

  let idempotencyKey = explicitIdempotencyKey;
  let lockToken: string | null = null;
  let ownsIdempotencyLock = false;

  const enrichRequestBodyForLog = (body: any) => {
    if (body && typeof body === "object" && !Array.isArray(body)) {
      return {
        ...body,
        ...(idempotencyKey ? { idempotency_key: idempotencyKey } : {}),
        ...(cadastroIdHeader ? { cadastro_id: cadastroIdHeader } : {}),
      };
    }

    return {
      payload: body,
      ...(idempotencyKey ? { idempotency_key: idempotencyKey } : {}),
      ...(cadastroIdHeader ? { cadastro_id: cadastroIdHeader } : {}),
    };
  };

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supabase = createClient(supabaseUrl, supabaseServiceKey);

  const finalizeIdempotency = async (
    status: "completed" | "failed",
    response: unknown,
    responseStatusCode: number,
    responseError: string | null = null,
  ) => {
    if (!ownsIdempotencyLock || !idempotencyKey || !lockToken) return;

    await supabase
      .from("erp_idempotency_keys")
      .update({
        status,
        response_body: response,
        status_code: responseStatusCode,
        error_message: responseError,
        updated_at: new Date().toISOString(),
      })
      .eq("endpoint", ENDPOINT_NAME)
      .eq("idempotency_key", idempotencyKey)
      .eq("lock_token", lockToken);
  };

  try {
    const authHeader = req.headers.get("Authorization");
    if (authHeader) {
      const token = authHeader.replace("Bearer ", "");
      const {
        data: { user },
      } = await supabase.auth.getUser(token);
      if (user) {
        userId = user.id;
        userEmail = user.email;
      }
    }

    const ERP_TOKEN = Deno.env.get("ERP_TOKEN");
    const ERP_URL =
      Deno.env.get("ERP_URL_NOVO_DEPENDENTE") ||
      "https://odontoart.s4e.com.br/api/vendedor/NovoDependente";

    if (!ERP_TOKEN) {
      throw new Error("ERP_TOKEN not configured");
    }

    requestBody = await req.json();

    if (!requestBody || !requestBody.dados) {
      statusCode = 400;
      errorMessage = "Payload invalido: campo 'dados' e obrigatorio";
      responseBody = { error: errorMessage };

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: ENDPOINT_NAME,
        method: "POST",
        request_body: enrichRequestBodyForLog(requestBody),
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

    if (!requestBody.dados.responsavelFinanceiro) {
      statusCode = 400;
      errorMessage = "Payload invalido: 'responsavelFinanceiro' e obrigatorio";
      responseBody = { error: errorMessage };

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: ENDPOINT_NAME,
        method: "POST",
        request_body: enrichRequestBodyForLog(requestBody),
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

    if (!requestBody.dados.parceiro || !requestBody.dados.parceiro.codigo) {
      statusCode = 400;
      errorMessage = "Payload invalido: 'parceiro.codigo' e obrigatorio";
      responseBody = { error: errorMessage };

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: ENDPOINT_NAME,
        method: "POST",
        request_body: enrichRequestBodyForLog(requestBody),
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

    if (idempotencyKey) {
      lockToken = crypto.randomUUID();

      const upsertRes = await supabase.from("erp_idempotency_keys").upsert(
        {
          endpoint: ENDPOINT_NAME,
          idempotency_key: idempotencyKey,
          user_id: userId ?? null,
          status: "processing",
          lock_token: lockToken,
          updated_at: new Date().toISOString(),
        },
        {
          onConflict: "endpoint,idempotency_key",
          ignoreDuplicates: true,
        },
      );

      if (upsertRes.error) {
        console.warn("idempotency upsert failed:", upsertRes.error.message);
      }

      const { data: idempotencyRowRaw, error: idempotencyFetchError } = await supabase
        .from("erp_idempotency_keys")
        .select("endpoint,idempotency_key,lock_token,status,response_body,status_code,error_message")
        .eq("endpoint", ENDPOINT_NAME)
        .eq("idempotency_key", idempotencyKey)
        .maybeSingle();

      const idempotencyRow = idempotencyRowRaw as IdempotencyRow | null;

      if (!idempotencyFetchError && idempotencyRow) {
        if (idempotencyRow.lock_token === lockToken && idempotencyRow.status === "processing") {
          ownsIdempotencyLock = true;
        } else if (idempotencyRow.status === "completed" && idempotencyRow.response_body) {
          responseBody =
            typeof idempotencyRow.response_body === "object" && idempotencyRow.response_body !== null
              ? { ...(idempotencyRow.response_body as Record<string, unknown>), idempotent: true, reused: true, reuseSource: "idempotency_keys" }
              : { success: true, data: idempotencyRow.response_body, idempotent: true, reused: true, reuseSource: "idempotency_keys" };

          await saveLog(supabase, {
            user_id: userId,
            user_email: userEmail,
            endpoint: ENDPOINT_NAME,
            method: "POST",
            request_body: enrichRequestBodyForLog(requestBody),
            response_body: responseBody,
            status_code: 200,
            success: true,
            duration_ms: Date.now() - startTime,
          });

          return new Response(JSON.stringify(responseBody), {
            status: 200,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
          });
        } else if (idempotencyRow.status === "failed") {
          statusCode = idempotencyRow.status_code ?? 400;
          errorMessage = idempotencyRow.error_message ?? "Falha anterior com a mesma chave de idempotencia.";
          responseBody =
            idempotencyRow.response_body && typeof idempotencyRow.response_body === "object"
              ? idempotencyRow.response_body
              : { error: errorMessage };

          await saveLog(supabase, {
            user_id: userId,
            user_email: userEmail,
            endpoint: ENDPOINT_NAME,
            method: "POST",
            request_body: enrichRequestBodyForLog(requestBody),
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
        } else {
          statusCode = 409;
          errorMessage = "Requisicao ja em processamento. Aguarde alguns segundos e tente novamente.";
          responseBody = { error: errorMessage, status: statusCode };

          await saveLog(supabase, {
            user_id: userId,
            user_email: userEmail,
            endpoint: ENDPOINT_NAME,
            method: "POST",
            request_body: enrichRequestBodyForLog(requestBody),
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
      }
    }

    const payload = {
      token: ERP_TOKEN,
      dados: requestBody.dados,
    };

    const erpResponse = await fetch(ERP_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });

    const responseData = await erpResponse.json();
    statusCode = erpResponse.status;

    if (!erpResponse.ok) {
      errorMessage = extractErpMessage(responseData) || "Erro ao incluir dependente no ERP";
      responseBody = {
        error: errorMessage,
        details: responseData,
        status: statusCode,
      };

      await finalizeIdempotency("failed", responseBody, statusCode, errorMessage);

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: ENDPOINT_NAME,
        method: "POST",
        request_body: enrichRequestBodyForLog(requestBody),
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

    if (!responseData.dados) {
      errorMessage = extractErpMessage(responseData) || "Erro: API retornou status 200 mas sem dados validos";
      statusCode = 400;
      responseBody = {
        error: errorMessage,
        details: responseData,
      };

      await finalizeIdempotency("failed", responseBody, statusCode, errorMessage);

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: ENDPOINT_NAME,
        method: "POST",
        request_body: enrichRequestBodyForLog(requestBody),
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

    await finalizeIdempotency("completed", responseBody, 200, null);

    await saveLog(supabase, {
      user_id: userId,
      user_email: userEmail,
      endpoint: ENDPOINT_NAME,
      method: "POST",
      request_body: enrichRequestBodyForLog(requestBody),
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
    console.error("Error in erp-novo-dependente:", error);
    statusCode = 500;
    errorMessage = error instanceof Error ? error.message : "Erro interno do servidor";
    responseBody = {
      error: errorMessage,
    };

    await finalizeIdempotency("failed", responseBody, statusCode, errorMessage);

    await saveLog(supabase, {
      user_id: userId,
      user_email: userEmail,
      endpoint: ENDPOINT_NAME,
      method: "POST",
      request_body: enrichRequestBodyForLog(requestBody),
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
