ALTER TABLE guess_stock_challenges DROP INDEX uk_guess_stock_challenge_day_version;
ALTER TABLE guess_stock_challenges
  ADD UNIQUE KEY uk_guess_stock_challenge_day (challenge_date);
