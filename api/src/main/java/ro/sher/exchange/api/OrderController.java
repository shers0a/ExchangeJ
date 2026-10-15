package ro.sher.exchange.api;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ro.sher.exchange.BookSnapshot;
import ro.sher.exchange.SymbolEngine;
import ro.sher.exchange.Trade;

// Every method returns a CompletableFuture: the HTTP thread hands the command to the symbol's engine and is freed
// right away; Spring writes the response when the engine completes the future (asynchronous request processing).
@RestController
@RequestMapping("/api/symbols")
public class OrderController{
    private final Exchange exchange;

    public OrderController(Exchange exchange){
        this.exchange = exchange;
    }

    @GetMapping
    public Set<String> symbols(){
        return exchange.symbols();
    }

    @PostMapping("/{symbol}/orders")
    public CompletableFuture<OrderResponse> placeOrder(@PathVariable String symbol, @RequestBody PlaceOrderRequest request){
        SymbolEngine engine = exchange.engine(symbol);
        if(request.type() == null){
            throw new IllegalArgumentException("Order type is required: LIMIT or MARKET.");
        }

        long orderId = exchange.nextOrderId();
        CompletableFuture<List<Trade>> trades = switch(request.type()){
            case LIMIT -> {
                if(request.price() == null){
                    throw new IllegalArgumentException("A limit order needs a price.");
                }
                yield engine.submitLimit(orderId, request.traderId(), request.side(), request.price(), request.quantity());
            }
            case MARKET -> {
                if(request.price() != null){
                    throw new IllegalArgumentException("A market order must not have a price.");
                }
                yield engine.submitMarket(orderId, request.traderId(), request.side(), request.quantity());
            }
        };

        // if the order is not done yet, thenApply runs on the engine's thread: keep it tiny (it only wraps the list)
        return trades.thenApply(executed -> new OrderResponse(orderId, symbol, executed));
    }

    @DeleteMapping("/{symbol}/orders/{orderId}")
    public CompletableFuture<ResponseEntity<CancelResponse>> cancelOrder(@PathVariable String symbol, @PathVariable long orderId){
        return exchange.engine(symbol).submitCancel(orderId)
            .thenApply(cancelled -> cancelled
                .map(order -> ResponseEntity.ok(CancelResponse.from(order)))
                .orElseGet(() -> ResponseEntity.notFound().build()));
    }

    @GetMapping("/{symbol}/book")
    public CompletableFuture<BookSnapshot> book(@PathVariable String symbol, @RequestParam(defaultValue = "10") int depth){
        return exchange.engine(symbol).submitSnapshot(depth);
    }
}
