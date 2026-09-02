ALTER TABLE guess_stock_challenge_entries
  ADD COLUMN research_attempt_count INT NOT NULL DEFAULT 0 AFTER accurate_research_count;
