ALTER TABLE game_room_members DROP COLUMN ready_stage, DROP COLUMN event_ack_stage;
DELETE t FROM guess_stock_trades t
JOIN guess_stock_orders o ON o.id = t.order_id
WHERE o.stage = 'CLOSING';
DELETE FROM guess_stock_orders WHERE stage = 'CLOSING';
DELETE FROM guess_stock_ai_tasks WHERE stage = 'CLOSING';
ALTER TABLE guess_stock_ai_tasks DROP COLUMN stage;
ALTER TABLE guess_stock_orders
  DROP INDEX uk_guess_stock_order_participant_stage,
  DROP COLUMN stage,
  ADD UNIQUE KEY uk_guess_stock_order_participant_round (session_id, participant_id, round_no);
UPDATE guess_stock_rounds SET open_price = COALESCE(open_price, previous_close_price, 0.01);
ALTER TABLE guess_stock_rounds
  DROP COLUMN closing_sell_quantity,
  DROP COLUMN opening_buy_quantity,
  DROP COLUMN opening_order_flow_change_percent,
  DROP COLUMN previous_close_price,
  DROP COLUMN event_stage;
ALTER TABLE guess_stock_rounds MODIFY COLUMN open_price DECIMAL(20,4) NOT NULL;
