package ro.sher.exchange.api;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import ro.sher.exchange.BookSnapshot;
import ro.sher.exchange.PriceLevel;
import ro.sher.exchange.Trade;

// A real STOMP client subscribes over WebSocket, orders go in over HTTP, and the market data must arrive.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "exchange.symbols=LIVE")
public class MarketDataWebSocketTest{
    @LocalServerPort
    private int port;

    @Autowired
    private SubscriptionRecorder subscriptions;

    private final HttpClient http = HttpClient.newHttpClient();
    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    public void connect() throws Exception{
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        session = stompClient.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter(){})
            .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    public void disconnect(){
        session.disconnect();
        stompClient.stop();
    }

    @Test
    public void subscribersReceiveTradesAndBookUpdates() throws Exception{
        // Trade[] and not List<Trade>: the STOMP client drops generic types, so a List would come back full of maps
        BlockingQueue<Trade[]> trades = new LinkedBlockingQueue<>();
        BlockingQueue<BookSnapshot> books = new LinkedBlockingQueue<>();
        subscribe("/topic/trades/LIVE", Trade[].class, trades);
        subscribe("/topic/book/LIVE", BookSnapshot.class, books);

        placeLimit("bogdan", "SELL", 10150, 50);
        assertEquals(new BookSnapshot("LIVE", List.of(), List.of(new PriceLevel(10150, 50, 1))), next(books));

        placeLimit("ana", "BUY", 10150, 20);
        Trade[] traded = next(trades);
        assertEquals(1, traded.length);
        assertEquals(10150, traded[0].price());
        assertEquals(20, traded[0].quantity());
        assertEquals(new BookSnapshot("LIVE", List.of(), List.of(new PriceLevel(10150, 30, 1))), next(books));
    }

    @SuppressWarnings("unchecked")
    private <T> void subscribe(String topic, Type payloadType, BlockingQueue<T> received) throws InterruptedException{
        session.subscribe(topic, new StompFrameHandler(){
            @Override
            public Type getPayloadType(StompHeaders headers){
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload){
                received.add((T) payload);
            }
        });
        // subscribing is asynchronous: without this wait the first message could be published before
        // the broker knows about the subscription, and the test would randomly miss it
        subscriptions.awaitSubscription(topic);
    }

    private static <T> T next(BlockingQueue<T> received) throws InterruptedException{
        T message = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "no message arrived within 5 seconds");
        return message;
    }

    private void placeLimit(String traderId, String side, long price, long quantity) throws Exception{
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/symbols/LIVE/orders"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                """
                {"traderId": "%s", "side": "%s", "type": "LIMIT", "price": %d, "quantity": %d}
                """.formatted(traderId, side, price, quantity)))
            .build();

        assertEquals(200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    // Remembers every subscription the broker has finished registering.
    // The simple broker does not answer subscriptions with a STOMP RECEIPT, so the test watches the broker instead.
    static class SubscriptionRecorder{
        private final BlockingQueue<String> registered = new LinkedBlockingQueue<>();

        void awaitSubscription(String destination) throws InterruptedException{
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while(true){
                String next = registered.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                assertNotNull(next, "the broker never registered the subscription to " + destination);
                if(next.equals(destination)){
                    return;
                }
            }
        }
    }

    // test-only configuration, added on top of the application's own
    @TestConfiguration
    static class TrackSubscriptions{
        @Bean
        SubscriptionRecorder subscriptionRecorder(){
            return new SubscriptionRecorder();
        }

        @Bean
        WebSocketMessageBrokerConfigurer subscriptionTracking(SubscriptionRecorder recorder){
            return new WebSocketMessageBrokerConfigurer(){
                @Override
                public void configureClientInboundChannel(ChannelRegistration registration){
                    registration.interceptors(new ExecutorChannelInterceptor(){
                        // runs after a handler processed a client message; we only care about the broker and SUBSCRIBE
                        @Override
                        public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                                                        Exception ex){
                            if(handler instanceof SimpleBrokerMessageHandler
                                && SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.SUBSCRIBE){
                                recorder.registered.add(SimpMessageHeaderAccessor.getDestination(message.getHeaders()));
                            }
                        }
                    });
                }
            };
        }
    }
}
