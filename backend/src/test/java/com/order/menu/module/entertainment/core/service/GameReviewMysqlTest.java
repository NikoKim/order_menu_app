package com.order.menu.module.entertainment.core.service;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.order.menu.module.entertainment.core.mapper.GameReviewMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.yaml.snakeyaml.Yaml;

@EnabledIfSystemProperty(named = "game.reviews.mysql", matches = "true")
class GameReviewMysqlTest {
    private static final String[] TABLES = { "game_reviews", "guess_stock_participants", "game_sessions", "games", "users" };

    @Test
    void realMysqlChecksSoloAndMultiplayerEligibilityAndRetrySafeReviews() throws Exception {
        Map<?, ?> config = new Yaml().load(Files.readString(Path.of("src/main/resources/application-dev.yml")));
        Map<?, ?> db = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
        var dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", db.get("url").toString(),
                db.get("username").toString(), db.get("password").toString());
        String prefix = "qa_review_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_";
        var mybatis = new MybatisConfiguration();
        mybatis.setEnvironment(new Environment("reviews-test", new JdbcTransactionFactory(), dataSource));
        mybatis.addInterceptor(new FixtureTables(prefix));
        mybatis.addMapper(GameReviewMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(mybatis);
        try (var session = factory.openSession(); var sql = session.getConnection().createStatement()) {
            try {
                // Prefix every fixture and migration table; application data is never modified.
                sql.execute(fixtureSql("CREATE TABLE games (id BIGINT PRIMARY KEY) ENGINE=InnoDB", prefix));
                sql.execute(fixtureSql("CREATE TABLE users (id BIGINT PRIMARY KEY) ENGINE=InnoDB", prefix));
                sql.execute(fixtureSql("CREATE TABLE game_sessions (id BIGINT PRIMARY KEY, game_id BIGINT, player_user_id BIGINT, started_at DATETIME NULL) ENGINE=InnoDB", prefix));
                sql.execute(fixtureSql("CREATE TABLE guess_stock_participants (session_id BIGINT, player_user_id BIGINT, participant_type VARCHAR(20), record_deleted TINYINT) ENGINE=InnoDB", prefix));
                String migration = fixtureSql(Files.readString(Path.of("src/main/resources/db/migration/V52__game_reviews.sql")), prefix)
                        .replaceAll("CONSTRAINT (\\w+)", "CONSTRAINT " + prefix + "$1");
                ScriptUtils.executeSqlScript(session.getConnection(), new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8)));
                sql.execute(fixtureSql("INSERT INTO games VALUES (12), (13)", prefix));
                sql.execute(fixtureSql("INSERT INTO users VALUES (7), (8)", prefix));
                sql.execute(fixtureSql("INSERT INTO game_sessions VALUES (1, 12, 7, NOW()), (2, 13, 9, NOW()), (3, 12, 10, NULL)", prefix));
                sql.execute(fixtureSql("INSERT INTO guess_stock_participants VALUES (1, 8, 'PLAYER', 1), (1, 11, 'AI', 0)", prefix));
                GameReviewMapper mapper = session.getMapper(GameReviewMapper.class);
                assertTrue(mapper.hasPlayed(12L, 7L));
                assertTrue(mapper.hasPlayed(12L, 8L), "multiplayer participants retain eligibility after hiding history");
                assertFalse(mapper.hasPlayed(12L, 9L), "playing another game is insufficient");
                assertFalse(mapper.hasPlayed(12L, 10L), "unstarted games are insufficient");
                assertFalse(mapper.hasPlayed(12L, 11L), "AI opponents cannot review");
                assertFalse(mapper.hasPlayed(12L, 99L));
                mapper.upsert(12L, 7L, 4, "Player's first review");
                mapper.upsert(12L, 7L, 5, "Updated review");
                mapper.upsert(12L, 8L, 3, "Second player");
                try (var rows = sql.executeQuery(fixtureSql("SELECT COUNT(*) FROM game_reviews", prefix))) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                }
                assertEquals(4.0, mapper.averageRating(12L).doubleValue());
                assertEquals(0.0, mapper.averageRating(99L).doubleValue());
                session.rollback();
            } finally {
                if (!prefix.matches("qa_review_[a-f0-9]{12}_")) throw new IllegalStateException("Invalid fixture prefix");
                for (String table : TABLES) sql.execute("DROP TABLE IF EXISTS `" + prefix + table + "`");
            }
        }
    }

    private static String fixtureSql(String sql, String prefix) {
        for (String table : TABLES) sql = sql.replaceAll("\\b" + table + "\\b", prefix + table);
        return sql;
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class }))
    static class FixtureTables implements Interceptor {
        private final String prefix;
        FixtureTables(String prefix) { this.prefix = prefix; }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            var bound = ((StatementHandler) invocation.getTarget()).getBoundSql();
            SystemMetaObject.forObject(bound).setValue("sql", fixtureSql(bound.getSql(), prefix));
            return invocation.proceed();
        }
    }
}
