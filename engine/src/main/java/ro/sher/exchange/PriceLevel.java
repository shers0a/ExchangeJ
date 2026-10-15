package ro.sher.exchange;

// one price level of the book, aggregated: the total quantity still waiting there and how many orders it comes from
public record PriceLevel(long price, long quantity, int orders){}
