from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    path.write_text(text.replace(old, new, 1))
    print(f"updated {label}")


worker = Path("supabase/functions/erp-process-upload-queue/index.ts")
replace_once(
    worker,
    'const ERP_UPLOAD_TIMEOUT_MS = 30_000;\n',
    'const ERP_UPLOAD_TIMEOUT_MS = 30_000;\nconst MAX_ERP_FILE_BYTES = 5 * 1024 * 1024;\n',
    "worker file size constant",
)
replace_once(
    worker,
    '''type UploadResult = {\n  success: boolean;\n  error?: string;\n  statusCode?: number;\n  response?: unknown;\n};\n''',
    '''type UploadResult = {\n  success: boolean;\n  error?: string;\n  statusCode?: number;\n  response?: unknown;\n  retryable?: boolean;\n};\n''',
    "worker retryable result",
)
replace_once(
    worker,
    '''async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {\n''',
    '''function normalizeErpMessage(value: unknown): string {\n  return typeof value === "string" ? value.trim() : "";\n}\n\nfunction extractErpSemanticError(payload: any): { message: string; retryable: boolean } | null {\n  if (!payload || typeof payload !== "object") return null;\n\n  const message = normalizeErpMessage(payload.mensagem || payload.message || payload.error);\n  const normalized = message\n    .normalize("NFD")\n    .replace(/[\\u0300-\\u036f]/g, "")\n    .toLowerCase();\n  const codigo = Number(payload.codigo);\n  const erros = payload.erros ?? payload.errors;\n  const hasErrors = Array.isArray(erros)\n    ? erros.length > 0\n    : Boolean(erros && (typeof erros !== "object" || Object.keys(erros).length > 0));\n  const messageLooksLikeError = /(erro|falha|excede|limite|maxim|inval|nao|obrigat|indisponivel)/.test(normalized);\n  const codeLooksLikeError = Number.isFinite(codigo) && codigo >= 2;\n\n  if (!hasErrors && !messageLooksLikeError && !codeLooksLikeError) return null;\n\n  const tooLarge = /5\\s*mb/.test(normalized) && /(excede|limite|maxim)/.test(normalized);\n  return {\n    message: message || `ERP recusou o anexo${Number.isFinite(codigo) ? ` (codigo ${codigo})` : ""}.`,\n    retryable: !tooLarge,\n  };\n}\n\nasync function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {\n''',
    "worker semantic response helper",
)
replace_once(
    worker,
    '''  const bytes = new Uint8Array(await fileData.arrayBuffer());\n  const ERP_URL = `${ERP_ENDPOINT}/api/dependente/UploadDocDependente?token=${ERP_TOKEN}`;\n''',
    '''  const bytes = new Uint8Array(await fileData.arrayBuffer());\n  if (bytes.byteLength > MAX_ERP_FILE_BYTES) {\n    return {\n      success: false,\n      retryable: false,\n      error: `Arquivo excede o limite maximo de 5 MB aceito pelo ERP (${(bytes.byteLength / 1024 / 1024).toFixed(2)} MB).`,\n      statusCode: 413,\n    };\n  }\n  const ERP_URL = `${ERP_ENDPOINT}/api/dependente/UploadDocDependente?token=${ERP_TOKEN}`;\n''',
    "worker preflight file size",
)
replace_once(
    worker,
    '''  return {\n    success: true,\n    statusCode: erpResponse.status,\n    response: responseData,\n  };\n}\n\nasync function markCadastroWaiting''',
    '''  const semanticError = extractErpSemanticError(responseData);\n  if (semanticError) {\n    return {\n      success: false,\n      retryable: semanticError.retryable,\n      error: semanticError.message,\n      statusCode: erpResponse.status,\n      response: responseData,\n    };\n  }\n\n  return {\n    success: true,\n    statusCode: erpResponse.status,\n    response: responseData,\n  };\n}\n\nasync function markCadastroWaiting''',
    "worker semantic success gate",
)
replace_once(
    worker,
    '  const isFinalFailure = newAttempts >= MAX_ATTEMPTS;\n',
    '  const isFinalFailure = result.retryable === false || newAttempts >= MAX_ATTEMPTS;\n',
    "worker non retryable failures",
)

