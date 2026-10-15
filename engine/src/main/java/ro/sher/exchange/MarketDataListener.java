package ro.sher.exchange;

import java.util.List;

// Receives market data from a SymbolEngine. It is called on the engine's own thread, in the exact order things
// happened in the book, so an implementation must be fast and must not block: hand the data off and return.
public interface MarketDataListener{
    MarketDataListener NONE = new MarketDataListener(){};

    // only called when an order actually traded
    default void onTrades(String symbol, List<Trade> trades){}

    // called after every command that changed the book
    default void onBook(BookSnapshot snapshot){}
}
