/*
  # Corrige conflito de CPF pendente com registros excluídos logicamente

  Problema:
    - O índice parcial `cadastros_cadastro_incompleto_cpf_unique_idx` considera
      qualquer registro `status='incompleto'` em `cadastros`.
    - Registros já excluídos logicamente (presentes em `cadastros_excluidos`) podem
      permanecer em `cadastros` com status incompleto em ambientes legados, bloqueando
      novas adesões para o mesmo CPF.

  Ajuste:
    1) Normaliza CPF de pendentes de cadastro para somente dígitos.
    2) Remove da faixa "pendente" os cadastros já excluídos logicamente.
    3) Deduplica pendentes remanescentes por CPF, mantendo o mais recente.
*/

-- 1) Normalizar CPF dos pendentes de cadastro
UPDATE cadastros
SET cpf = regexp_replace(cpf, '\D', '', 'g')
WHERE tipo_cadastro = 'cadastro'
  AND status = 'incompleto'
  AND cpf IS NOT NULL
  AND cpf ~ '\D';

-- 2) Registros excluídos logicamente não podem permanecer como pendentes
UPDATE cadastros c
SET status = 'enviado',
    updated_at = now()
WHERE c.tipo_cadastro = 'cadastro'
  AND c.status = 'incompleto'
  AND EXISTS (
    SELECT 1
    FROM cadastros_excluidos ce
    WHERE ce.cadastro_id = c.id
  );

-- 3) Deduplicar pendentes remanescentes por CPF (mantém o mais recente)
WITH ranked AS (
  SELECT
    c.id,
    row_number() OVER (
      PARTITION BY regexp_replace(coalesce(c.cpf, ''), '\D', '', 'g')
      ORDER BY c.updated_at DESC NULLS LAST, c.created_at DESC NULLS LAST, c.id DESC
    ) AS rn
  FROM cadastros c
  WHERE c.tipo_cadastro = 'cadastro'
    AND c.status = 'incompleto'
    AND coalesce(c.cpf, '') <> ''
)
UPDATE cadastros c
SET status = 'enviado',
    updated_at = now()
FROM ranked r
WHERE c.id = r.id
  AND r.rn > 1;
