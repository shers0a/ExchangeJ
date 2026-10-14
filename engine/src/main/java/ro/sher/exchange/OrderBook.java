package ro.sher.exchange;

import java.util.ArrayDeque;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.OptionalLong;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.HashMap;
import java.util.Optional;

public class OrderBook{
    private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();
    private final String symbol;
    private long nextSequence = 0;
    private final HashMap<Long, Order> ordersById = new HashMap<>();

    public OrderBook(String symbol){
        if(symbol == null || symbol.isBlank()){
            throw new IllegalArgumentException("OrderBook symbol must non-null nor blank.");
        }

        this.symbol = symbol;
    }

    public List<Trade> placeLimitOrder(long id, String traderId, Side side, long price, long quantity){
        Order order = new Order(price, symbol, side, quantity, nextSequence++, id, traderId);

        var trades = matchEngine(order);

        if( order.getRemainingQuantity() != 0 ){
            
            ordersById.put(order.getId(), order);

            switch(side){
                case BUY -> {
                    bids.computeIfAbsent(price, p -> new ArrayDeque<>()).addLast(order);
                }

                case SELL -> {
                    asks.computeIfAbsent(price, p -> new ArrayDeque<>()).addLast(order);
                }
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
                ordersById.remove(resting.getId());

                if(queue.isEmpty()){
                    other.pollFirstEntry();
                }
            }
        }
        
        return trades;
    }

    public Optional<Order> cancel(long orderId){
        Order order = ordersById.remove(orderId);

        if(order == null){
            return Optional.empty();
        }

        TreeMap<Long, ArrayDeque<Order>> side = switch(order.getSide()){
            case BUY -> bids;
            case SELL -> asks;
        };

        ArrayDeque<Order> queue = side.get(order.getPrice());
        queue.remove(order);

        if(queue.isEmpty()){
            side.remove(order.getPrice());
        }

        return Optional.of(order);
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