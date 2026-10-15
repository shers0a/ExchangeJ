# ExchangeJ

[![CI](https://github.com/shers0a/ExchangeJ/actions/workflows/CI.yaml/badge.svg)](https://github.com/shers0a/ExchangeJ/actions/workflows/CI.yaml)

A stock exchange simulator built around a matching engine written in plain Java 25. Traders place BUY and SELL orders for fictional stocks (e.g. `ACME`). The engine matches them by **price-time priority**: the best price goes first, and orders at the same price are filled in arrival order. A Spring Boot API exposes it over REST, streams live market data over WebSocket, and ships as a Docker image.

> **Status:** active development. The matching engine, the REST API, live market data and the Docker setup are done and tested. Accounts and persistence are next (see [Roadmap](#roadmap)).

## Quick start

With Docker (or Podman):

```bash
git clone https://github.com/shers0a/ExchangeJ.git
cd ExchangeJ
docker compose up --build        # Podman: podman-compose up --build
```

Then open **http://localhost:8080**: a demo page shows the order book and the trades live and lets you place orders. Open it in two tabs to see one tab's orders appear in the other.

Without Docker, you need **JDK 25** and **Maven 3.9+**:

```bash
mvn verify                                  # compile and run all tests
java -jar api/target/api-0.1.0-SNAPSHOT.jar # start the API on port 8080
```

## Features

**Matching engine** (`engine`, plain Java)
- **Order book with price-time priority:** limit orders match against the best opposite price first, and orders at the same price fill in arrival order. A single order can sweep several price levels. Trades execute at the resting (maker) order's price.
- **Partial fills:** an order tracks its remaining quantity; whatever a limit order cannot fill rests in the book.
- **Market orders:** fill against whatever is in the book; the unfilled rest is dropped (immediate-or-cancel).
- **Cancellations:** an id index (`HashMap`) finds the order directly, so cancelling never scans the whole book.
- **Unique order ids:** an id can be used only once per book, so every trade points to exactly one order.
- **Single-writer concurrency:** each symbol's order book is owned by one dedicated thread. Other threads submit commands to a `BlockingQueue` and get the result back as a `CompletableFuture`, so the book needs no locks. Reads (book snapshots) go through the same queue.
- **Market data events:** after every change the engine reports the trades and the new top of the book to a listener, in the exact order things happened.
- **Fail-fast validation:** invalid prices, quantities, symbols or trader ids are rejected when an order is created.

**API** (`api`, Spring Boot 4)
- **REST endpoints** to place and cancel orders and read the book. Requests are handled asynchronously: the HTTP thread hands the order to the engine and is freed until the result is ready.
- **Live market data over WebSocket (STOMP):** trades and book updates per symbol, delivered to each client in publication order.
- **Errors as Problem Details** (RFC 9457): invalid orders return `400` with a readable message, unknown symbols and orders `404`.
- **Demo page** served by the app.

**Quality**
- **76 tests** with JUnit 6: unit tests with boundary cases, an 8-thread test with 8,000 concurrent orders, HTTP tests against the running app and a WebSocket test with a real STOMP client.
- **CI** on GitHub Actions: every push builds, runs all tests and builds the Docker image.

## API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/symbols` | Symbols traded on this exchange |
| `POST` | `/api/symbols/{symbol}/orders` | Place an order; returns its id and the trades it produced |
| `DELETE` | `/api/symbols/{symbol}/orders/{orderId}` | Cancel a resting order (`404` if it is no longer in the book) |
| `GET` | `/api/symbols/{symbol}/book?depth=10` | Best price levels on each side |

Prices are integers in ticks (cents): `10150` means 101.50.

```bash
curl -X POST localhost:8080/api/symbols/ACME/orders -H 'Content-Type: application/json' \
     -d '{"traderId": "ana", "side": "BUY", "type": "LIMIT", "price": 10150, "quantity": 20}'
# {"orderId":2,"symbol":"ACME","trades":[{"buyOrderId":2,"sellOrderId":1,"price":10150,"quantity":20}]}
```

A market order has `"type": "MARKET"` and no price. Order ids are assigned by the API.

**WebSocket:** connect a STOMP client to `ws://localhost:8080/ws` and subscribe to `/topic/trades/{symbol}` (list of trades) and `/topic/book/{symbol}` (book snapshot).

## Design decisions

- **Prices are `long` values in ticks** (`101.50` → `10150`). Floating point cannot represent money exactly (`0.1 + 0.2 != 0.3`), and `BigDecimal` allocates objects on the hot path. Prices become decimals only for display.
- **Time priority comes from a sequence number, not a timestamp.** The engine numbers incoming orders in a strictly increasing order. Two orders can share a timestamp and the system clock can jump backwards; a sequence number is always unique and ordered.
- **One thread per symbol instead of locks.** Only the symbol's own thread ever touches its book, so there is no shared mutable state to protect. Contention-free and easy to reason about: every command runs to completion before the next one starts.
- **`Order` is a class, `Trade` is a record.** An order changes over its lifetime (only through `fill()`, no setters). A trade is a historical fact that never changes, so it is an immutable record. The API never sends the mutable `Order` out: it copies what it needs into immutable responses.
- **The engine knows nothing about Spring or WebSocket.** It reports market data to a plain `MarketDataListener` interface; the API implements it and publishes to WebSocket on a separate thread, so JSON and networking never slow down matching.
- **The engine only matches orders.** Checking whether a buyer has the money or a seller has the shares belongs to a layer in front of it (accounts are on the roadmap). This keeps the engine small, fast and easy to test.

## Project layout

| Module | Purpose |
|---|---|
| `engine` | Pure Java, no frameworks: domain model, order book, matching and the per-symbol engine thread |
| `api` | Spring Boot application: REST endpoints, WebSocket market data, demo page |

## Roadmap

- [x] Domain model with validation and unit tests
- [x] Order book with price-time priority matching
- [x] Market orders and cancellations
- [x] One dedicated thread per symbol (single-writer model, inspired by LMAX)
- [x] REST API with Spring Boot
- [x] Real-time market data over WebSocket
- [x] Docker image, Compose setup and CI
- [ ] Accounts, balances and positions, checked before orders reach the engine
- [ ] Persistence: PostgreSQL, Flyway, Testcontainers
- [ ] Authentication (JWT), idempotency keys and rate limiting
- [ ] Bounded command queues (backpressure) and incremental book updates
- [ ] Simulated trading bots and JMH benchmarks
