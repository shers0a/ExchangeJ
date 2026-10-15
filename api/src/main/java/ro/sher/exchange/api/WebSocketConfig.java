package ro.sher.exchange.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// STOMP over WebSocket: clients connect to /ws and subscribe to topics, e.g. /topic/trades/ACME and /topic/book/ACME.
// The simple broker lives in memory inside the app and sends every message to everyone subscribed to its topic.
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/ws");
        // a client's frames (subscribe, unsubscribe) are handled in the order it sent them
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry){
        registry.enableSimpleBroker("/topic");
        // Messages to a client go out through a thread pool, which could reorder them: an older book snapshot
        // could overtake a newer one. This keeps each client's messages in the order they were published.
        registry.setPreservePublishOrder(true);
    }
}
