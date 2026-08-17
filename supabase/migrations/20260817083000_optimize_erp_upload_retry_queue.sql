/*
  Otimiza a busca idempotente de retries de contrato.

  A Edge Function erp-enqueue-upload procura uma tentativa ativa pelo mesmo
  funcionário/dependente/arquivo antes de inserir uma nova linha. Este índice
  evita varreduras da fila conforme o volume de uploads cresce.
*/

CREATE INDEX IF NOT EXISTS idx_erp_upload_queue_retry_identity
  ON erp_upload_queue (
    id_funcionario,
    id_dependente,
    bucket,
    arquivo_path,
    created_at DESC
  )
  WHERE status IN ('queued', 'retry_wait', 'processing', 'failed');
