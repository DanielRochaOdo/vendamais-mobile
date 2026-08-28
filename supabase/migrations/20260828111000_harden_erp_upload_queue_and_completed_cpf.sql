/*
  Endurece a entrega de anexos ao ERP e impede nova adesao para CPF ja concluido.

  - cron confiavel sem depender de app.settings.service_role_key
  - token interno exclusivo para o worker
  - reset de itens processing travados
  - health check administrativo da fila
  - bloqueio no banco contra nova adesao apos cadastro enviado
*/

CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;

CREATE TABLE IF NOT EXISTS public.erp_upload_worker_control (
  id boolean PRIMARY KEY DEFAULT true CHECK (id),
  worker_token uuid NOT NULL DEFAULT gen_random_uuid(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO public.erp_upload_worker_control (id)
VALUES (true)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.erp_upload_worker_control ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.erp_upload_worker_control FROM anon, authenticated;
GRANT SELECT ON public.erp_upload_worker_control TO service_role;

CREATE TABLE IF NOT EXISTS public.erp_upload_queue_cron_log (
  id bigserial PRIMARY KEY,
  executed_at timestamptz DEFAULT now(),
  status text,
  details text
);

ALTER TABLE public.erp_upload_queue_cron_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Administradores podem visualizar log de cron" ON public.erp_upload_queue_cron_log;
CREATE POLICY "Administradores podem visualizar log de cron"
  ON public.erp_upload_queue_cron_log FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.profiles
      WHERE profiles.id = auth.uid()
      AND upper(profiles.role) IN ('ADMINISTRADOR', 'ADMIN', 'GERENTE', 'GESTOR')
    )
  );

CREATE INDEX IF NOT EXISTS idx_erp_upload_queue_cron_log_executed_at
  ON public.erp_upload_queue_cron_log(executed_at DESC);

CREATE OR REPLACE FUNCTION public.reset_stuck_queue_items(stuck_threshold_minutes integer DEFAULT 10)
RETURNS TABLE(reset_count integer)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_allowed boolean := false;
  v_count integer := 0;
BEGIN
  IF auth.role() = 'service_role' THEN
    v_allowed := true;
  ELSE
    SELECT EXISTS (
      SELECT 1
      FROM public.profiles p
      WHERE p.id = auth.uid()
        AND upper(p.role) IN ('ADMINISTRADOR', 'ADMIN', 'GERENTE', 'GESTOR')
    ) INTO v_allowed;
  END IF;

  IF NOT v_allowed THEN
    RAISE EXCEPTION 'Sem permissao para resetar a fila ERP' USING ERRCODE = '42501';
  END IF;

  UPDATE public.erp_upload_queue
  SET
    status = 'queued',
    next_attempt_at = now(),
    last_error = CASE
      WHEN coalesce(last_error, '') = '' THEN 'Processamento anterior ficou travado e foi reaberto automaticamente.'
      ELSE last_error || ' | Processamento travado reaberto automaticamente.'
    END
  WHERE status = 'processing'
    AND coalesce(last_attempt_at, updated_at, created_at)
      < now() - make_interval(mins => greatest(stuck_threshold_minutes, 1));

  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN QUERY SELECT v_count;
END;
$$;

REVOKE ALL ON FUNCTION public.reset_stuck_queue_items(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.reset_stuck_queue_items(integer) TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.process_erp_upload_queue()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_worker_token text;
  v_request_id bigint;
BEGIN
  SELECT worker_token::text
    INTO v_worker_token
  FROM public.erp_upload_worker_control
  WHERE id = true;

  IF v_worker_token IS NULL OR v_worker_token = '' THEN
    RAISE EXCEPTION 'Token interno do worker ERP nao configurado';
  END IF;

  SELECT net.http_post(
    url := 'https://plonbokgcxwsdqfyjkwl.supabase.co/functions/v1/erp-process-upload-queue',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'X-Queue-Worker-Token', v_worker_token
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 60000
  ) INTO v_request_id;

  INSERT INTO public.erp_upload_queue_cron_log(status, details)
  VALUES ('triggered', 'request_id=' || coalesce(v_request_id::text, 'null'));
EXCEPTION
  WHEN OTHERS THEN
    INSERT INTO public.erp_upload_queue_cron_log(status, details)
    VALUES ('error', SQLERRM);
    RAISE WARNING 'Erro ao disparar fila ERP: %', SQLERRM;
END;
$$;

REVOKE ALL ON FUNCTION public.process_erp_upload_queue() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.process_erp_upload_queue() TO service_role;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'process-erp-upload-queue') THEN
    PERFORM cron.unschedule('process-erp-upload-queue');
  END IF;
