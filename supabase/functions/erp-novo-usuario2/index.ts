import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey, X-Idempotency-Key, X-Cadastro-Id",
};

const ENDPOINT_NAME = "erp-novo-usuario2";
const UPLOAD_BUCKET = "cadastros-temp-files";
const UPLOAD_QUEUE_STATUSES = ["queued", "retry_wait", "processing", "failed", "success"];

type IdempotencyRow = {
  endpoint: string;
  idempotency_key: string;
  lock_token: string | null;
  status: "processing" | "completed" | "failed";
  response_body: unknown;
  status_code: number | null;
  error_message: string | null;
};

type AttachmentQueueResult = {
  required: boolean;
  queued: boolean;
  reused?: boolean;
  delivered?: boolean;
  processing?: boolean;
  queueId?: string;
  idFuncionario?: number;
  idDependente?: number;
  arquivoPath?: string;
  error?: string;
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
  }
) {
  try {
    await supabase.from("api_logs").insert(logData);
  } catch (error) {
    console.error("Error saving log:", error);
  }
}

const isUuid = (value: string) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);

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

const toPositiveInt = (value: unknown): number | null => {
  if (typeof value === "number" && Number.isInteger(value) && value > 0) return value;
  if (typeof value !== "string") return null;
  const parsed = Number(value.trim());
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
};

const extractFuncionarioCadastro = (body: any): number | null => {
  const dependentes = Array.isArray(body?.dados?.dependente) ? body.dados.dependente : [];
  for (const dependente of dependentes) {
    const codigo = toPositiveInt(dependente?.funcionarioCadastro);
    if (codigo) return codigo;
  }
  return null;
};

const extractDependenteCodigo = (payload: any): number | null => {
  const candidateArrays = [
    payload?.dados?.dependentes,
    payload?.data?.dados?.dependentes,
    payload?.data?.data?.dados?.dependentes,
  ];

  for (const candidate of candidateArrays) {
    if (!Array.isArray(candidate)) continue;
    for (const item of candidate) {
      const codigo = toPositiveInt(item?.codigo);
      if (codigo) return codigo;
    }
  }

  return null;
};

const extractDocumentRequest = (body: any): { arquivoPath: string; arquivoNome: string } | null => {
  const documento = body?.dados?.documento;
  const arquivoPath = typeof documento?.caminho === "string" ? documento.caminho.trim() : "";
  if (!arquivoPath) return null;

  const arquivoNome =
    typeof documento?.nome === "string" && documento.nome.trim()
      ? documento.nome.trim()
      : arquivoPath.split("/").filter(Boolean).pop() || "contrato.pdf";

  return { arquivoPath, arquivoNome };
};

