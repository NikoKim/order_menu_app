CREATE TABLE moment_quotes (
  moment_id BIGINT PRIMARY KEY,
  quote_type VARCHAR(20) NOT NULL,
  quoted_moment_id BIGINT NOT NULL,
  quoted_comment_id BIGINT NULL,
  author_user_id BIGINT NOT NULL,
  author_nickname VARCHAR(100) NOT NULL,
  author_avatar_url VARCHAR(500) NULL,
  author_role VARCHAR(20) NULL,
  moment_title VARCHAR(120) NOT NULL,
  quoted_content VARCHAR(2000) NULL,
  quoted_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_moment_quote_target (quoted_moment_id),
  KEY idx_moment_quote_comment (quoted_comment_id),
  CONSTRAINT fk_moment_quote_owner FOREIGN KEY (moment_id) REFERENCES moments(id) ON DELETE CASCADE,
  CONSTRAINT chk_moment_quote_type CHECK (quote_type IN ('MOMENT', 'COMMENT'))
);

CREATE TABLE moment_mentions (
  moment_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (moment_id, user_id),
  KEY idx_moment_mention_user (user_id, created_at),
  CONSTRAINT fk_moment_mention_moment FOREIGN KEY (moment_id) REFERENCES moments(id) ON DELETE CASCADE,
  CONSTRAINT fk_moment_mention_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE moment_comment_mentions (
  comment_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (comment_id, user_id),
  KEY idx_moment_comment_mention_user (user_id, created_at),
  CONSTRAINT fk_moment_comment_mention_comment FOREIGN KEY (comment_id) REFERENCES moment_comments(id) ON DELETE CASCADE,
  CONSTRAINT fk_moment_comment_mention_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
