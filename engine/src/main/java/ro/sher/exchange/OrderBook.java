package ro.sher.exchange;

import java.util.ArrayDeque;
import java.util.TreeMap;
import java.util.Comparator;

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
            case BUY:
                bids.computeIfAbsent(price, p -> new ArrayDeque<>()).addLast(order);
                break;

            case SELL:
                asks.computeIfAbsent(price, p -> new ArrayDeque<>()).addLast(order);
                break;
        }       
    }
}