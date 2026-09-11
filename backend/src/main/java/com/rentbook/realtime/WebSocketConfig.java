package com.rentbook.realtime;

import com.rentbook.config.RentbookProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over a native WebSocket at {@code /ws}. The handshake is open; the CONNECT frame must carry an
 * access token, and every SUBSCRIBE is authorized (see {@link StompAuthInterceptor}).
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuth;
    private final RentbookProperties properties;

    WebSocketConfig(StompAuthInterceptor stompAuth, RentbookProperties properties) {
        this.stompAuth = stompAuth;
        this.properties = properties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();
        heartbeats.setPoolSize(1);
        heartbeats.setThreadNamePrefix("stomp-heartbeat-");
        heartbeats.initialize();
        // Heartbeats keep proxies (Render, mobile networks) from dropping idle connections.
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {20_000, 20_000})
                .setTaskScheduler(heartbeats);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuth);
    }
}