legacy = Path("supabase/functions/erp-upload-documento/index.ts")
replace_once(
    legacy,
    'const DEFAULT_ERP_UPLOAD_TIMEOUT_MS = 12_000;\n',
    'const DEFAULT_ERP_UPLOAD_TIMEOUT_MS = 12_000;\nconst MAX_ERP_FILE_BYTES = 5 * 1024 * 1024;\n',
    "legacy file size constant",
)
replace_once(
    legacy,
    '''async function fetchWithTimeout(\n''',
    '''function normalizeErpMessage(value: unknown): string {\n  return typeof value === "string" ? value.trim() : "";\n}\n\nfunction extractErpSemanticError(payload: any): { message: string; retryable: boolean } | null {\n  if (!payload || typeof payload !== "object") return null;\n  const message = normalizeErpMessage(payload.mensagem || payload.message || payload.error);\n  const normalized = message.normalize("NFD").replace(/[\\u0300-\\u036f]/g, "").toLowerCase();\n  const codigo = Number(payload.codigo);\n  const erros = payload.erros ?? payload.errors;\n  const hasErrors = Array.isArray(erros)\n    ? erros.length > 0\n    : Boolean(erros && (typeof erros !== "object" || Object.keys(erros).length > 0));\n  const messageLooksLikeError = /(erro|falha|excede|limite|maxim|inval|nao|obrigat|indisponivel)/.test(normalized);\n  const codeLooksLikeError = Number.isFinite(codigo) && codigo >= 2;\n  if (!hasErrors && !messageLooksLikeError && !codeLooksLikeError) return null;\n  const tooLarge = /5\\s*mb/.test(normalized) && /(excede|limite|maxim)/.test(normalized);\n  return {\n    message: message || `ERP recusou o anexo${Number.isFinite(codigo) ? ` (codigo ${codigo})` : ""}.`,\n    retryable: !tooLarge,\n  };\n}\n\nasync function fetchWithTimeout(\n''',
    "legacy semantic response helper",
)
replace_once(
    legacy,
    '''      const bytes = new Uint8Array(await fileData.arrayBuffer());\n      arquivoBase64 = bytesToBase64(bytes);\n''',
    '''      const bytes = new Uint8Array(await fileData.arrayBuffer());\n      if (bytes.byteLength > MAX_ERP_FILE_BYTES) {\n        errorMessage = `Arquivo excede o limite maximo de 5 MB aceito pelo ERP (${(bytes.byteLength / 1024 / 1024).toFixed(2)} MB).`;\n        responseBody = { error: errorMessage, attached: false, retryable: false };\n        await saveLog(supabase, {\n          user_id: userId,\n          user_email: userEmail,\n          endpoint: "erp-upload-documento",\n          method: "POST",\n          request_body: requestBody,\n          response_body: responseBody,\n          status_code: 413,\n          success: false,\n          error_message: errorMessage,\n          duration_ms: Date.now() - startTime,\n        });\n        return new Response(JSON.stringify(responseBody), {\n          status: 413,\n          headers: { ...corsHeaders, "Content-Type": "application/json" },\n        });\n      }\n      arquivoBase64 = bytesToBase64(bytes);\n''',
    "legacy preflight file size",
)
replace_once(
    legacy,
    '''    responseBody = {\n      success: true,\n      attached: true,\n      queued: false,\n      data: responseData,\n    };\n''',
    '''    const semanticError = extractErpSemanticError(responseData);\n    if (semanticError) {\n      errorMessage = semanticError.message;\n      if (semanticError.retryable) {\n        const queued = await enqueueForRetry(supabase, requestBody, userId, arquivoNome, errorMessage);\n        if (queued.queued) {\n          triggerQueueProcessor(supabaseUrl, supabaseServiceKey);\n          statusCode = 202;\n          responseBody = {\n            success: true,\n            attached: false,\n            queued: true,\n            queue_id: queued.queueId,\n            details: responseData,\n            message: "O ERP respondeu HTTP 200, mas nao confirmou o anexo. O contrato ficou salvo para retry automatico.",\n          };\n        } else {\n          statusCode = 422;\n          responseBody = { error: errorMessage, attached: false, retryable: true, details: responseData };\n        }\n      } else {\n        statusCode = 422;\n        responseBody = { error: errorMessage, attached: false, retryable: false, details: responseData };\n      }\n\n      await saveLog(supabase, {\n        user_id: userId,\n        user_email: userEmail,\n        endpoint: "erp-upload-documento",\n        method: "POST",\n        request_body: requestBody,\n        response_body: responseBody,\n        status_code: statusCode,\n        success: false,\n        error_message: errorMessage,\n        duration_ms: Date.now() - startTime,\n      });\n\n      return new Response(JSON.stringify(responseBody), {\n        status: statusCode,\n        headers: { ...corsHeaders, "Content-Type": "application/json" },\n      });\n    }\n\n    responseBody = {\n      success: true,\n      attached: true,\n      queued: false,\n      data: responseData,\n    };\n''',
    "legacy semantic success gate",
)

