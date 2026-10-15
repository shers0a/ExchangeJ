package ro.sher.exchange.api;

import java.util.List;

import ro.sher.exchange.Trade;

// what the client gets back after placing an order: its id and the trades it produced right away
public record OrderResponse(long orderId, String symbol, List<Trade> trades){}
