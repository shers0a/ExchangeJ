package ro.sher.exchange;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class TradeTest{
    @Test
    public void checksNegativeOrZeroPrice(){
        assertThrows(IllegalArgumentException.class,
            () -> {Trade trade = new Trade(12, 0, 0, 12);}
        );
    }

    @Test
    public void checksNegativeOrZeroQuantity(){
        assertThrows(IllegalArgumentException.class,
            ()->{Trade trade = new Trade(12, 0, 12, 0);}
        );
    }
    
    @Test
    public void checksIdEquality(){
        assertThrows(IllegalArgumentException.class,
            () -> {Trade trade = new Trade(12, 12, 12, 100);}
        );
    }

    @Test
    public void checksGetters(){
        Trade trade = new Trade(12,11, 10, 100 );
        assertAll(
            () -> assertEquals(10, trade.price(), "Price getter does not work."),
            () -> assertEquals(100, trade.quantity(), "Quantity getter does not work.")
        );
    }
}