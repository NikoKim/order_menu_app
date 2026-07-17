ALTER TABLE users
  ADD COLUMN moment_profile_visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';

CREATE TABLE moment_user_preferences (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, target_user_id BIGINT NOT NULL,
  hide_target_moments TINYINT NOT NULL DEFAULT 0, hide_my_moments TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_moment_pref_users (user_id,target_user_id), KEY idx_moment_pref_target(target_user_id),
  CONSTRAINT fk_moment_pref_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_moment_pref_target FOREIGN KEY(target_user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE moments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, author_user_id BIGINT NOT NULL, title VARCHAR(120) NOT NULL,
  content TEXT NOT NULL, visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  shop_id BIGINT NULL, dish_id BIGINT NULL, image_urls TEXT NULL, edited_at DATETIME NULL,
  published_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_moment_feed(status,visibility,published_at,id), KEY idx_moment_author(author_user_id,status,published_at),
  FULLTEXT KEY ft_moment_title_content(title,content), CONSTRAINT fk_moment_author FOREIGN KEY(author_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_moment_shop FOREIGN KEY(shop_id) REFERENCES shops(id) ON DELETE SET NULL,
  CONSTRAINT fk_moment_dish FOREIGN KEY(dish_id) REFERENCES dishes(id) ON DELETE SET NULL
);

CREATE TABLE moment_topics (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50) NOT NULL, normalized_name VARCHAR(50) NOT NULL,
  created_by_user_id BIGINT NOT NULL, usage_count BIGINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_moment_topic_name(normalized_name), KEY idx_moment_topic_usage(usage_count),
  CONSTRAINT fk_moment_topic_creator FOREIGN KEY(created_by_user_id) REFERENCES users(id)
);
CREATE TABLE moment_topic_relations (
  moment_id BIGINT NOT NULL, topic_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(moment_id,topic_id), KEY idx_moment_topic_relation_topic(topic_id,moment_id),
  CONSTRAINT fk_mtr_moment FOREIGN KEY(moment_id) REFERENCES moments(id) ON DELETE CASCADE,
  CONSTRAINT fk_mtr_topic FOREIGN KEY(topic_id) REFERENCES moment_topics(id) ON DELETE CASCADE
);
CREATE TABLE moment_likes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, moment_id BIGINT NOT NULL, user_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_moment_like(moment_id,user_id), CONSTRAINT fk_ml_moment FOREIGN KEY(moment_id) REFERENCES moments(id) ON DELETE CASCADE,
  CONSTRAINT fk_ml_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE moment_reactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, moment_id BIGINT NOT NULL, user_id BIGINT NOT NULL, emoji VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_moment_reaction(moment_id,user_id,emoji),
  CONSTRAINT fk_mr_moment FOREIGN KEY(moment_id) REFERENCES moments(id) ON DELETE CASCADE,
  CONSTRAINT fk_mr_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE moment_comments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, moment_id BIGINT NOT NULL, author_user_id BIGINT NOT NULL,
  parent_comment_id BIGINT NULL, reply_to_comment_id BIGINT NULL, content VARCHAR(2000) NULL, image_urls TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_mc_roots(moment_id,parent_comment_id,created_at,id), KEY idx_mc_parent(parent_comment_id,created_at,id),
  CONSTRAINT fk_mc_moment FOREIGN KEY(moment_id) REFERENCES moments(id) ON DELETE CASCADE,
  CONSTRAINT fk_mc_author FOREIGN KEY(author_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_mc_parent FOREIGN KEY(parent_comment_id) REFERENCES moment_comments(id) ON DELETE CASCADE,
  CONSTRAINT fk_mc_reply FOREIGN KEY(reply_to_comment_id) REFERENCES moment_comments(id) ON DELETE SET NULL
);
CREATE TABLE moment_comment_likes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, comment_id BIGINT NOT NULL, user_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_moment_comment_like(comment_id,user_id), CONSTRAINT fk_mcl_comment FOREIGN KEY(comment_id) REFERENCES moment_comments(id) ON DELETE CASCADE,
  CONSTRAINT fk_mcl_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE moment_reads (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, moment_id BIGINT NOT NULL,
  read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_moment_read(user_id,moment_id),
  KEY idx_moment_read_moment(moment_id), CONSTRAINT fk_mread_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_mread_moment FOREIGN KEY(moment_id) REFERENCES moments(id) ON DELETE CASCADE
);

ALTER TABLE chat_messages
  ADD COLUMN shared_moment_id BIGINT NULL, ADD COLUMN shared_moment_comment_id BIGINT NULL,
  ADD COLUMN shared_moment_title VARCHAR(120) NULL, ADD COLUMN shared_moment_content VARCHAR(500) NULL,
  ADD COLUMN shared_moment_author_name VARCHAR(100) NULL, ADD COLUMN shared_moment_cover_url VARCHAR(500) NULL;
ALTER TABLE chat_messages DROP CHECK chk_chat_messages_type,
  ADD CONSTRAINT chk_chat_messages_type CHECK(type IN ('TEXT','DISH_SHARE','ORDER_SHARE','IMAGE_SHARE','SHOP_SHARE','MOMENT_SHARE','MOMENT_COMMENT_SHARE','SYSTEM'));
