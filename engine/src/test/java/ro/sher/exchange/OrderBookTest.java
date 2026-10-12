package ro.sher.exchange;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ro.sher.exchange.Side.*;
import java.util.OptionalLong;

public class OrderBookTest{
    @Test
    public void checksNullSymbol(){
        assertThrows(IllegalArgumentException.class,
        () -> new OrderBook(null));
    }

    @Test
    public void checksBlankSymbol(){
        assertThrows(IllegalArgumentException.class,
        () -> new OrderBook(""));
    }

    @Test
    public void checkBestBidGetter(){
        OrderBook orderBook = new OrderBook("Google");

        orderBook.placeLimitOrder(123, "john", BUY, 20, 100);

        assertTrue(orderBook.bestAsk().isEmpty());
        assertEquals( OptionalLong.of(20), orderBook.bestBid(), "The bestBid() method does not work.");
    }

    @Test
    public void checkBestBidGetterWithTwoBids(){
        OrderBook orderBook = new OrderBook("Google");

        orderBook.placeLimitOrder(123, "john", BUY, 20, 100);
        orderBook.placeLimitOrder(124, "john1", BUY, 19, 100);

        assertEquals(OptionalLong.of(20), orderBook.bestBid(), "The placeLimitOrder() method does not sort Bids correctly.");
    }

    @Test
    public void checkBestAskGetter(){
        OrderBook orderBook = new OrderBook("Amazon");

        orderBook.placeLimitOrder(123, "john", SELL, 20, 100);
        
        assertTrue(orderBook.bestBid().isEmpty());
        assertEquals(OptionalLong.of(20), orderBook.bestAsk(), "The bestAsk() method does not work.");
    }

    @Test
    public void checkBestAskGetterWithTwoAsks(){
        OrderBook orderBook = new OrderBook("Amazon");

        orderBook.placeLimitOrder(123, "john", SELL, 20, 100);
        orderBook.placeLimitOrder(124, "john1", SELL, 19, 100);

        assertEquals(OptionalLong.of(19), orderBook.bestAsk(),"The bestAsk() method does not sort Asks correctly.");
    }

    @Test
    public void checkBestBidEmpty(){
        OrderBook orderBook = new OrderBook("Google");

        assertEquals(OptionalLong.empty(),
        orderBook.bestBid());
    }

    @Test
    public void checkBestAskEmpty(){
        OrderBook orderBook = new OrderBook("Amazon");
        
        assertEquals(OptionalLong.empty(),
        orderBook.bestAsk());
    }
}