function triggerUploadQueueProcessor(supabaseUrl: string, serviceRoleKey: string) {
  const promise = fetch(`${supabaseUrl}/functions/v1/erp-process-upload-queue`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${serviceRoleKey}`,
    },
    body: "{}",
  }).catch((error) => {
    console.warn("Falha ao disparar processamento imediato da fila de anexos:", error);
  });

  const edgeRuntime = (globalThis as any).EdgeRuntime;
  if (edgeRuntime?.waitUntil) {
    edgeRuntime.waitUntil(promise);
  }
}

async function keepCadastroPendingForAttachment(supabase: any, cadastroId: string) {
  if (!cadastroId || !isUuid(cadastroId)) return;
  const { error } = await supabase
    .from("cadastros")
    .update({
      status: "incompleto",
      motivo_bloqueio: "Aguardando envio do anexo ao ERP.",
    })
    .eq("id", cadastroId);
  if (error) console.warn(`Falha ao manter cadastro ${cadastroId} pendente pelo anexo:`, error.message);
}

async function ensureAttachmentQueued(
  supabase: any,
  supabaseUrl: string,
  serviceRoleKey: string,
  cadastroId: string,
  userId: string | undefined,
  requestBody: any,
  erpPayload: any,
): Promise<AttachmentQueueResult> {
  const documento = extractDocumentRequest(requestBody);
  if (!documento) {
    return { required: false, queued: false };
  }

  const idFuncionario = extractFuncionarioCadastro(requestBody);
  const idDependente = extractDependenteCodigo(erpPayload);

  if (!idFuncionario || !idDependente) {
    const error = !idFuncionario
      ? "ERP criou o cadastro, mas nao foi possivel identificar o funcionario responsavel pelo upload do anexo."
      : "ERP criou o cadastro, mas nao retornou o codigo do dependente necessario para enviar o anexo.";
    console.error(error, {
      cadastroId,
      hasFuncionario: Boolean(idFuncionario),
      hasDependente: Boolean(idDependente),
      arquivoPath: documento.arquivoPath,
    });
    return {
      required: true,
      queued: false,
      idFuncionario: idFuncionario ?? undefined,
      idDependente: idDependente ?? undefined,
      arquivoPath: documento.arquivoPath,
      error,
    };
  }

  let createdBy = userId ?? null;
  if (cadastroId && isUuid(cadastroId)) {
    const { data: cadastro } = await supabase
      .from("cadastros")
      .select("created_by")
      .eq("id", cadastroId)
      .maybeSingle();
    createdBy = cadastro?.created_by || createdBy;
  }

  const { data: existingItems, error: existingError } = await supabase
    .from("erp_upload_queue")
    .select("*")
    .eq("id_funcionario", idFuncionario)
    .eq("id_dependente", idDependente)
    .eq("arquivo_path", documento.arquivoPath)
    .eq("bucket", UPLOAD_BUCKET)
    .in("status", UPLOAD_QUEUE_STATUSES)
    .order("created_at", { ascending: false })
    .limit(1);

  if (existingError) {
    console.warn("Falha ao consultar fila existente antes do enqueue automatico:", existingError.message);
  }

  const existingItem = existingItems?.[0];
  if (existingItem) {
    if (existingItem.status === "success") {
      return {
        required: true,
        queued: true,
        reused: true,
        delivered: true,
        queueId: existingItem.id,
        idFuncionario,
        idDependente,
        arquivoPath: documento.arquivoPath,
      };
    }

    if (existingItem.status === "processing") {
      if (cadastroId && !existingItem.cadastro_id) {
        await supabase
          .from("erp_upload_queue")
          .update({ cadastro_id: cadastroId, created_by: createdBy })
          .eq("id", existingItem.id);
      }

      await keepCadastroPendingForAttachment(supabase, cadastroId);
      return {
        required: true,
        queued: true,
        reused: true,
        processing: true,
        queueId: existingItem.id,
        idFuncionario,
        idDependente,
        arquivoPath: documento.arquivoPath,
      };
    }

    const isFinalFailure = existingItem.status === "failed";
    const { data: reusedItem, error: reuseError } = await supabase
      .from("erp_upload_queue")
      .update({
        cadastro_id: cadastroId || existingItem.cadastro_id,
        created_by: createdBy,
        status: "queued",
        attempts: isFinalFailure ? 0 : existingItem.attempts,
        next_attempt_at: new Date().toISOString(),
        last_error: null,
        last_status_code: null,
        arquivo_nome: documento.arquivoNome,
        tipo: "titular",
      })
      .eq("id", existingItem.id)
      .select()
      .single();

    if (reuseError || !reusedItem) {
      const error = `Cadastro criado no ERP, mas falhou ao reativar a fila do anexo: ${reuseError?.message || "erro desconhecido"}`;
      console.error(error);
      return {
        required: true,
        queued: false,
        idFuncionario,
        idDependente,
        arquivoPath: documento.arquivoPath,
        error,
      };
    }

    await keepCadastroPendingForAttachment(supabase, cadastroId);
    triggerUploadQueueProcessor(supabaseUrl, serviceRoleKey);
    return {
      required: true,
      queued: true,
      reused: true,
      queueId: reusedItem.id,
      idFuncionario,
      idDependente,
      arquivoPath: documento.arquivoPath,
    };
  }

  const { data: queueItem, error: queueError } = await supabase
    .from("erp_upload_queue")
    .insert({
      cadastro_id: cadastroId && isUuid(cadastroId) ? cadastroId : null,
      created_by: createdBy,
      id_funcionario: idFuncionario,
      id_dependente: idDependente,
      arquivo_path: documento.arquivoPath,
      arquivo_nome: documento.arquivoNome,
      bucket: UPLOAD_BUCKET,
      tipo: "titular",
      status: "queued",
      attempts: 0,
      next_attempt_at: new Date().toISOString(),
    })
    .select()
    .single();

  if (queueError || !queueItem) {
    const error = `Cadastro criado no ERP, mas falhou ao enfileirar o anexo: ${queueError?.message || "erro desconhecido"}`;
    console.error(error);
    return {
      required: true,
      queued: false,
      idFuncionario,
      idDependente,
      arquivoPath: documento.arquivoPath,
      error,
    };
  }

  await keepCadastroPendingForAttachment(supabase, cadastroId);
  triggerUploadQueueProcessor(supabaseUrl, serviceRoleKey);
  return {
    required: true,
    queued: true,
    reused: false,
    queueId: queueItem.id,
    idFuncionario,
    idDependente,
    arquivoPath: documento.arquivoPath,
  };
}

const unwrapStoredErpPayload = (value: any): any => {
  if (
    value &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    (value.success !== undefined || value.erpCreated !== undefined) &&
    value.data !== undefined
  ) {
    return value.data;
  }
  return value;
};

const buildResponseWithAttachment = (
  erpPayload: any,
  attachment: AttachmentQueueResult,
  extra: Record<string, unknown> = {},
) => {
  if (attachment.required && !attachment.queued) {
    return {
      success: false,
      erpCreated: true,
      data: erpPayload,
      attachmentQueue: attachment,
      error: attachment.error || "Cadastro criado no ERP, mas o anexo ainda nao foi enfileirado.",
      ...extra,
    };
  }

  return {
    success: true,
    data: erpPayload,
    attachmentQueue: attachment,
    ...extra,
  };
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
    const ERP_URL = Deno.env.get("ERP_URL") || "https://odontoart.s4e.com.br/api/vendedor/NovoUsuario2";

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
        endpoint: "erp-novo-usuario2",
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
        endpoint: "erp-novo-usuario2",
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

    if (cadastroIdHeader && isUuid(cadastroIdHeader)) {
      const { data: existingCadastro, error: existingCadastroError } = await supabase
        .from("cadastros")
        .select("status, erp_response")
        .eq("id", cadastroIdHeader)
        .maybeSingle();

      if (!existingCadastroError && existingCadastro?.status === "enviado" && existingCadastro?.erp_response) {
        const erpPayload = unwrapStoredErpPayload(existingCadastro.erp_response);
        const attachment = await ensureAttachmentQueued(
          supabase,
          supabaseUrl,
          supabaseServiceKey,
          cadastroIdHeader,
          userId,
          requestBody,
          erpPayload,
        );
        responseBody = buildResponseWithAttachment(erpPayload, attachment, {
          idempotent: true,
          reused: true,
          reuseSource: "cadastros",
        });
        statusCode = attachment.required && !attachment.queued ? 503 : 200;
        errorMessage = statusCode === 200 ? undefined : responseBody.error;

        await saveLog(supabase, {
          user_id: userId,
          user_email: userEmail,
          endpoint: "erp-novo-usuario2",
          method: "POST",
          request_body: enrichRequestBodyForLog(requestBody),
          response_body: responseBody,
          status_code: statusCode,
          success: statusCode === 200,
          error_message: errorMessage,
          duration_ms: Date.now() - startTime,
        });

        return new Response(JSON.stringify(responseBody), {
          status: statusCode,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
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
          const erpPayload = unwrapStoredErpPayload(idempotencyRow.response_body);
          const attachment = await ensureAttachmentQueued(
            supabase,
            supabaseUrl,
            supabaseServiceKey,
            cadastroIdHeader,
            userId,
            requestBody,
            erpPayload,
          );
          responseBody = buildResponseWithAttachment(erpPayload, attachment, {
            idempotent: true,
            reused: true,
            reuseSource: "idempotency_keys",
          });
          statusCode = attachment.required && !attachment.queued ? 503 : 200;
          errorMessage = statusCode === 200 ? undefined : responseBody.error;

          await saveLog(supabase, {
            user_id: userId,
            user_email: userEmail,
            endpoint: ENDPOINT_NAME,
            method: "POST",
            request_body: enrichRequestBodyForLog(requestBody),
            response_body: responseBody,
            status_code: statusCode,
            success: statusCode === 200,
            error_message: errorMessage,
            duration_ms: Date.now() - startTime,
          });

          return new Response(JSON.stringify(responseBody), {
            status: statusCode,
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

    const erpResponse = await fetch(ERP_URL, {
      method: "POST",
      headers: {
        token: ERP_TOKEN,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(requestBody),
    });

    const responseData = await erpResponse.json();
    statusCode = erpResponse.status;

    const hasDadosCodigo = responseData?.dados?.codigo || responseData?.data?.dados?.codigo;

    if (!hasDadosCodigo) {
      if (!erpResponse.ok) {
        errorMessage = extractErpMessage(responseData) || "Erro ao enviar cadastro para o ERP";
      } else {
        errorMessage = extractErpMessage(responseData) || "Erro no cadastro: dados invalidos retornados pelo ERP";
      }

      responseBody = {
        error: errorMessage,
        details: responseData,
        status: statusCode,
      };
      await finalizeIdempotency("failed", responseBody, statusCode, errorMessage ?? null);

      await saveLog(supabase, {
        user_id: userId,
        user_email: userEmail,
        endpoint: "erp-novo-usuario2",
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

    const attachment = await ensureAttachmentQueued(
      supabase,
      supabaseUrl,
      supabaseServiceKey,
      cadastroIdHeader,
      userId,
      requestBody,
      responseData,
    );

    responseBody = buildResponseWithAttachment(responseData, attachment);
    statusCode = attachment.required && !attachment.queued ? 503 : 200;
    errorMessage = statusCode === 200 ? undefined : responseBody.error;

    await finalizeIdempotency(
      "completed",
      responseBody,
      statusCode,
      errorMessage ?? null,
    );

    await saveLog(supabase, {
      user_id: userId,
      user_email: userEmail,
      endpoint: "erp-novo-usuario2",
      method: "POST",
      request_body: enrichRequestBodyForLog(requestBody),
      response_body: responseBody,
      status_code: statusCode,
      success: statusCode === 200,
      error_message: errorMessage,
      duration_ms: Date.now() - startTime,
    });

    return new Response(JSON.stringify(responseBody), {
      status: statusCode,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("Error in erp-novo-usuario2:", error);
    statusCode = 500;
    errorMessage = error instanceof Error ? error.message : "Erro interno do servidor";
    responseBody = {
      error: errorMessage,
    };

    await finalizeIdempotency("failed", responseBody, statusCode, errorMessage ?? null);

    await saveLog(supabase, {
      user_id: userId,
      user_email: userEmail,
      endpoint: "erp-novo-usuario2",
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
