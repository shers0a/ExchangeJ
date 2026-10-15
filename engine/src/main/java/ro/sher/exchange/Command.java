package ro.sher.exchange;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;

public sealed interface Command permits Command.PlaceLimit, Command.PlaceMarket, Command.Cancel, Command.Snapshot{
    // every command carries the future its caller is waiting on
    CompletableFuture<?> result();

    record PlaceLimit(long id, String traderId, Side side, long price, long quantity,
                      CompletableFuture<List<Trade>> result) implements Command {}

    record PlaceMarket(long id, String traderId, Side side, long quantity,
                       CompletableFuture<List<Trade>> result) implements Command {}

    record Cancel(long orderId, CompletableFuture<Optional<Order>> result) implements Command {}

    // reads go through the queue too, so they never race with the worker that changes the book
    record Snapshot(int depth, CompletableFuture<BookSnapshot> result) implements Command {}
}
