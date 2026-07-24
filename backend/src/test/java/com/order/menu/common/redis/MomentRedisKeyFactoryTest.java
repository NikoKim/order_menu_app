package com.order.menu.common.redis;

import com.order.menu.config.OrderMenuRedisProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MomentRedisKeyFactoryTest {

    private final RedisKeyFactory factory;

    MomentRedisKeyFactoryTest() {
        OrderMenuRedisProperties properties = new OrderMenuRedisProperties();
        properties.setEnvironment("test");
        factory = new RedisKeyFactory(properties);
    }

    @Test
    void generatesEnvironmentScopedVersionedMomentKeys() {
        assertThat(factory.momentBase(8L)).isEqualTo("oma:test:moment:base:v1:8");
        assertThat(factory.momentFeedVersion()).isEqualTo("oma:test:moment:feed:version");
        assertThat(factory.momentFeedViewerVersion(3L))
                .isEqualTo("oma:test:moment:feed:viewer-version:3");
        assertThat(factory.momentFeed(3L, "RECOMMENDED", 4L, 6L, 2L, 20L))
                .isEqualTo("oma:test:moment:feed:v2:3:RECOMMENDED:4:6:2:20");
        assertThat(factory.momentTopicVersion(7L)).isEqualTo("oma:test:moment:topic:version:7");
        assertThat(factory.momentTopicIds(3L, 7L, 4L, 2L, 20L))
                .isEqualTo("oma:test:moment:topic-ids:v1:3:7:4:2:20");
        assertThat(factory.momentUserVersion(9L)).isEqualTo("oma:test:moment:user:version:9");
        assertThat(factory.momentUserIds(3L, 9L, 4L, 2L, 20L))
                .isEqualTo("oma:test:moment:user-ids:v1:3:9:4:2:20");
        assertThat(factory.momentUnread(3L)).isEqualTo("oma:test:moment:unread:v1:3");
        assertThat(factory.momentHotTopics()).isEqualTo("oma:test:moment:topic:hot:v1");
        assertThat(factory.momentHotIds()).isEqualTo("oma:test:moment:hot-ids:v1");
        assertThat(factory.momentCommentsFirst(8L, 10L))
                .isEqualTo("oma:test:moment:comments:first:v1:8:10");
        assertThat(factory.momentHotComment(8L)).isEqualTo("oma:test:moment:hot-comment:v1:8");
        assertThat(factory.momentStats(8L)).isEqualTo("oma:test:moment:stats:v1:8");
    }

    @Test
    void partitionsPermissionSensitiveListsByViewer() {
        assertThat(factory.momentTopicIds(1L, 7L, 0L, 1L, 10L))
                .isNotEqualTo(factory.momentTopicIds(2L, 7L, 0L, 1L, 10L));
        assertThat(factory.momentUserIds(1L, 9L, 0L, 1L, 10L))
                .isNotEqualTo(factory.momentUserIds(2L, 9L, 0L, 1L, 10L));
    }
}
