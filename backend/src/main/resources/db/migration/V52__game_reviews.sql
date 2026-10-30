CREATE TABLE game_reviews (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  game_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  rating TINYINT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_game_review_user (game_id, user_id),
  KEY idx_game_review_recent (game_id, updated_at, id),
  CONSTRAINT chk_game_review_rating CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT fk_game_review_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
  CONSTRAINT fk_game_review_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE game_sessions ADD KEY idx_game_session_review_player (player_user_id, game_id);
ALTER TABLE guess_stock_participants ADD KEY idx_game_participant_review_user (player_user_id, session_id);
