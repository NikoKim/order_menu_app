package com.order.menu.module.entertainment.mode.guessstock.service;

import static org.junit.jupiter.api.Assertions.*;

import com.order.menu.module.entertainment.mode.guessstock.agent.AiModelCostPolicy;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiModelSettings;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.yaml.snakeyaml.Yaml;

@EnabledIfSystemProperty(named = "ai.budget.mysql", matches = "true")
class AiModelBudgetMysqlTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            Map<?, ?> config = new Yaml().load(Files.readString(Path.of("src/main/resources/application-dev.yml")));
            Map<?, ?> db = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
            return new DriverManagerDataSource(db.get("url").toString(), db.get("username").toString(),
                    db.get("password").toString());
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean AiModelSettings settings() { return new AiModelSettings(); }
        @Bean AiModelBudgetService budgets(JdbcTemplate jdbc, AiModelSettings settings) {
            return new AiModelBudgetService(jdbc, settings);
        }
    }

    @Test
    void eightConcurrentAgentsReserveAndSettleWithoutLockUpgradeDeadlocks() throws Exception {
        long sessionId = -Math.max(1, System.nanoTime());
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var budgets = context.getBean(AiModelBudgetService.class);
            var jdbc = context.getBean(JdbcTemplate.class);
            var rates = AiModelCostPolicy.rates("https://api.deepseek.com", "deepseek-v4-pro", new AiModelSettings());
            var usage = new ModelAiDecisionService.InvocationTelemetry(100, 2600, 100, 200, 1, 1800, 800);
            var executor = Executors.newFixedThreadPool(8);
            try {
                CountDownLatch ready = new CountDownLatch(8);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int actor = 0; actor < 8; actor++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        for (int review = 0; review < 4; review++) {
                            var reservation = budgets.reserve(sessionId, 180, 8, rates, 8000);
                            Thread.sleep(10);
                            budgets.settle(reservation, usage);
                        }
                        return null;
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                for (var future : futures) future.get(30, TimeUnit.SECONDS);
                var actual = budgets.usage(sessionId);
                assertEquals(32, actual.requestCount());
                assertEquals(0, actual.reservedYuan().compareTo(BigDecimal.ZERO));
                assertEquals(0, actual.estimatedCostYuan().compareTo(rates.cost(2600, 1800, 100)
                        .multiply(BigDecimal.valueOf(32))));
                assertEquals(2600L * 32, actual.promptTokens());
                assertEquals(1800L * 32, actual.promptCacheHitTokens());

                List<AiModelBudgetService.Reservation> held = Collections.synchronizedList(new ArrayList<>());
                futures.clear();
                for (int actor = 0; actor < 8; actor++) {
                    futures.add(executor.submit(() -> {
                        for (int review = 0; review < 2; review++) {
                            try { held.add(budgets.reserve(sessionId, 180, 8, rates, 8000)); }
                            catch (ModelAiDecisionService.ModelDecisionException exhausted) { }
                        }
                    }));
                }
                for (var future : futures) future.get(30, TimeUnit.SECONDS);
                BigDecimal reservationCost = rates.cost(8000, 0, 384);
                int slots = BigDecimal.ONE.subtract(actual.estimatedCostYuan())
                        .divideToIntegralValue(reservationCost).intValueExact();
                assertEquals(slots, held.size());
                var full = budgets.usage(sessionId);
                assertEquals(32 + slots, full.requestCount());
                assertTrue(full.estimatedCostYuan().add(full.reservedYuan()).compareTo(BigDecimal.ONE) <= 0);
                var rejected = new ModelAiDecisionService.InvocationTelemetry(1, 0, 0, 400, 1, 0, 0);
                for (var reservation : held) budgets.settle(reservation, rejected);
                assertEquals(0, budgets.usage(sessionId).reservedYuan().signum());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(35, TimeUnit.SECONDS));
                jdbc.update("DELETE FROM guess_stock_ai_budgets WHERE session_id = ?", sessionId);
            }
        }
    }
}
