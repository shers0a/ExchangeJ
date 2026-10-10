package ro.sher.exchange;

public class Order{
    private final String symbol;
    private final long price;
    private final long sequence;
    private long remainingQuantity;
    private final long id;
    private final Side side;
    private final String traderId;
    private final long quantity;

    Order(long price, String symbol, Side side, long quantity, long sequence, long id, String traderId){
        if(price <= 0){
            throw new IllegalArgumentException("The price must be strictly positive.");
        }
        
        if(quantity <= 0 ){
            throw new IllegalArgumentException("The quantity must be strictly positive.");
        }

        if(symbol == null || symbol.isBlank()){
            throw new IllegalArgumentException("The name of the stock (symbol) has to be non-null.");
        }
        
        if(side == null){
            throw new IllegalArgumentException("Side has to be non-null of the Side enum.");
        }

        if(traderId == null){
            throw new IllegalArgumentException("The trader's Id must not be null.");
        }

        this.price = price;
        this.symbol = symbol;
        this.side = side;
        this.id = id;
        this.sequence = sequence;
        this.traderId = traderId;
        this.quantity = quantity;

        remainingQuantity = quantity;
    }

    public void fill(long currQuantity){
        if(currQuantity <= 0){
            throw new IllegalArgumentException("Quantity has to be positive.");
        }
        if(remainingQuantity < currQuantity){
            throw new IllegalArgumentException("The remaining quantity must be greater than/equal to the quantity demanded.");
        }
        remainingQuantity -= currQuantity;
    }

    public long getQuantity(){
        return quantity;
    }

    public long getRemainingQuantity(){
        return remainingQuantity;
    }

    public String getSymbol(){
        return symbol;
    }

    public long getPrice(){
        return price;
    }

    public long getSequence(){
        return sequence;
    }

    public long getId(){
        return id;
    }

    public Side getSide(){
        return side;
    }

    public String getTraderId(){
        return traderId;
    }
}