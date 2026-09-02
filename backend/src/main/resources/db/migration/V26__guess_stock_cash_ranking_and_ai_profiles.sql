ALTER TABLE guess_stock_orders
  ADD COLUMN all_in TINYINT NOT NULL DEFAULT 0 AFTER reserved_amount;

ALTER TABLE guess_stock_participants
  ADD COLUMN initial_cash DECIMAL(20,4) NULL AFTER cash_balance;

UPDATE guess_stock_participants p
JOIN game_sessions s ON s.id = p.session_id
JOIN game_versions v ON v.id = s.game_version_id
SET p.initial_cash = CAST(JSON_UNQUOTE(JSON_EXTRACT(v.config_json, '$.initialCash')) AS DECIMAL(20,4))
WHERE p.initial_cash IS NULL;

UPDATE game_leaderboard_entries
SET score = final_asset
WHERE final_asset IS NOT NULL;
