package com.miyuki.Inventory.Management.logistics.tracking;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One shared Redis container for all tracking tests (started once per JVM, needs Docker).
 * Tests build their own StringRedisTemplate against it, so no Spring context is required.
 * If GenericContainer<?> does not compile on your Testcontainers version, drop the "<?>".
 */
final class RedisTestSupport {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    private RedisTestSupport() {}

    static StringRedisTemplate template() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    static void flush(StringRedisTemplate template) {
        template.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}
