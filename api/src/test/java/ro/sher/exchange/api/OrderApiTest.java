package ro.sher.exchange.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

// Starts the real application on a random port and talks to it over HTTP, like a client would.
// Spring keeps one application (so the same order books) for every test in this class:
// that is why each test trades its own symbol and cannot see the orders of another test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "exchange.symbols=MATCH,BOOK,MARKET,CANCEL,ERRORS")
public class OrderApiTest{
    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    public void listsConfiguredSymbols() throws Exception{
        HttpResponse<String> response = get("/api/symbols");

        assertEquals(200, response.statusCode());
        assertEquals(List.of("MATCH", "BOOK", "MARKET", "CANCEL", "ERRORS"), JsonPath.read(response.body(), "$"));
    }

    @Test
    public void limitOrdersMatchAndReturnTheirTrades() throws Exception{
        HttpResponse<String> sell = placeLimit("MATCH", "bogdan", "SELL", 10150, 50);
        assertEquals(200, sell.statusCode());
        assertEquals(List.of(), JsonPath.read(sell.body(), "$.trades"));

        HttpResponse<String> buy = placeLimit("MATCH", "ana", "BUY", 10200, 20);
        assertEquals(200, buy.statusCode());

        // the buyer pays the resting seller's price, not its own limit
        assertEquals(number(buy.body(), "$.orderId"), number(buy.body(), "$.trades[0].buyOrderId"));
        assertEquals(number(sell.body(), "$.orderId"), number(buy.body(), "$.trades[0].sellOrderId"));
        assertEquals(10150, number(buy.body(), "$.trades[0].price"));
        assertEquals(20, number(buy.body(), "$.trades[0].quantity"));
    }

    @Test
    public void bookShowsAggregatedLevels() throws Exception{
        placeLimit("BOOK", "ana", "BUY", 100, 10);
        placeLimit("BOOK", "dan", "BUY", 100, 5);
        placeLimit("BOOK", "eva", "BUY", 99, 1);
        placeLimit("BOOK", "bogdan", "SELL", 105, 7);

        HttpResponse<String> book = get("/api/symbols/BOOK/book?depth=1");

        assertEquals(200, book.statusCode());
        assertEquals(1, ((List<?>) JsonPath.read(book.body(), "$.bids")).size());
        assertEquals(100, number(book.body(), "$.bids[0].price"));
        assertEquals(15, number(book.body(), "$.bids[0].quantity"));
        assertEquals(2, number(book.body(), "$.bids[0].orders"));
        assertEquals(105, number(book.body(), "$.asks[0].price"));
    }

    @Test
    public void marketOrderTradesAndItsRestIsDropped() throws Exception{
        placeLimit("MARKET", "bogdan", "SELL", 100, 30);

        HttpResponse<String> market = post("/api/symbols/MARKET/orders",
            """
            {"traderId": "ana", "side": "BUY", "type": "MARKET", "quantity": 50}
            """);

        assertEquals(200, market.statusCode());
        assertEquals(30, number(market.body(), "$.trades[0].quantity"));
        String book = get("/api/symbols/MARKET/book").body();
        assertEquals(List.of(), JsonPath.read(book, "$.bids"));
        assertEquals(List.of(), JsonPath.read(book, "$.asks"));
    }

    @Test
    public void cancelReturnsTheCancelledOrderOnlyOnce() throws Exception{
        long orderId = number(placeLimit("CANCEL", "ana", "BUY", 100, 10).body(), "$.orderId");

        HttpResponse<String> cancelled = delete("/api/symbols/CANCEL/orders/" + orderId);
        assertEquals(200, cancelled.statusCode());
        assertEquals(10, number(cancelled.body(), "$.cancelledQuantity"));

        assertEquals(404, delete("/api/symbols/CANCEL/orders/" + orderId).statusCode());
        assertEquals(List.of(), JsonPath.read(get("/api/symbols/CANCEL/book").body(), "$.bids"));
    }

    @Test
    public void invalidRequestsAreRejectedWithClearErrors() throws Exception{
        HttpResponse<String> zeroPrice = placeLimit("ERRORS", "ana", "BUY", 0, 10);
        assertEquals(400, zeroPrice.statusCode());
        assertEquals("The price must be strictly positive.", JsonPath.read(zeroPrice.body(), "$.detail"));

        assertEquals(400, post("/api/symbols/ERRORS/orders",
            """
            {"traderId": "ana", "side": "BUY", "type": "LIMIT", "quantity": 10}
            """).statusCode());
        assertEquals(400, post("/api/symbols/ERRORS/orders",
            """
            {"traderId": "ana", "side": "BUY", "type": "MARKET", "price": 100, "quantity": 10}
            """).statusCode());
        assertEquals(400, post("/api/symbols/ERRORS/orders",
            """
            {"traderId": "ana", "side": "HOLD", "type": "LIMIT", "price": 100, "quantity": 10}
            """).statusCode());
        assertEquals(400, get("/api/symbols/ERRORS/book?depth=0").statusCode());
        assertEquals(404, get("/api/symbols/NOPE/book").statusCode());
    }

    private HttpResponse<String> placeLimit(String symbol, String traderId, String side, long price, long quantity)
            throws IOException, InterruptedException{
        return post("/api/symbols/" + symbol + "/orders",
            """
            {"traderId": "%s", "side": "%s", "type": "LIMIT", "price": %d, "quantity": %d}
            """.formatted(traderId, side, price, quantity));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException{
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    private HttpResponse<String> post(String path, String json) throws IOException, InterruptedException{
        return send(HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private HttpResponse<String> delete(String path) throws IOException, InterruptedException{
        return send(HttpRequest.newBuilder(uri(path)).DELETE());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException{
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path){
        return URI.create("http://localhost:" + port + path);
    }

    private static long number(String json, String path){
        return JsonPath.<Number>read(json, path).longValue();
    }
}
