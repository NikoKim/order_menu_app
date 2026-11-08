ALTER TABLE naval_progression_profiles ALTER COLUMN coins SET DEFAULT 100;
ALTER TABLE naval_daily_tasks ADD COLUMN claimed_tasks INT NOT NULL DEFAULT 0;

-- Earlier versions already paid these completed tasks automatically.
UPDATE naval_daily_tasks SET claimed_tasks=completed_tasks;

INSERT INTO naval_progression_profiles(player_key,coins)
SELECT CONCAT('U:',id),100 FROM users WHERE nickname='小鲨鱼'
ON DUPLICATE KEY UPDATE coins=100,coin_fraction=0;
