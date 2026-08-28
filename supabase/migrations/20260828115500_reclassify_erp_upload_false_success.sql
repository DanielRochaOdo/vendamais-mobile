/*
  Corrige falsos positivos historicos da fila ERP.
  O endpoint pode responder HTTP 200 com erro semantico no JSON (ex.: codigo=3).
*/

UPDATE public.erp_upload_queue q
SET
  status = 'failed',
  attempts = GREATEST(q.attempts, 5),
  next_attempt_at = now(),
  last_error = 'ERP rejeitou o anexo: ' || COALESCE(NULLIF(q.erp_response->>'mensagem', ''), NULLIF(q.erp_response->>'message', ''), 'resposta semantica de erro'),
  updated_at = now()
WHERE q.status = 'success'
  AND (
    (COALESCE(q.erp_response->>'codigo', '') ~ '^[0-9]+$' AND (q.erp_response->>'codigo')::integer >= 2)
    OR lower(COALESCE(q.erp_response->>'mensagem', q.erp_response->>'message', '')) LIKE ANY (ARRAY[
      '%erro%', '%falha%', '%excede%', '%limite%', '%maxim%', '%inval%', '%nao%', '%não%', '%obrigat%', '%indisponivel%'
    ])
    OR (
      q.erp_response ? 'erros'
      AND q.erp_response->'erros' IS DISTINCT FROM 'null'::jsonb
      AND q.erp_response->'erros' <> '[]'::jsonb
      AND q.erp_response->'erros' <> '{}'::jsonb
    )
  );

UPDATE public.cadastros c
SET
  status = 'erro_envio',
  motivo_bloqueio = 'O ERP rejeitou o anexo. E necessario anexar um novo arquivo valido (maximo 5 MB) antes de concluir.'
WHERE EXISTS (
  SELECT 1
  FROM public.erp_upload_queue q
  WHERE q.cadastro_id = c.id
    AND q.status = 'failed'
    AND q.last_error LIKE 'ERP rejeitou o anexo:%'
);
