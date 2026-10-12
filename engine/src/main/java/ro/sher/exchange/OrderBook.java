package ro.sher.exchange;

import java.util.ArrayDeque;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.OptionalLong;
import java.util.Map;

public class OrderBook{
    private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();
    private final String symbol;
    private long nextSequence = 0;

    public OrderBook(String symbol){
        if(symbol == null || symbol.isBlank()){
            throw new IllegalArgumentException("OrderBook symbol must non-null nor blank.");
        }

        this.symbol = symbol;
    }

    public void placeLimitOrder(long id, String traderId, Side side, long price, long quantity){
        Order order = new Order(price, symbol, side, quantity, nextSequence++, id, traderId);

        switch(side){
            case BUY -> {
                bids.computeIfAbsent(price, p -> new ArrayDeque<>()).addLast(order);
            }

            case SELL -> {
                asks.computeIfAbsent(price, p -> new ArrayDeque<>()).addLast(order);
            }
        }
    }

    public OptionalLong bestBid(){
        Map.Entry<Long, ArrayDeque<Order>> entry = bids.firstEntry();
        
        if(entry == null)
            return OptionalLong.empty();
        
        return OptionalLong.of(entry.getKey());
    }

    public OptionalLong bestAsk(){
        Map.Entry<Long, ArrayDeque<Order>> entry = asks.firstEntry();

        if(entry == null)
            return OptionalLong.empty();
        
        return OptionalLong.of(entry.getKey());
    }
}