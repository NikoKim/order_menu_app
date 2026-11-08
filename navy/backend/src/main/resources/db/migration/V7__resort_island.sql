CREATE TABLE naval_resort_islands (
  player_key VARCHAR(100) NOT NULL PRIMARY KEY,
  state_json JSON NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE naval_island_receipts (
  player_key VARCHAR(100) NOT NULL,
  request_id CHAR(36) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  acknowledgement_json JSON NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY(player_key, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
