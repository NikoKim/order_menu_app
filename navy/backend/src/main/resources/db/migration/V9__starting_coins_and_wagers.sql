ALTER TABLE naval_progression_profiles ALTER COLUMN coins SET DEFAULT 50;
ALTER TABLE naval_progression_profiles ADD COLUMN coin_fraction INT NOT NULL DEFAULT 0;
UPDATE naval_progression_profiles SET coins=coins+50;

ALTER TABLE naval_rooms
 ADD COLUMN wager INT NOT NULL DEFAULT 0;

CREATE TABLE naval_wager_stakes (
 game_id VARCHAR(100) NOT NULL,
 player_key VARCHAR(100) NOT NULL,
 amount INT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'HELD',
 PRIMARY KEY(game_id,player_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE naval_wager_settlements (
 game_id VARCHAR(100) NOT NULL PRIMARY KEY,
 outcome_json JSON NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
