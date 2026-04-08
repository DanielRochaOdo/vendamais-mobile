/*
  # Corrige duplicidade no fluxo publico de cadastro

  1) Normaliza CPF dos cadastros publicos para apenas digitos
  2) Remove duplicados de legado por (origem_link_id, cpf), mantendo:
     - prioridade 1: status = 'enviado'
     - prioridade 2: registro mais recente (updated_at/created_at)
  3) Garante unicidade parcial para impedir novas duplicidades
*/

-- Normaliza CPF dos registros do fluxo publico para reduzir variacoes de formato
UPDATE cadastros
SET cpf = regexp_replace(cpf, '\D', '', 'g')
WHERE fluxo_publico = true
  AND origem_link_id IS NOT NULL
  AND cpf IS NOT NULL
  AND cpf ~ '\D';

-- Deduplicacao de legado no fluxo publico
WITH ranked AS (
  SELECT
    id,
    row_number() OVER (
      PARTITION BY origem_link_id, cpf
      ORDER BY
        CASE WHEN status = 'enviado' THEN 0 ELSE 1 END,
        updated_at DESC NULLS LAST,
        created_at DESC NULLS LAST,
        id DESC
    ) AS rn
  FROM cadastros
  WHERE origem_link_id IS NOT NULL
    AND fluxo_publico = true
),
to_remove AS (
  SELECT id
  FROM ranked
  WHERE rn > 1
)
DELETE FROM cadastros c
USING to_remove r
WHERE c.id = r.id;

-- Trava definitiva para o fluxo publico
CREATE UNIQUE INDEX IF NOT EXISTS cadastros_public_link_cpf_unique_idx
  ON cadastros (origem_link_id, cpf)
  WHERE origem_link_id IS NOT NULL
    AND fluxo_publico = true;
