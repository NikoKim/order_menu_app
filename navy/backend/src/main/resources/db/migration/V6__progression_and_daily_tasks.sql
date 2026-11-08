CREATE TABLE naval_progression_profiles (
  player_key VARCHAR(100) NOT NULL PRIMARY KEY,
  score INT NOT NULL DEFAULT 0,
  coins BIGINT NOT NULL DEFAULT 0,
  promotion_wins INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE naval_daily_tasks (
  player_key VARCHAR(100) NOT NULL,
  task_day DATE NOT NULL,
  classic_restriction VARCHAR(24) NOT NULL,
  military_restriction VARCHAR(24) NOT NULL,
  completed_tasks INT NOT NULL DEFAULT 0,
  PRIMARY KEY(player_key, task_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Independent of room/history retention: deleting old battles cannot credit them twice.
CREATE TABLE naval_progression_rounds (
  game_id VARCHAR(100) NOT NULL,
  player_key VARCHAR(100) NOT NULL,
  reward_json JSON NOT NULL,
  credited_at BIGINT NOT NULL,
  PRIMARY KEY(game_id, player_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
