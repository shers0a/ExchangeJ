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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class OrderBook{
    private static final long MARKET_BUY_PRICE = Long.MAX_VALUE;
    private static final long MARKET_SELL_PRICE = 1;

    private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();
    private final String symbol;
    private long nextSequence = 0;
    // only the orders resting in the book, so cancel can find them
    private final HashMap<Long, Order> ordersById = new HashMap<>();
    // every id this book ever accepted (also filled, cancelled and market orders), so ids are never reused
    private final Set<Long> usedIds = new HashSet<>();

    public OrderBook(String symbol){
        if(symbol == null || symbol.isBlank()){
            throw new IllegalArgumentException("OrderBook symbol must not be null or blank.");
        }

        this.symbol = symbol;
    }

    public List<Trade> placeLimitOrder(long id, String traderId, Side side, long price, long quantity){
        requireUniqueId(id);
        Order order = new Order(price, symbol, side, quantity, nextSequence++, id, traderId);
        usedIds.add(id);

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

    public List<Trade> placeMarketOrder(long id, String traderId, Side side, long quantity){
        requireUniqueId(id);
        long price = side == Side.BUY ? MARKET_BUY_PRICE : MARKET_SELL_PRICE;
        Order order = new Order(price, symbol, side, quantity, nextSequence++, id, traderId);
        usedIds.add(id);

        return matchEngine(order);
    }

    // called before the Order is built; the id is recorded only after the Order passed validation,
    // so an invalid order does not use up its id
    private void requireUniqueId(long id){
        if(usedIds.contains(id)){
            throw new IllegalArgumentException("Order id " + id + " was already used in this book.");
        }
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

    public BookSnapshot snapshot(int depth){
        if(depth <= 0){
            throw new IllegalArgumentException("Snapshot depth must be strictly positive.");
        }

        return new BookSnapshot(symbol, levels(bids, depth), levels(asks, depth));
    }

    // a TreeMap iterates in the order of its comparator, so the first entries are the best prices
    private static List<PriceLevel> levels(TreeMap<Long, ArrayDeque<Order>> side, int depth){
        List<PriceLevel> levels = new ArrayList<>();

        for(Map.Entry<Long, ArrayDeque<Order>> entry : side.entrySet()){
            if(levels.size() == depth){
                break;
            }

            long quantity = 0;
            for(Order order : entry.getValue()){
                quantity += order.getRemainingQuantity();
            }

            levels.add(new PriceLevel(entry.getKey(), quantity, entry.getValue().size()));
        }

        return levels;
    }

    public String getSymbol(){
        return symbol;
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