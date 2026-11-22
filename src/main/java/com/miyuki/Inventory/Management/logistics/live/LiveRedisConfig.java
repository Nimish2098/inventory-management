package com.miyuki.Inventory.Management.logistics.live;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(name = "tracking.live.enabled", havingValue = "true", matchIfMissing = true)
public class LiveRedisConfig {

    @Bean
    RedisMessageListenerContainer liveRedisListenerContainer(
            RedisConnectionFactory connectionFactory, LiveMessageListener listener, TrackingProperties props) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(props.getLiveChannel()));
        return container;
    }
}
