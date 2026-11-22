-- Phase 3: the stream gives at-least-once delivery, so inserts must be idempotent.
-- A vehicle cannot have two positions with the same device timestamp; duplicates are skipped
-- with INSERT ... ON CONFLICT DO NOTHING. This index replaces the non-unique one from V2.
-- (Rename V4 if your next free migration number is different.)
DROP INDEX IF EXISTS idx_position_vehicle_time;
CREATE UNIQUE INDEX uq_position_vehicle_time ON vehicle_position (vehicle_id, recorded_at DESC);
