package com.order.menu.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisProfileConfigurationTest {

    @Test
    void devLoadsOnlyOptionalWorkspaceRedisFiles() throws IOException {
        List<PropertySource<?>> sources = load("application-dev.yml");

        assertThat(property(sources, "spring.config.import[0]"))
                .isEqualTo("optional:file:./.env.redis[.properties]");
        assertThat(property(sources, "spring.config.import[1]"))
                .isEqualTo("optional:file:./backend/.env.redis[.properties]");
        assertThat(property(sources, "order-menu.redis.environment"))
                .isEqualTo("${ORDER_MENU_REDIS_ENV:dev}");
    }

    @Test
    void prodRequiresTheServerRedisFileAndUsesProdKeys() throws IOException {
        List<PropertySource<?>> sources = load("application-prod.yml");

        assertThat(property(sources, "spring.config.import"))
                .isEqualTo("file:/www/server/SpringBoot/.env.redis[.properties]");
        assertThat(property(sources, "order-menu.redis.environment"))
                .isEqualTo("${ORDER_MENU_REDIS_ENV:prod}");
    }

    private List<PropertySource<?>> load(String resource) throws IOException {
        return new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream().map(source -> source.getProperty(name))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
}
