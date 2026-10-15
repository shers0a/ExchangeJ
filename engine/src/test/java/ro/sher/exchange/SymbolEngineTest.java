package ro.sher.exchange;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ro.sher.exchange.Side.*;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class SymbolEngineTest{
    private SymbolEngine engine;

    @BeforeEach
    public void startEngine(){
        engine = new SymbolEngine("ACME");
        engine.start();
    }

    @AfterEach
    public void stopEngine() throws InterruptedException{
        engine.stop();
    }

    @Test
    public void limitOrderReturnsTradesThroughFuture(){
        engine.submitLimit(1, "bogdan", SELL, 100, 10);
        List<Trade> trades = engine.submitLimit(2, "ana", BUY, 100, 10).join();

        assertEquals(List.of(new Trade(2, 1, 100, 10)), trades);
    }

    @Test
    public void marketOrderReturnsTradesThroughFuture(){
        engine.submitLimit(1, "bogdan", SELL, 100, 10);
        List<Trade> trades = engine.submitMarket(2, "ana", BUY, 10).join();

        assertEquals(List.of(new Trade(2, 1, 100, 10)), trades);
    }

    @Test
    public void cancelReturnsOrderThroughFuture(){
        engine.submitLimit(1, "ana", BUY, 100, 10);

        assertEquals(1, engine.submitCancel(1).join().orElseThrow().getId());
        assertTrue(engine.submitCancel(1).join().isEmpty());
    }

    @Test
    public void invalidOrderFailsItsFutureAndEngineKeepsRunning(){
        CompletableFuture<List<Trade>> bad = engine.submitLimit(1, "ana", BUY, 0, 10);

        CompletionException error = assertThrows(CompletionException.class, bad::join);
        assertInstanceOf(IllegalArgumentException.class, error.getCause());

        engine.submitLimit(2, "bogdan", SELL, 100, 10);
        assertEquals(List.of(new Trade(3, 2, 100, 10)), engine.submitLimit(3, "ana", BUY, 100, 10).join());
    }

    @Test
    public void submitAfterStopFails() throws InterruptedException{
        engine.stop();

        CompletionException error = assertThrows(CompletionException.class,
            () -> engine.submitLimit(1, "ana", BUY, 100, 10).join());
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    public void concurrentOrdersFromManyThreadsAreAllMatched(){
        int threads = 8;
        int ordersPerThread = 1_000;
        AtomicLong nextId = new AtomicLong(1);
        Queue<CompletableFuture<List<Trade>>> results = new ConcurrentLinkedQueue<>();

        // half of the threads only buy, half only sell, all at the same price and quantity 1
        try(ExecutorService pool = Executors.newFixedThreadPool(threads)){
            for(int t = 0; t < threads; t++){
                Side side = t % 2 == 0 ? BUY : SELL;
                pool.submit(() -> {
                    for(int i = 0; i < ordersPerThread; i++){
                        results.add(engine.submitLimit(nextId.getAndIncrement(), "trader", side, 100, 1));
                    }
                });
            }
        }

        long tradedQuantity = results.stream()
            .flatMap(future -> future.join().stream())
            .mapToLong(Trade::quantity)
            .sum();

        // equal numbers of buys and sells at one price: every order must find a match exactly once
        assertEquals(threads / 2 * ordersPerThread, tradedQuantity);
    }

    @Test
    public void snapshotIsReadThroughTheEngine(){
        engine.submitLimit(1, "bogdan", SELL, 101, 10);
        engine.submitLimit(2, "ana", BUY, 99, 5);

        BookSnapshot snapshot = engine.submitSnapshot(5).join();

        assertEquals(List.of(new PriceLevel(99, 5, 1)), snapshot.bids());
        assertEquals(List.of(new PriceLevel(101, 10, 1)), snapshot.asks());
    }

    @Test
    public void listenerGetsTradesAndBookInTheOrderTheyHappened() throws InterruptedException{
        List<Object> events = new CopyOnWriteArrayList<>();
        SymbolEngine recorded = new SymbolEngine("ACME", recordingListener(events));
        recorded.start();
        try{
            recorded.submitLimit(1, "bogdan", SELL, 100, 10);
            recorded.submitLimit(2, "ana", BUY, 100, 4);
            // commands run one by one: when this snapshot is done, the listener calls before it are done too
            recorded.submitSnapshot(1).join();

            assertEquals(List.of(
                new BookSnapshot("ACME", List.of(), List.of(new PriceLevel(100, 10, 1))),
                List.of(new Trade(2, 1, 100, 4)),
                new BookSnapshot("ACME", List.of(), List.of(new PriceLevel(100, 6, 1)))
            ), events);
        }finally{
            recorded.stop();
        }
    }

    @Test
    public void cancelPublishesBookOnlyWhenAnOrderWasCancelled() throws InterruptedException{
        List<Object> events = new CopyOnWriteArrayList<>();
        SymbolEngine recorded = new SymbolEngine("ACME", recordingListener(events));
        recorded.start();
        try{
            recorded.submitLimit(1, "ana", BUY, 100, 10);
            recorded.submitCancel(1);
            recorded.submitCancel(99);
            recorded.submitSnapshot(1).join();

            assertEquals(List.of(
                new BookSnapshot("ACME", List.of(new PriceLevel(100, 10, 1)), List.of()),
                new BookSnapshot("ACME", List.of(), List.of())
            ), events);
        }finally{
            recorded.stop();
        }
    }

    @Test
    public void failingListenerDoesNotFailOrdersOrStopTheEngine() throws InterruptedException{
        MarketDataListener broken = new MarketDataListener(){
            @Override
            public void onBook(BookSnapshot snapshot){
                throw new IllegalStateException("listener is broken");
            }
        };
        SymbolEngine withBrokenListener = new SymbolEngine("ACME", broken);
        withBrokenListener.start();
        try{
            withBrokenListener.submitLimit(1, "bogdan", SELL, 100, 10).join();
            List<Trade> trades = withBrokenListener.submitLimit(2, "ana", BUY, 100, 10).join();

            assertEquals(List.of(new Trade(2, 1, 100, 10)), trades);
            assertTrue(withBrokenListener.submitSnapshot(1).join().asks().isEmpty());
        }finally{
            withBrokenListener.stop();
        }
    }

    private static MarketDataListener recordingListener(List<Object> events){
        return new MarketDataListener(){
            @Override
            public void onTrades(String symbol, List<Trade> trades){
                events.add(trades);
            }

            @Override
            public void onBook(BookSnapshot snapshot){
                events.add(snapshot);
            }
        };
    }
}
