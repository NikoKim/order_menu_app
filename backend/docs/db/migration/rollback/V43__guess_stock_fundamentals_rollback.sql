DROP TABLE IF EXISTS guess_stock_financial_reports;
ALTER TABLE guess_stock_rounds
  DROP COLUMN financial_report_published, DROP COLUMN financial_risk, DROP COLUMN fair_value_index, DROP COLUMN fundamental_score,
  DROP COLUMN cash_flow_index, DROP COLUMN margin_index, DROP COLUMN inventory_index,
  DROP COLUMN order_index, DROP COLUMN demand_index, DROP COLUMN report_surprise_percent,
  DROP COLUMN fundamental_change_percent;
