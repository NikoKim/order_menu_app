-- This rollback is valid only after all but one facility per session/participant
-- have been repaid and removed. It intentionally fails when duplicate rows remain.
ALTER TABLE guess_stock_credit_facilities
  DROP INDEX uk_guess_stock_credit_facility_product,
  ADD UNIQUE KEY uk_guess_stock_credit_facility_participant (session_id, participant_id);
