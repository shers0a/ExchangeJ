package ro.sher.exchange.api;

import ro.sher.exchange.Side;

// JSON body of POST /api/symbols/{symbol}/orders. There is no id: the API assigns it.
// price is a Long (not long) because a market order has none; everything else is validated by the engine.
public record PlaceOrderRequest(String traderId, Side side, OrderType type, Long price, long quantity){}
