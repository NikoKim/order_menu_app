ALTER TABLE naval_rooms
 ADD COLUMN match_rule_set VARCHAR(16) GENERATED ALWAYS AS (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(state_json,'$.ruleSet')),'CLASSIC')) STORED,
 ADD INDEX idx_naval_waiting_match(phase,auto_match,mode,match_rule_set,created_at,id);
