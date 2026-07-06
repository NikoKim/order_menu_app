SET @has_nickname_index := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND index_name = 'uk_users_nickname'
);

SET @drop_nickname_index := IF(
  @has_nickname_index > 0,
  'ALTER TABLE users DROP INDEX uk_users_nickname',
  'SELECT 1'
);
PREPARE stmt FROM @drop_nickname_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_nickname_role_index := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND index_name = 'uk_users_nickname_role'
);

SET @add_nickname_role_index := IF(
  @has_nickname_role_index = 0,
  'ALTER TABLE users ADD UNIQUE KEY uk_users_nickname_role (nickname, role)',
  'SELECT 1'
);
PREPARE stmt FROM @add_nickname_role_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
