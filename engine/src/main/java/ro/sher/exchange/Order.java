package ro.sher.exchange;

public class Order{
    private final String symbol;
    private final long price;
    private final long sequence;
    private long remainingQuantity;
    private final long id;
    private final Side side;

    Order(long price, String symbol, Side side, long quantity, long sequence, long id){
        if(price <= 0 || quantity <= 0){
            throw new IllegalArgumentException("The price must be strictly positive.");
        }
        
        if(symbol == null){
            throw new IllegalArgumentException("The name of the stock (symbol) has to be non-null.");

            symbol.isBlank();
        }
        
        if(side == null){
            throw new IllegalArgumentException("Side has to be non-null of the Side enum.");
        }

        this.price = price;
        this.symbol = symbol;
        this.side = side;

        remainingQuantity = quantity;
    }

    public void fill(long quantity){
        if(quantity <= 0 || remainingQuantity < quantity){
            throw new IllegalArgumentException("Quantity has to be positive.");
        }

        remainingQuantity -= quantity;
    }

    public long getRemainingQuantity(){
        return remainingQuantity;
    }
}