END;
$$;

SELECT cron.schedule(
  'process-erp-upload-queue',
  '* * * * *',
  $$SELECT public.process_erp_upload_queue();$$
);

CREATE OR REPLACE FUNCTION public.get_erp_upload_queue_health()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_allowed boolean;
  v_result jsonb;
BEGIN
  SELECT auth.role() = 'service_role' OR EXISTS (
    SELECT 1 FROM public.profiles p
    WHERE p.id = auth.uid()
      AND upper(p.role) IN ('ADMINISTRADOR', 'ADMIN', 'GERENTE', 'GESTOR')
  ) INTO v_allowed;

  IF NOT v_allowed THEN
    RAISE EXCEPTION 'Sem permissao para consultar a saude da fila ERP' USING ERRCODE = '42501';
  END IF;

  SELECT jsonb_build_object(
    'total', count(*),
    'queued', count(*) FILTER (WHERE status = 'queued'),
    'processing', count(*) FILTER (WHERE status = 'processing'),
    'retry_wait', count(*) FILTER (WHERE status = 'retry_wait'),
    'success', count(*) FILTER (WHERE status = 'success'),
    'failed', count(*) FILTER (WHERE status = 'failed'),
    'due_now', count(*) FILTER (
      WHERE status IN ('queued', 'retry_wait')
        AND attempts < 5
        AND next_attempt_at <= now()
    ),
    'stuck_processing', count(*) FILTER (
      WHERE status = 'processing'
        AND coalesce(last_attempt_at, updated_at, created_at) < now() - interval '10 minutes'
    ),
    'oldest_pending_at', min(created_at) FILTER (WHERE status IN ('queued', 'processing', 'retry_wait')),
    'checked_at', now()
  ) INTO v_result
  FROM public.erp_upload_queue;

  RETURN v_result;
END;
$$;

REVOKE ALL ON FUNCTION public.get_erp_upload_queue_health() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_erp_upload_queue_health() TO authenticated, service_role;

CREATE INDEX IF NOT EXISTS idx_cadastros_sent_cpf_normalized
  ON public.cadastros ((regexp_replace(cpf, '\D', '', 'g')))
  WHERE tipo_cadastro = 'cadastro' AND status = 'enviado';

CREATE OR REPLACE FUNCTION public.block_new_cadastro_when_sent_exists()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cpf text;
BEGIN
  IF NEW.tipo_cadastro IS DISTINCT FROM 'cadastro' THEN
    RETURN NEW;
  END IF;

  v_cpf := regexp_replace(coalesce(NEW.cpf, ''), '\D', '', 'g');
  IF length(v_cpf) <> 11 THEN
    RETURN NEW;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.cadastros c
    WHERE c.tipo_cadastro = 'cadastro'
      AND c.status = 'enviado'
      AND regexp_replace(coalesce(c.cpf, ''), '\D', '', 'g') = v_cpf
      AND NOT EXISTS (
        SELECT 1 FROM public.cadastros_excluidos ce WHERE ce.cadastro_id = c.id
      )
  ) THEN
    RAISE EXCEPTION 'Este CPF ja possui uma adesao concluida. Abra o cadastro enviado existente em vez de iniciar uma nova adesao.'
      USING ERRCODE = '23505';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_block_new_cadastro_when_sent_exists ON public.cadastros;
CREATE TRIGGER trg_block_new_cadastro_when_sent_exists
  BEFORE INSERT ON public.cadastros
  FOR EACH ROW
  EXECUTE FUNCTION public.block_new_cadastro_when_sent_exists();
