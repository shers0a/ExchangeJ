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
}
