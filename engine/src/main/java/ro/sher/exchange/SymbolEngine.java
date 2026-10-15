package ro.sher.exchange;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

// Runs one OrderBook on its own thread (single-writer). Other threads never touch the book:
// they put commands in a queue and get the result back through a CompletableFuture.
public class SymbolEngine{
    // how many levels per side the market data listener receives after every change
    public static final int PUBLISHED_DEPTH = 10;
    private static final System.Logger LOG = System.getLogger(SymbolEngine.class.getName());

    private final OrderBook book;
    private final MarketDataListener listener;
    private final BlockingQueue<Command> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean running = false;

    public SymbolEngine(String symbol){
        this(symbol, MarketDataListener.NONE);
    }

    public SymbolEngine(String symbol, MarketDataListener listener){
        if(listener == null){
            throw new IllegalArgumentException("Listener must not be null, use MarketDataListener.NONE instead.");
        }

        this.book = new OrderBook(symbol);
        this.listener = listener;
        this.worker = new Thread(this::run, "engine-" + symbol);
    }

    public String getSymbol(){
        return book.getSymbol();
    }

    public void start(){
        running = true;
        worker.start();
    }

    public void stop() throws InterruptedException{
        running = false;
        worker.interrupt();
        worker.join();
    }

    public CompletableFuture<List<Trade>> submitLimit(long id, String traderId, Side side, long price, long quantity){
        var result = new CompletableFuture<List<Trade>>();
        submit(new Command.PlaceLimit(id, traderId, side, price, quantity, result));
        return result;
    }

    public CompletableFuture<List<Trade>> submitMarket(long id, String traderId, Side side, long quantity){
        var result = new CompletableFuture<List<Trade>>();
        submit(new Command.PlaceMarket(id, traderId, side, quantity, result));
        return result;
    }

    public CompletableFuture<Optional<Order>> submitCancel(long orderId){
        var result = new CompletableFuture<Optional<Order>>();
        submit(new Command.Cancel(orderId, result));
        return result;
    }

    public CompletableFuture<BookSnapshot> submitSnapshot(int depth){
        var result = new CompletableFuture<BookSnapshot>();
        submit(new Command.Snapshot(depth, result));
        return result;
    }

    private void submit(Command command){
        queue.add(command);

        // stop() may have run in the meantime and the worker may already be gone:
        // take the command back so its caller does not wait forever
        if(!running && queue.remove(command)){
            command.result().completeExceptionally(new IllegalStateException("Engine is not running."));
        }
    }

    private void run(){
        while(running){
            Command command;
            try{
                command = queue.take();
            }catch(InterruptedException e){
                break;
            }
            execute(command);
        }

        // fail whatever is still waiting, so no caller hangs forever
        Command left;
        while((left = queue.poll()) != null){
            left.result().completeExceptionally(new IllegalStateException("Engine stopped."));
        }
    }

    private void execute(Command command){
        try{
            switch(command){
                case Command.PlaceLimit c -> {
                    List<Trade> trades = book.placeLimitOrder(c.id(), c.traderId(), c.side(), c.price(), c.quantity());
                    c.result().complete(trades);
                    publish(trades);
                }
                case Command.PlaceMarket c -> {
                    List<Trade> trades = book.placeMarketOrder(c.id(), c.traderId(), c.side(), c.quantity());
                    c.result().complete(trades);
                    publish(trades);
                }
                case Command.Cancel c -> {
                    Optional<Order> cancelled = book.cancel(c.orderId());
                    c.result().complete(cancelled);
                    if(cancelled.isPresent()){
                        publish(List.of());
                    }
                }
                case Command.Snapshot c -> c.result().complete(book.snapshot(c.depth()));
            }
        }catch(RuntimeException e){
            // a bad order must not kill the worker: hand the error to the caller and keep going
            command.result().completeExceptionally(e);
        }
    }

    // The caller already has its result at this point: the order executed no matter what the listener does.
    // A failing listener is logged and ignored, so it can neither kill the worker nor fail an order that went through.
    private void publish(List<Trade> trades){
        try{
            if(!trades.isEmpty()){
                listener.onTrades(book.getSymbol(), trades);
            }
            listener.onBook(book.snapshot(PUBLISHED_DEPTH));
        }catch(RuntimeException e){
            LOG.log(System.Logger.Level.WARNING, "Market data listener failed for " + book.getSymbol(), e);
        }
    }
}
