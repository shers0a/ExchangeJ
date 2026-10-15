package ro.sher.exchange.api;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import ro.sher.exchange.BookSnapshot;
import ro.sher.exchange.MarketDataListener;
import ro.sher.exchange.Trade;

// Sends the engines' market data to WebSocket subscribers: /topic/trades/{symbol} and /topic/book/{symbol}.
//
// The engine calls this on its own thread, and that thread must only do matching. So the listener methods just
// queue the work on one separate publishing thread; turning objects into JSON and sending happens there.
// It is a single thread on purpose: messages leave in the same order the engines produced them.
@Component
public class StompMarketDataPublisher implements MarketDataListener{
    private final SimpMessagingTemplate messaging;
    private final ExecutorService publisher = Executors.newSingleThreadExecutor(r -> new Thread(r, "market-data-publisher"));

    public StompMarketDataPublisher(SimpMessagingTemplate messaging){
        this.messaging = messaging;
    }

    @Override
    public void onTrades(String symbol, List<Trade> trades){
        publisher.execute(() -> messaging.convertAndSend("/topic/trades/" + symbol, trades));
    }

    @Override
    public void onBook(BookSnapshot snapshot){
        publisher.execute(() -> messaging.convertAndSend("/topic/book/" + snapshot.symbol(), snapshot));
    }

    @PreDestroy
    public void stop(){
        publisher.shutdown();
    }
}
