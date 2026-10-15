package ro.sher.exchange;

import java.util.List;

// A read-only picture of the best levels of a book at one moment: bids best (highest) first, asks best (lowest) first.
// The lists are copied into immutable lists, so a snapshot can be handed to other threads safely.
public record BookSnapshot(String symbol, List<PriceLevel> bids, List<PriceLevel> asks){
    public BookSnapshot{
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}
