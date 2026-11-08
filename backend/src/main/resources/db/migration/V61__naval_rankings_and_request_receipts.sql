CREATE TABLE naval_ranking_rounds(game_id VARCHAR(80) PRIMARY KEY, credited_at BIGINT NOT NULL);
CREATE TABLE naval_ranking_totals(
 player_key VARCHAR(100) NOT NULL, mode VARCHAR(16) NOT NULL, rule_set VARCHAR(16) NOT NULL,
 name VARCHAR(100) NOT NULL, avatar_url VARCHAR(500) NULL, games BIGINT NOT NULL DEFAULT 0, wins BIGINT NOT NULL DEFAULT 0,
 win_rate DOUBLE GENERATED ALWAYS AS (IF(games=0,0,wins*100.0/games)) STORED,
 best_economy BIGINT NULL,best_game_id VARCHAR(80) NULL,best_finished_at BIGINT NOT NULL DEFAULT 0,
 spent BIGINT NULL,opponent_spent BIGINT NULL,
 PRIMARY KEY(player_key,mode,rule_set), KEY idx_naval_wins(mode,rule_set,wins DESC,name),
 KEY idx_naval_rate(mode,rule_set,win_rate DESC,name),KEY idx_naval_economy(mode,rule_set,best_economy DESC,name)
);
CREATE TABLE naval_action_receipts(
 room_id VARCHAR(36) NOT NULL,player_key VARCHAR(100) NOT NULL,request_id CHAR(36) NOT NULL,
 payload_hash CHAR(64) NOT NULL,version BIGINT NOT NULL,reply_json JSON NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(room_id,player_key,request_id),FOREIGN KEY(room_id) REFERENCES naval_rooms(id) ON DELETE CASCADE
);
INSERT INTO naval_ranking_totals(player_key,mode,rule_set,name,avatar_url,games,wins,best_economy,best_game_id,best_finished_at,spent,opponent_spent)
WITH participants AS (
 SELECT r.game_id,r.finished_at,JSON_UNQUOTE(JSON_EXTRACT(r.result_json,'$.mode')) AS mode,
 COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.result_json,'$.ruleSet')),'CLASSIC') AS rule_set,
 p.player_key,p.name,NULLIF(p.avatar_url,'null') AS avatar_url,p.coins_spent AS spent,
 JSON_EXTRACT(r.result_json,CONCAT('$.players[',2-p.ordinal,'].coinsSpent'))+0 AS opponent_spent,
 IF(JSON_EXTRACT(r.result_json,'$.winner')+0=p.ordinal-1,1,0) AS won
 FROM naval_round_results r JOIN JSON_TABLE(r.result_json,'$.players[*]' COLUMNS(
 ordinal FOR ORDINALITY,player_key VARCHAR(100) PATH '$.key',name VARCHAR(100) PATH '$.name',
 avatar_url VARCHAR(500) PATH '$.avatarUrl' NULL ON EMPTY,coins_spent BIGINT PATH '$.coinsSpent' NULL ON EMPTY
 )) p ON TRUE
 WHERE p.player_key LIKE 'U:%' AND (JSON_UNQUOTE(JSON_EXTRACT(r.result_json,'$.mode'))<>'AI' OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.result_json,'$.battleStarted')),'true')='true')
), ordered AS (
 SELECT *,ROW_NUMBER() OVER(PARTITION BY player_key,mode,rule_set ORDER BY finished_at DESC,game_id DESC) latest,
 ROW_NUMBER() OVER(PARTITION BY player_key,mode,rule_set ORDER BY IF(won=1 AND spent IS NOT NULL AND opponent_spent IS NOT NULL,0,1),opponent_spent-spent DESC,finished_at DESC,game_id DESC) best
 FROM participants
)
SELECT player_key,mode,rule_set,MAX(IF(latest=1,name,NULL)),MAX(IF(latest=1,avatar_url,NULL)),COUNT(*),SUM(won),
 MAX(IF(best=1 AND won=1,opponent_spent-spent,NULL)),MAX(IF(best=1 AND won=1,game_id,NULL)),
 COALESCE(MAX(IF(best=1 AND won=1,finished_at,NULL)),0),MAX(IF(best=1 AND won=1,spent,NULL)),MAX(IF(best=1 AND won=1,opponent_spent,NULL))
FROM ordered GROUP BY player_key,mode,rule_set;
INSERT INTO naval_ranking_rounds SELECT game_id,finished_at FROM naval_round_results;
