ALTER TABLE naval_daily_tasks
  ADD COLUMN selected_tasks INT NOT NULL DEFAULT 63,
  ADD COLUMN ai_classic_restriction VARCHAR(24) NOT NULL DEFAULT 'fighter',
  ADD COLUMN ai_survival VARCHAR(24) NOT NULL DEFAULT 'patrol',
  ADD COLUMN assignment_version INT NOT NULL DEFAULT 0;

-- Old dates retain their original six assignments and claim bits. The current
-- day's legacy assignment is upgraded once under the existing account lock;
-- any displaced, unclaimed reward is kept in naval_task_reset_rewards.
