ALTER TABLE dish_option_groups
  ADD COLUMN selection_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
  ADD COLUMN min_select INT NOT NULL DEFAULT 0,
  ADD COLUMN max_select INT NULL;

UPDATE dish_option_groups
SET min_select = CASE WHEN required = 1 THEN 1 ELSE 0 END,
    max_select = 1
WHERE selection_type = 'SINGLE';

ALTER TABLE orders
  DROP CHECK chk_orders_status;

ALTER TABLE orders
  ADD CONSTRAINT chk_orders_status CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'COMPLETED', 'CANCELLED', 'REJECTED'));
