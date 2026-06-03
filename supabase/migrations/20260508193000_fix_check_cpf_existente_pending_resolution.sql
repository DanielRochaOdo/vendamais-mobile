/*
  # Fix check_cpf_existente para reconciliacao real de pendentes por CPF

  Problema:
    - Em conflitos do indice `cadastros_cadastro_incompleto_cpf_unique_idx`, o app usa
      `check_cpf_existente` como fallback para localizar o pendente.
    - A funcao podia devolver `can_continue = false` mesmo em perfis que tem permissao
      de continuidade (ex.: ADESIONISTA) e priorizava registros nao pendentes em alguns
      cenarios, dificultando a reconciliacao.

  Ajuste:
    1) Prioriza cadastro pendente (`status = incompleto`) para o CPF.
    2) Alinha `can_continue` com o modelo atual de acesso:
       - ADMINISTRADOR/GERENTE/GESTOR/CADASTRO/ADESIONISTA: pode continuar.
       - SUPERVISOR: pode continuar quando o pendente for do mesmo time.
       - VENDEDOR: pode continuar quando for criador, vendedor_id ou vendedor_codigo.
    3) Mantem filtro de tipo_cadastro = cadastro e ignora excluidos logicamente.
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
  v_user_external_id text;
  v_user_team_id uuid;
  v_cpf_norm text := regexp_replace(coalesce(p_cpf, ''), '\D', '', 'g');
  v_can_continue boolean := false;
BEGIN
  SELECT
    p.role,
    nullif(btrim(p.external_id), ''),
    p.team_id
  INTO
    v_user_role,
    v_user_external_id,
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
    c.vendedor_codigo,
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
    IF v_user_role IN ('ADMINISTRADOR', 'GERENTE', 'GESTOR', 'CADASTRO', 'ADESIONISTA') THEN
      v_can_continue := true;
    ELSIF v_user_role = 'SUPERVISOR' THEN
      v_can_continue := (v_user_team_id IS NOT NULL AND v_pending.team_id = v_user_team_id);
    ELSIF v_user_role = 'VENDEDOR' THEN
      v_can_continue := (
        v_pending.created_by = p_user_id
        OR v_pending.vendedor_id = p_user_id
        OR (
          v_user_external_id IS NOT NULL
          AND nullif(btrim(v_pending.vendedor_codigo), '') = v_user_external_id
        )
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
'Verifica CPF ativo (tipo_cadastro=cadastro), priorizando pendente incompleto para reconciliacao e alinhando can_continue com regras atuais de acesso.';
