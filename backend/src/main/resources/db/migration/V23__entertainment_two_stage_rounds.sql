ALTER TABLE guess_stock_rounds
  MODIFY COLUMN open_price DECIMAL(20,4) NULL,
  ADD COLUMN event_stage VARCHAR(20) NULL AFTER event_config_index,
  ADD COLUMN previous_close_price DECIMAL(20,4) NULL AFTER event_effective,
  ADD COLUMN opening_order_flow_change_percent DECIMAL(10,4) NULL AFTER order_flow_change_percent,
  ADD COLUMN opening_buy_quantity BIGINT NULL AFTER opening_order_flow_change_percent,
  ADD COLUMN closing_sell_quantity BIGINT NULL AFTER opening_buy_quantity;

ALTER TABLE guess_stock_orders
  DROP INDEX uk_guess_stock_order_participant_round,
  ADD COLUMN stage VARCHAR(20) NOT NULL DEFAULT 'OPENING' AFTER round_no,
  ADD UNIQUE KEY uk_guess_stock_order_participant_stage (session_id, participant_id, round_no, stage);

ALTER TABLE guess_stock_ai_tasks
  ADD COLUMN stage VARCHAR(20) NOT NULL DEFAULT 'OPENING' AFTER round_no;

ALTER TABLE game_room_members
  ADD COLUMN event_ack_stage VARCHAR(30) NULL AFTER event_ack_round,
  ADD COLUMN ready_stage VARCHAR(30) NULL AFTER ready_round;

UPDATE game_sessions s
SET s.status = 'ABANDONED', s.phase = 'COMPLETED', s.phase_ends_at = NULL,
    s.activity_lease_until = NULL, s.completed_at = CURRENT_TIMESTAMP
WHERE s.status IN ('ACTIVE', 'PAUSED')
  AND EXISTS (
    SELECT 1 FROM guess_stock_rounds r
    WHERE r.session_id = s.id AND r.previous_close_price IS NULL
  );
