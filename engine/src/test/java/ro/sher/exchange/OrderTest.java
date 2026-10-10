package ro.sher.exchange;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OrderTest{
    @Test
    public void checksNonNegativePrice(){
        assertThrows(IllegalArgumentException.class,
        ()->{Order order = new Order(-1, "ACME", Side.BUY, 100, 2, 13, "26279");});
    }

    @Test
    public void checkZeroPrice(){
        assertThrows(IllegalArgumentException.class,
        ()->{Order order = new Order(0, "ACME", Side.BUY, 100, 2, 13, "26279");});
    }
    @Test
    public void checksNonNegativeQuantity(){
        assertThrows(IllegalArgumentException.class,
        ()->{Order order = new Order(10150, "ACME", Side.BUY, -1, 2, 13, "26279");});
    }

    @Test
    public void checksNonNullSymbol(){
        assertThrows(IllegalArgumentException.class,
        ()->{Order order = new Order(10150, null, Side.BUY, 100, 2, 13, "26279");});
    }

    @Test
    public void checkNonBlankSymbol(){
         assertThrows(IllegalArgumentException.class,
        ()->{Order order = new Order(10150, "", Side.BUY, 100, 2, 13, "26279");});
    }

    @Test 
    public void checkEqualityRemainingQuantity(){
        Order order = new Order(10150, "ACME", Side.BUY, 100, 2, 13, "26279");
        
        assertEquals(100, order.getRemainingQuantity(),
        "Initial Quantity is not equal to the Remaining Quantity.");
    }

    @Test
    public void checksRemainingQuantity(){
        Order order = new Order(10150, "ACME", Side.BUY, 100, 2, 13, "26279");

        order.fill(30);
        assertEquals(70, order.getRemainingQuantity(),
        "Fill method does not work properly.");
    }

    @Test
    public void checksOverFilling(){
        Order order = new Order(10150, "ACME", Side.BUY, 100, 2, 13, "26279");
        
        assertThrows(IllegalArgumentException.class, 
        () -> {order.fill(101);});
    }

    @Test
    public void checksZeroQuantity(){
        assertThrows(IllegalArgumentException.class,
        () -> {Order order = new Order(10150, "ACME", Side.BUY, 0, 2, 13, "26279");});
    }
    @Test
    public void checksFillAsMuchAsQuantity(){
        Order order = new Order(10150, "ACME", Side.BUY, 100, 2, 13, "26279");
        order.fill(100);
        assertEquals(0, order.getRemainingQuantity());
    }

    @Test
    public void checksGettersReturnConstructorValues(){
        Order order = new Order(10150, "ACME", Side.SELL, 100, 7, 42, "trader-1");

        assertAll(
            () -> assertEquals(10150, order.getPrice()),
            () -> assertEquals("ACME", order.getSymbol()),
            () -> assertEquals(Side.SELL, order.getSide()),
            () -> assertEquals(100, order.getQuantity()),
            () -> assertEquals(7, order.getSequence()),
            () -> assertEquals(42, order.getId()),
            () -> assertEquals("trader-1", order.getTraderId())
        );
    }
}