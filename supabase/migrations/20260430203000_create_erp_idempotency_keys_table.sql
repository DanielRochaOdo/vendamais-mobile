/*
  # Create erp_idempotency_keys table

  1. Purpose
    - Prevent duplicate ERP submissions for the same idempotency key.
    - Support request replay with deterministic responses for already processed keys.

  2. Security
    - RLS enabled with no public policies.
    - Service role can always bypass RLS (used by Edge Functions).
*/

CREATE TABLE IF NOT EXISTS erp_idempotency_keys (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  endpoint text NOT NULL,
  idempotency_key text NOT NULL,
  user_id uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  status text NOT NULL DEFAULT 'processing' CHECK (status IN ('processing', 'completed', 'failed')),
  lock_token text,
  response_body jsonb,
  status_code integer,
  error_message text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE erp_idempotency_keys ENABLE ROW LEVEL SECURITY;

CREATE UNIQUE INDEX IF NOT EXISTS idx_erp_idempotency_keys_endpoint_key
  ON erp_idempotency_keys(endpoint, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_erp_idempotency_keys_created_at
  ON erp_idempotency_keys(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_erp_idempotency_keys_status
  ON erp_idempotency_keys(status);
