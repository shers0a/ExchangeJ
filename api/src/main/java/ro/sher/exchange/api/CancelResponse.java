package ro.sher.exchange.api;

import ro.sher.exchange.Order;
import ro.sher.exchange.Side;

// An immutable copy of the cancelled order. The engine's Order is mutable, so it is never sent out as it is.
public record CancelResponse(long orderId, String symbol, Side side, long price, long cancelledQuantity){
    public static CancelResponse from(Order order){
        return new CancelResponse(order.getId(), order.getSymbol(), order.getSide(), order.getPrice(),
            order.getRemainingQuantity());
    }
}
