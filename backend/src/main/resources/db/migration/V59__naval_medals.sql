CREATE TABLE IF NOT EXISTS naval_medal_profiles (
  player_key VARCHAR(100) NOT NULL PRIMARY KEY,
  public_id CHAR(36) NOT NULL UNIQUE,
  classic_current INT NOT NULL DEFAULT 0,
  classic_best INT NOT NULL DEFAULT 0,
  military_current INT NOT NULL DEFAULT 0,
  military_best INT NOT NULL DEFAULT 0,
  equipped VARCHAR(32) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS naval_medal_rounds (
  game_id VARCHAR(100) NOT NULL,
  player_key VARCHAR(100) NOT NULL,
  PRIMARY KEY(game_id,player_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
