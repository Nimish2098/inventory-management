-- Phase 2: record which action caused each status change, and support idempotent retries.
-- Rename V3 if your next free migration number is different.
ALTER TABLE delivery_event ADD COLUMN action          VARCHAR(20);
ALTER TABLE delivery_event ADD COLUMN idempotency_key VARCHAR(100);

-- The same client key can only be used once per delivery (NULL keys are unlimited).
CREATE UNIQUE INDEX uq_delivery_event_idem
    ON delivery_event (delivery_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
