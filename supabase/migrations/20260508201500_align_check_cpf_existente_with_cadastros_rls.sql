/*
  # Alinha check_cpf_existente com visibilidade real da lista de cadastros (RLS)

  Problema:
    - A funcao podia retornar `can_continue=true` para pendentes nao visiveis na listagem,
      gerando popup indevido de "cadastro pendente" ao iniciar nova adesao.

  Ajuste:
    - Aplica exatamente o mesmo criterio de visibilidade de `cadastros` (RLS atual):
      * ADMINISTRADOR/GERENTE/CADASTRO/ADESIONISTA: visao total.
      * SUPERVISOR: apenas team_id do proprio time.
      * VENDEDOR: apenas `created_by = auth.uid()` ou `vendedor_id = auth.uid()`.
    - Remove criterio por `vendedor_codigo` do can_continue para evitar falso positivo.
*/

CREATE OR REPLACE FUNCTION check_cpf_existente(
  p_cpf text,
  p_user_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_pending record;
  v_existing record;
  v_user_role text;
  v_user_team_id uuid;
  v_cpf_norm text := regexp_replace(coalesce(p_cpf, ''), '\D', '', 'g');
  v_can_continue boolean := false;
BEGIN
  SELECT
    p.role,
    p.team_id
  INTO
    v_user_role,
    v_user_team_id
  FROM profiles p
  WHERE p.id = p_user_id;

  IF v_user_role IS NULL THEN
    RETURN jsonb_build_object(
      'exists', false,
      'can_continue', false,
      'error', 'Usuario nao encontrado'
    );
  END IF;

  IF length(v_cpf_norm) <> 11 THEN
    RETURN jsonb_build_object(
      'exists', false,
      'can_continue', false
    );
  END IF;

  SELECT
    c.id,
    c.status,
    c.created_at,
    c.updated_at,
    c.empresa_nome,
    c.created_by,
    c.vendedor_id,
    c.team_id
  INTO v_pending
  FROM cadastros c
  WHERE c.tipo_cadastro = 'cadastro'
    AND c.status = 'incompleto'
    AND regexp_replace(coalesce(c.cpf, ''), '\D', '', 'g') = v_cpf_norm
    AND NOT EXISTS (
      SELECT 1
      FROM cadastros_excluidos ce
      WHERE ce.cadastro_id = c.id
    )
  ORDER BY
    c.updated_at DESC NULLS LAST,
    c.created_at DESC NULLS LAST,
    c.id DESC
  LIMIT 1;

  IF FOUND THEN
    IF v_user_role IN ('ADMINISTRADOR', 'GERENTE', 'CADASTRO', 'ADESIONISTA') THEN
      v_can_continue := true;
    ELSIF v_user_role = 'SUPERVISOR' THEN
      v_can_continue := (v_user_team_id IS NOT NULL AND v_pending.team_id = v_user_team_id);
    ELSIF v_user_role = 'VENDEDOR' THEN
      v_can_continue := (
        v_pending.created_by = p_user_id
        OR v_pending.vendedor_id = p_user_id
      );
    ELSE
      v_can_continue := false;
    END IF;

    RETURN jsonb_build_object(
      'exists', true,
      'can_continue', v_can_continue,
      'status', v_pending.status,
      'cadastro_id', CASE WHEN v_can_continue THEN v_pending.id ELSE NULL END,
      'created_at', v_pending.created_at,
      'empresa_nome', v_pending.empresa_nome
    );
  END IF;

  SELECT
    c.id,
    c.status,
    c.created_at,
    c.updated_at,
    c.empresa_nome
  INTO v_existing
  FROM cadastros c
  WHERE c.tipo_cadastro = 'cadastro'
    AND regexp_replace(coalesce(c.cpf, ''), '\D', '', 'g') = v_cpf_norm
    AND NOT EXISTS (
      SELECT 1
      FROM cadastros_excluidos ce
      WHERE ce.cadastro_id = c.id
    )
  ORDER BY
    CASE
      WHEN c.status = 'enviado' THEN 0
      WHEN c.status = 'erro_envio' THEN 1
      ELSE 2
    END,
    c.updated_at DESC NULLS LAST,
    c.created_at DESC NULLS LAST
  LIMIT 1;

  IF NOT FOUND THEN
    RETURN jsonb_build_object(
      'exists', false,
      'can_continue', false
    );
  END IF;

  RETURN jsonb_build_object(
    'exists', true,
    'can_continue', false,
    'status', v_existing.status,
    'cadastro_id', NULL,
    'created_at', v_existing.created_at,
    'empresa_nome', v_existing.empresa_nome
  );
END;
$$;

COMMENT ON FUNCTION check_cpf_existente IS
'Verifica CPF ativo (tipo_cadastro=cadastro) com can_continue alinhado a visibilidade RLS da listagem de cadastros.';
