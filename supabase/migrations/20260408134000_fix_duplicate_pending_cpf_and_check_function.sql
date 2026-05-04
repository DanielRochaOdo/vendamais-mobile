/*
  # Corrige duplicidade de pendentes por CPF e prioridade de status na checagem

  1) Consolida pendentes duplicados (tipo_cadastro='cadastro', status='incompleto')
     mantendo apenas o mais recente por CPF (ignora registros já excluídos logicamente).
  2) Cria índice único parcial para impedir novos duplicados de pendente por CPF.
  3) Atualiza `check_cpf_existente` para priorizar cadastro enviado sobre incompleto,
     evitando que CPF concluído volte como "continuar pendente".
*/

-- Normaliza CPF dos pendentes para apenas dígitos
UPDATE cadastros
SET cpf = regexp_replace(cpf, '\D', '', 'g')
WHERE tipo_cadastro = 'cadastro'
  AND status = 'incompleto'
  AND cpf IS NOT NULL
  AND cpf ~ '\D';

-- Remove duplicados de pendentes por CPF (mantém o mais recente)
WITH ranked AS (
  SELECT
    c.id,
    row_number() OVER (
      PARTITION BY regexp_replace(coalesce(c.cpf, ''), '\D', '', 'g')
      ORDER BY
        c.updated_at DESC NULLS LAST,
        c.created_at DESC NULLS LAST,
        c.id DESC
    ) AS rn
  FROM cadastros c
  WHERE c.tipo_cadastro = 'cadastro'
    AND c.status = 'incompleto'
    AND coalesce(c.cpf, '') <> ''
    AND NOT EXISTS (
      SELECT 1
      FROM cadastros_excluidos ce
      WHERE ce.cadastro_id = c.id
    )
),
to_delete AS (
  SELECT id
  FROM ranked
  WHERE rn > 1
)
DELETE FROM cadastros c
USING to_delete d
WHERE c.id = d.id;

-- Impede novos pendentes duplicados por CPF
CREATE UNIQUE INDEX IF NOT EXISTS cadastros_cadastro_incompleto_cpf_unique_idx
  ON cadastros ((regexp_replace(coalesce(cpf, ''), '\D', '', 'g')))
  WHERE tipo_cadastro = 'cadastro'
    AND status = 'incompleto'
    AND coalesce(cpf, '') <> '';

-- Recria função para priorizar status enviado e bloquear continuidade indevida
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
  WHERE regexp_replace(coalesce(c.cpf, ''), '\D', '', 'g') = v_cpf_norm
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
  v_status_permite_continuar := v_status_norm IN ('incompleto', 'adesoes_pendentes', 'pendente');

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
'Verifica CPF ativo ignorando exclusões. Prioriza status enviado para evitar continuidade indevida de pendentes duplicados.';
