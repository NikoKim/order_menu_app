CREATE TABLE naval_round_results (
  game_id VARCHAR(80) NOT NULL PRIMARY KEY,
  room_id VARCHAR(36) NOT NULL,
  round_no INT NOT NULL,
  player0_key VARCHAR(80) NOT NULL,
  player1_key VARCHAR(80) NOT NULL,
  finished_at BIGINT NOT NULL,
  result_json JSON NOT NULL,
  UNIQUE KEY uk_naval_room_round (room_id, round_no),
  KEY idx_naval_result_player0 (player0_key, finished_at),
  KEY idx_naval_result_player1 (player1_key, finished_at)
);
