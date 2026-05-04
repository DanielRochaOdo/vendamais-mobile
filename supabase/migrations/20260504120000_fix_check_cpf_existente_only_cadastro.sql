/*
  # Fix check_cpf_existente para considerar apenas adesões de cadastro

  1. Problema
    - A função `check_cpf_existente` estava considerando também registros
      `tipo_cadastro = 'inclusao_dependente'`.
    - Isso bloqueava novas adesões de cadastro por CPF de forma indevida.

  2. Ajuste
    - Restringe a busca para `tipo_cadastro = 'cadastro'`.
    - Mantém a lógica de priorização de status e permissões por perfil.
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
  v_cadastro record;
  v_user_role text;
  v_user_external_id text;
  v_cpf_norm text := regexp_replace(coalesce(p_cpf, ''), '\D', '', 'g');
  v_status_norm text := '';
  v_status_permite_continuar boolean := false;
  v_can_continue boolean := false;
BEGIN
  SELECT role, external_id
  INTO v_user_role, v_user_external_id
  FROM profiles
  WHERE id = p_user_id;

  IF v_user_role IS NULL THEN
    RETURN jsonb_build_object(
      'exists', false,
      'error', 'Usuário não encontrado'
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
    c.vendedor_codigo
  INTO v_cadastro
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
      WHEN c.status = 'incompleto' THEN 1
      WHEN c.status = 'erro_envio' THEN 2
      ELSE 3
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

  v_status_norm := lower(coalesce(v_cadastro.status, ''));
  v_status_permite_continuar := v_status_norm = 'incompleto';

  IF NOT v_status_permite_continuar THEN
    v_can_continue := false;
  ELSIF v_user_role IN ('ADMINISTRADOR', 'GESTOR', 'SUPERVISOR', 'CADASTRO') THEN
    v_can_continue := true;
  ELSIF v_user_role = 'VENDEDOR' THEN
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

COMMENT ON FUNCTION check_cpf_existente IS
'Verifica CPF ativo somente para tipo_cadastro=cadastro, ignorando exclusões e priorizando status enviado.';
