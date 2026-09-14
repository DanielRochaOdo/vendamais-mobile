/*
  # Alinhar recadastro de CPF com a regra autoritativa do ERP

  Objetivo:
  - Historico local `enviado` nao deve bloquear uma nova adesao por si so.
  - Apenas uma adesao local ainda pendente deve impedir a abertura de outro fluxo.
  - A elegibilidade atual do CPF para nova adesao e decidida por `erp-check-associado`,
    que avalia situacao/plano no ERP.
*/

-- O bloqueio criado em 20260828111000 tratava qualquer historico enviado como
-- impedimento permanente. Isso conflita com o fluxo do Adesart e com a regra
-- operacional: o ERP e a fonte de verdade para saber se o CPF pode recadastrar.
DROP TRIGGER IF EXISTS trg_block_new_cadastro_when_sent_exists ON public.cadastros;
DROP FUNCTION IF EXISTS public.block_new_cadastro_when_sent_exists();

-- Mantemos a RPC local estritamente como protecao contra dois processos abertos
-- ao mesmo tempo. Historico concluido/enviado nao participa desta consulta.
CREATE OR REPLACE FUNCTION public.check_cpf_existente(
  p_cpf text,
  p_user_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cadastro record;
  v_user_role text;
  v_user_external_id text;
  v_can_continue boolean := false;
  v_cpf text;
BEGIN
  v_cpf := regexp_replace(COALESCE(p_cpf, ''), '\D', '', 'g');

  IF length(v_cpf) <> 11 THEN
    RETURN jsonb_build_object(
      'exists', false,
      'can_continue', false
    );
  END IF;

  SELECT role, external_id
    INTO v_user_role, v_user_external_id
  FROM public.profiles
  WHERE id = p_user_id;

  IF v_user_role IS NULL THEN
    RETURN jsonb_build_object(
      'exists', false,
      'can_continue', false,
      'error', 'Usuario nao encontrado'
    );
  END IF;

  SELECT
    c.id,
    c.status,
    c.created_at,
    c.empresa_nome,
    c.vendedor_codigo
  INTO v_cadastro
  FROM public.cadastros c
  WHERE regexp_replace(COALESCE(c.cpf, ''), '\D', '', 'g') = v_cpf
    AND c.tipo_cadastro = 'cadastro'
    AND lower(trim(COALESCE(c.status, ''))) IN ('incompleto', 'erro_envio', 'adesoes_pendentes')
    AND NOT EXISTS (
      SELECT 1
      FROM public.cadastros_excluidos ce
      WHERE ce.cadastro_id = c.id
    )
  ORDER BY c.updated_at DESC NULLS LAST, c.created_at DESC, c.id DESC
  LIMIT 1;

  IF NOT FOUND THEN
    RETURN jsonb_build_object(
      'exists', false,
      'can_continue', false
    );
  END IF;

  IF upper(COALESCE(v_user_role, '')) IN (
    'ADMINISTRADOR', 'ADMIN', 'GESTOR', 'GERENTE', 'SUPERVISOR', 'CADASTRO'
  ) THEN
    v_can_continue := true;
  ELSIF upper(COALESCE(v_user_role, '')) IN ('VENDEDOR', 'ADESIONISTA') THEN
    v_can_continue := (v_cadastro.vendedor_codigo = v_user_external_id);
  ELSE
    v_can_continue := false;
  END IF;

  RETURN jsonb_build_object(
    'exists', true,
    'can_continue', v_can_continue,
    'status', v_cadastro.status,
    'cadastro_id', CASE WHEN v_can_continue THEN v_cadastro.id ELSE NULL END,
    'created_at', v_cadastro.created_at,
    'empresa_nome', v_cadastro.empresa_nome
  );
END;
$$;

REVOKE ALL ON FUNCTION public.check_cpf_existente(text, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.check_cpf_existente(text, uuid) TO authenticated;

COMMENT ON FUNCTION public.check_cpf_existente(text, uuid) IS
  'Verifica somente adesoes locais pendentes do titular. Historico enviado nao bloqueia; elegibilidade de recadastro e decidida pelo ERP.';
