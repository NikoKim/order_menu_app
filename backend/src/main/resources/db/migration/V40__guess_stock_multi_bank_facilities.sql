ALTER TABLE guess_stock_credit_facilities
  DROP INDEX uk_guess_stock_credit_facility_participant,
  ADD UNIQUE KEY uk_guess_stock_credit_facility_product (session_id, participant_id, product_code);
