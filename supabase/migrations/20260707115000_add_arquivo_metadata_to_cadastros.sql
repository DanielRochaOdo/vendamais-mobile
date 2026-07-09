/*
  # Add attachment metadata to cadastros

  Adds columns used by the Android draft flow to persist attachment metadata
  alongside arquivo_path.
*/

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'cadastros' AND column_name = 'arquivo_nome'
  ) THEN
    ALTER TABLE cadastros ADD COLUMN arquivo_nome text;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'cadastros' AND column_name = 'arquivo_mime_type'
  ) THEN
    ALTER TABLE cadastros ADD COLUMN arquivo_mime_type text;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'cadastros' AND column_name = 'arquivo_tamanho'
  ) THEN
    ALTER TABLE cadastros ADD COLUMN arquivo_tamanho bigint;
  END IF;
END $$;

COMMENT ON COLUMN cadastros.arquivo_mime_type IS 'Mime type do anexo associado ao cadastro.';
COMMENT ON COLUMN cadastros.arquivo_tamanho IS 'Tamanho do anexo em bytes.';
COMMENT ON COLUMN cadastros.arquivo_nome IS 'Nome original do anexo associado ao cadastro.';
