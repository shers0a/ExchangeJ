package ro.sher.exchange;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ro.sher.exchange.Side.*;
import java.util.List;
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

    @Test
    public void matchesBuyAgainstSellAtSamePrice(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 10);
        List<Trade> trades = orderBook.placeLimitOrder(2, "ana", BUY, 100, 10);

        assertEquals(List.of(new Trade(2, 1, 100, 10)), trades);
        assertTrue(orderBook.bestBid().isEmpty());
        assertTrue(orderBook.bestAsk().isEmpty());
    }

    @Test
    public void fullyFilledOrderDoesNotRestInBook(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 10);
        orderBook.placeLimitOrder(2, "ana", BUY, 100, 10);
        List<Trade> trades = orderBook.placeLimitOrder(3, "cristi", SELL, 100, 5);

        assertTrue(trades.isEmpty());
        assertTrue(orderBook.bestBid().isEmpty());
        assertEquals(OptionalLong.of(100), orderBook.bestAsk());
    }

    @Test
    public void noTradeWhenPricesDoNotCross(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 10);
        List<Trade> trades = orderBook.placeLimitOrder(2, "ana", BUY, 99, 10);

        assertTrue(trades.isEmpty());
        assertEquals(OptionalLong.of(99), orderBook.bestBid());
        assertEquals(OptionalLong.of(100), orderBook.bestAsk());
    }

    @Test
    public void incomingOrderPartiallyFilledRestsWithRemainder(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 30);
        List<Trade> trades = orderBook.placeLimitOrder(2, "ana", BUY, 100, 100);

        assertEquals(List.of(new Trade(2, 1, 100, 30)), trades);
        assertEquals(OptionalLong.of(100), orderBook.bestBid());
        assertTrue(orderBook.bestAsk().isEmpty());

        // the remaining 70 must still be in the book
        List<Trade> next = orderBook.placeLimitOrder(3, "cristi", SELL, 100, 100);

        assertEquals(List.of(new Trade(2, 3, 100, 70)), next);
        assertTrue(orderBook.bestBid().isEmpty());
        assertEquals(OptionalLong.of(100), orderBook.bestAsk());
    }

    @Test
    public void restingOrderPartiallyFilledKeepsItsPlace(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 100);
        orderBook.placeLimitOrder(2, "ana", BUY, 100, 30);
        List<Trade> trades = orderBook.placeLimitOrder(3, "cristi", BUY, 100, 70);

        assertEquals(List.of(new Trade(3, 1, 100, 70)), trades);
        assertTrue(orderBook.bestAsk().isEmpty());
        assertTrue(orderBook.bestBid().isEmpty());
    }

    @Test
    public void buySweepsMultipleAskLevelsFromLowestPrice(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 101, 50);
        orderBook.placeLimitOrder(2, "cristi", SELL, 100, 30);
        List<Trade> trades = orderBook.placeLimitOrder(3, "ana", BUY, 101, 100);

        assertEquals(List.of(
            new Trade(3, 2, 100, 30),
            new Trade(3, 1, 101, 50)
        ), trades);
        assertEquals(OptionalLong.of(101), orderBook.bestBid());
        assertTrue(orderBook.bestAsk().isEmpty());
    }

    @Test
    public void buyDoesNotMatchAboveItsLimitPrice(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 30);
        orderBook.placeLimitOrder(2, "cristi", SELL, 102, 50);
        List<Trade> trades = orderBook.placeLimitOrder(3, "ana", BUY, 101, 100);

        assertEquals(List.of(new Trade(3, 1, 100, 30)), trades);
        assertEquals(OptionalLong.of(101), orderBook.bestBid());
        assertEquals(OptionalLong.of(102), orderBook.bestAsk());
    }

    @Test
    public void ordersAtSamePriceAreFilledInArrivalOrder(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 10);
        orderBook.placeLimitOrder(2, "cristi", SELL, 100, 10);
        List<Trade> first = orderBook.placeLimitOrder(3, "ana", BUY, 100, 10);
        List<Trade> second = orderBook.placeLimitOrder(4, "dan", BUY, 100, 10);

        assertEquals(List.of(new Trade(3, 1, 100, 10)), first);
        assertEquals(List.of(new Trade(4, 2, 100, 10)), second);
    }

    @Test
    public void incomingBuyTradesAtRestingSellPrice(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 99, 10);
        List<Trade> trades = orderBook.placeLimitOrder(2, "ana", BUY, 101, 10);

        assertEquals(99, trades.get(0).price());
    }

    @Test
    public void incomingSellTradesAtRestingBuyPrice(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "ana", BUY, 101, 10);
        List<Trade> trades = orderBook.placeLimitOrder(2, "bogdan", SELL, 99, 10);

        assertEquals(101, trades.get(0).price());
    }

    @Test
    public void sellSweepsMultipleBidLevelsFromHighestPrice(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "ana", BUY, 100, 50);
        orderBook.placeLimitOrder(2, "dan", BUY, 101, 30);
        List<Trade> trades = orderBook.placeLimitOrder(3, "bogdan", SELL, 99, 100);

        assertEquals(List.of(
            new Trade(2, 3, 101, 30),
            new Trade(1, 3, 100, 50)
        ), trades);
        assertTrue(orderBook.bestBid().isEmpty());
        assertEquals(OptionalLong.of(99), orderBook.bestAsk());
    }

    @Test
    public void rejectsInvalidOrder(){
        OrderBook orderBook = new OrderBook("ACME");

        assertThrows(IllegalArgumentException.class,
        () -> orderBook.placeLimitOrder(1, "ana", BUY, 0, 10));
        assertThrows(IllegalArgumentException.class,
        () -> orderBook.placeLimitOrder(2, "ana", BUY, 100, 0));
    }

    @Test
    public void cancelRemovesOrderFromBook(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "ana", BUY, 100, 10);

        assertEquals(1, orderBook.cancel(1).orElseThrow().getId());
        assertTrue(orderBook.bestBid().isEmpty());
    }

    @Test
    public void cancelLastOrderAtLevelMovesBestPriceToNextLevel(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 10);
        orderBook.placeLimitOrder(2, "cristi", SELL, 101, 10);
        orderBook.cancel(1);

        assertEquals(OptionalLong.of(101), orderBook.bestAsk());
    }

    @Test
    public void cancelKeepsOtherOrdersAtSameLevel(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 10);
        orderBook.placeLimitOrder(2, "cristi", SELL, 100, 10);
        orderBook.cancel(1);
        List<Trade> trades = orderBook.placeLimitOrder(3, "ana", BUY, 100, 10);

        assertEquals(List.of(new Trade(3, 2, 100, 10)), trades);
    }

    @Test
    public void cancelUnknownOrderReturnsEmpty(){
        OrderBook orderBook = new OrderBook("ACME");

        assertTrue(orderBook.cancel(42).isEmpty());
    }

    @Test
    public void cancelFullyFilledOrderReturnsEmpty(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 10);
        orderBook.placeLimitOrder(2, "ana", BUY, 100, 10);

        assertTrue(orderBook.cancel(1).isEmpty());
        assertTrue(orderBook.cancel(2).isEmpty());
    }

    @Test
    public void cancelPartiallyFilledOrderRemovesTheRest(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 100);
        orderBook.placeLimitOrder(2, "ana", BUY, 100, 30);

        assertEquals(70, orderBook.cancel(1).orElseThrow().getRemainingQuantity());
        assertTrue(orderBook.bestAsk().isEmpty());
    }

    @Test
    public void cancelledOrderDoesNotMatch(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "ana", BUY, 100, 10);
        orderBook.cancel(1);
        List<Trade> trades = orderBook.placeLimitOrder(2, "bogdan", SELL, 100, 10);

        assertTrue(trades.isEmpty());
        assertEquals(OptionalLong.of(100), orderBook.bestAsk());
    }

    @Test
    public void cancelSameOrderTwiceReturnsEmptySecondTime(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "ana", BUY, 100, 10);

        assertTrue(orderBook.cancel(1).isPresent());
        assertTrue(orderBook.cancel(1).isEmpty());
    }

    @Test
    public void rejectsDuplicateIdWithoutTouchingTheBook(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "ana", BUY, 100, 10);

        assertThrows(IllegalArgumentException.class,
        () -> orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 10));

        // the rejected SELL must not have traded with the resting BUY
        assertEquals(OptionalLong.of(100), orderBook.bestBid());
        assertTrue(orderBook.bestAsk().isEmpty());
        assertEquals(10, orderBook.cancel(1).orElseThrow().getRemainingQuantity());
    }

    @Test
    public void marketBuySweepsAsksFromLowestPrice(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 105, 50);
        orderBook.placeLimitOrder(2, "cristi", SELL, 100, 30);
        List<Trade> trades = orderBook.placeMarketOrder(3, "ana", BUY, 60);

        assertEquals(List.of(
            new Trade(3, 2, 100, 30),
            new Trade(3, 1, 105, 30)
        ), trades);
        assertEquals(OptionalLong.of(105), orderBook.bestAsk());
        assertTrue(orderBook.bestBid().isEmpty());
    }

    @Test
    public void marketSellSweepsBidsFromHighestPrice(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "ana", BUY, 99, 50);
        orderBook.placeLimitOrder(2, "dan", BUY, 101, 30);
        List<Trade> trades = orderBook.placeMarketOrder(3, "bogdan", SELL, 60);

        assertEquals(List.of(
            new Trade(2, 3, 101, 30),
            new Trade(1, 3, 99, 30)
        ), trades);
        assertEquals(OptionalLong.of(99), orderBook.bestBid());
        assertTrue(orderBook.bestAsk().isEmpty());
    }

    @Test
    public void marketOrderRemainderDoesNotRest(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 30);
        List<Trade> trades = orderBook.placeMarketOrder(2, "ana", BUY, 100);

        assertEquals(List.of(new Trade(2, 1, 100, 30)), trades);
        assertTrue(orderBook.bestBid().isEmpty());
        assertTrue(orderBook.bestAsk().isEmpty());
        assertTrue(orderBook.cancel(2).isEmpty());
    }

    @Test
    public void marketOrderOnEmptyBookProducesNoTrades(){
        OrderBook orderBook = new OrderBook("ACME");

        assertTrue(orderBook.placeMarketOrder(1, "ana", BUY, 10).isEmpty());
        assertTrue(orderBook.bestBid().isEmpty());
        assertTrue(orderBook.bestAsk().isEmpty());
    }

    @Test
    public void marketOrderRejectsInvalidInput(){
        OrderBook orderBook = new OrderBook("ACME");

        orderBook.placeLimitOrder(1, "bogdan", SELL, 100, 10);

        assertThrows(IllegalArgumentException.class,
        () -> orderBook.placeMarketOrder(2, "ana", BUY, 0));
        assertThrows(IllegalArgumentException.class,
        () -> orderBook.placeMarketOrder(3, "ana", null, 10));
        assertThrows(IllegalArgumentException.class,
        () -> orderBook.placeMarketOrder(1, "ana", BUY, 10));
        assertEquals(OptionalLong.of(100), orderBook.bestAsk());
    }
}