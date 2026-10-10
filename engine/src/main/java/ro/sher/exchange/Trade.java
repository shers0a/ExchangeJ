package ro.sher.exchange;

public record Trade(long buyOrderId, long sellOrderId, long price, long quantity){
    public Trade{
        if(price <= 0){
            throw new IllegalArgumentException("Price must be strictly positive.");
        }

        if(quantity <= 0){
            throw new IllegalArgumentException("Quantity must be strictly positive.");
        }

        if(buyOrderId == sellOrderId){
            throw new IllegalArgumentException("Order Buy Id and Sell Id must not be equal.");
        }
    }
}