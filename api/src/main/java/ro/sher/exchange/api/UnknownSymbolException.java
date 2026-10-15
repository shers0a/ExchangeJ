package ro.sher.exchange.api;

public class UnknownSymbolException extends RuntimeException{
    public UnknownSymbolException(String symbol){
        super("Unknown symbol: " + symbol);
    }
}
