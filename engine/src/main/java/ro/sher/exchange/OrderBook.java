package ro.sher.exchange;

import java.util.ArrayDeque;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.OptionalLong;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

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

    public List<Trade> placeLimitOrder(long id, String traderId, Side side, long price, long quantity){
        List<Trade> trades = new ArrayList<>();
        Order order = new Order(price, symbol, side, quantity, nextSequence++, id, traderId);

        switch(side){
            case BUY -> {
                bids.computeIfAbsent(price, p -> new ArrayDeque<>()).addLast(order);
            }

            case SELL -> {
                asks.computeIfAbsent(price, p -> new ArrayDeque<>()).addLast(order);
            }
        }

        return trades;
    }

    private List<Trade> matchEngine(Order next){
        List<Trade> trades = new ArrayList<>();
        
        TreeMap <Long, ArrayDeque<Order>> other= switch(next.getSide()){
            case BUY -> asks;
            case SELL -> bids;
        };
        
        BooleanSupplier crosses = () -> next.getSide() == Side.BUY
            ? other.firstKey() <= next.getPrice()
            : other.firstKey() >= next.getPrice();

        while(next.getRemainingQuantity() > 0 && other.isEmpty() == false && crosses.getAsBoolean() ){
            Map.Entry<Long, ArrayDeque<Order>> bestLevel = other.firstEntry();
            ArrayDeque<Order> queue = bestLevel.getValue();
            Order resting = queue.peekFirst();

            long tradeQuantity = Math.min(next.getRemainingQuantity(), resting.getRemainingQuantity());
            long tradePrice = bestLevel.getKey();

            next.fill(tradeQuantity);
            resting.fill(tradeQuantity);

            long buyOrderId = next.getSide() == Side.BUY ? next.getId() : resting.getId();
            long sellOrderId = next.getSide() == Side.SELL ? next.getId() : resting.getId();
            trades.add(new Trade(buyOrderId, sellOrderId, tradePrice, tradeQuantity));

            if(resting.getRemainingQuantity() == 0){
                queue.pollFirst();

                if(queue.isEmpty()){
                    other.pollFirstEntry();
                }
            }
        }
        
        return trades;
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