android = Path("android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/CadastroWorkflowRepository.kt")
replace_once(
    android,
    '''): UploadedTempFile {\n        val sanitizedName = fileName\n''',
    '''): UploadedTempFile {\n        val maxErpAttachmentBytes = 5 * 1024 * 1024\n        if (bytes.size > maxErpAttachmentBytes) {\n            throw IllegalArgumentException(\n                "O anexo excede o limite de 5 MB aceito pelo ERP. Reduza o arquivo antes de continuar.",\n            )\n        }\n        val sanitizedName = fileName\n''',
    "android block oversized ERP attachment",
)

migration = Path("supabase/migrations/20260828115500_reclassify_erp_upload_false_success.sql")
migration.write_text('''/*\n  Corrige falsos positivos historicos da fila ERP.\n  O endpoint pode responder HTTP 200 com erro semantico no JSON (ex.: codigo=3).\n*/\n\nUPDATE public.erp_upload_queue q\nSET\n  status = 'failed',\n  attempts = GREATEST(q.attempts, 5),\n  next_attempt_at = now(),\n  last_error = 'ERP rejeitou o anexo: ' || COALESCE(NULLIF(q.erp_response->>'mensagem', ''), NULLIF(q.erp_response->>'message', ''), 'resposta semantica de erro'),\n  updated_at = now()\nWHERE q.status = 'success'\n  AND (\n    (COALESCE(q.erp_response->>'codigo', '') ~ '^[0-9]+$' AND (q.erp_response->>'codigo')::integer >= 2)\n    OR lower(COALESCE(q.erp_response->>'mensagem', q.erp_response->>'message', '')) LIKE ANY (ARRAY[\n      '%erro%', '%falha%', '%excede%', '%limite%', '%maxim%', '%inval%', '%nao%', '%não%', '%obrigat%', '%indisponivel%'\n    ])\n    OR (\n      q.erp_response ? 'erros'\n      AND q.erp_response->'erros' IS DISTINCT FROM 'null'::jsonb\n      AND q.erp_response->'erros' <> '[]'::jsonb\n      AND q.erp_response->'erros' <> '{}'::jsonb\n    )\n  );\n\nUPDATE public.cadastros c\nSET\n  status = 'erro_envio',\n  motivo_bloqueio = 'O ERP rejeitou o anexo. E necessario anexar um novo arquivo valido (maximo 5 MB) antes de concluir.'\nWHERE EXISTS (\n  SELECT 1\n  FROM public.erp_upload_queue q\n  WHERE q.cadastro_id = c.id\n    AND q.status = 'failed'\n    AND q.last_error LIKE 'ERP rejeitou o anexo:%'\n);\n''')
print("created false success reclassification migration")
