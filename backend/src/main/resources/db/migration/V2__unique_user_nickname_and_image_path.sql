ALTER TABLE users DROP INDEX uk_users_nickname;
ALTER TABLE users ADD UNIQUE KEY uk_users_nickname_role (nickname, role);
