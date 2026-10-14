# ExchangeJ

[![CI](https://github.com/shers0a/ExchangeJ/actions/workflows/CI.yaml/badge.svg)](https://github.com/shers0a/ExchangeJ/actions/workflows/CI.yaml)

A stock exchange simulator built around a matching engine written in plain Java 25. Traders place BUY and SELL orders for fictional stocks (e.g. `ACME`). The engine matches them by **price-time priority**: the best price goes first, and orders at the same price are filled in arrival order.

> **Status:** early development. The matching engine (limit and market orders, partial fills, cancellations) is done and tested. Concurrency and the REST API are next (see [Roadmap](#roadmap)).

## Features

- **Domain model:** `Order`, `Trade` and `Side`, with fail-fast validation. Invalid prices, quantities, symbols or trader ids are rejected when the object is created.
- **Partial fills:** an `Order` tracks its remaining quantity through `fill()`. It cannot be overfilled, and filling exactly the remaining quantity is valid.
- **Order book with price-time priority:** limit orders match against the best opposite price first, and orders at the same price fill in arrival order. A single order can sweep several price levels. Trades execute at the resting (maker) order's price.
- **Market orders:** fill against whatever is in the book; the unfilled rest is dropped (immediate-or-cancel) instead of resting.
- **Cancellations in O(1):** an id index (`HashMap`) points straight to the order, so cancelling never scans the book.
- **Unit tests** with JUnit 6, including boundary cases (zero values, filling exactly what is left, sweeping several levels).
- **CI:** every push and pull request is built and tested on GitHub Actions.

## Design decisions

- **Prices are `long` values in ticks** (`101.50` → `10150`). Floating point cannot represent money exactly (`0.1 + 0.2 != 0.3`), and `BigDecimal` allocates objects on what will become the hot path. Prices are converted to decimals only for display.
- **Time priority comes from a sequence number, not a timestamp.** The engine assigns a strictly increasing number to each incoming order. Two orders can share a timestamp and the system clock can jump backwards, but a sequence number is always unique and ordered.
- **`Order` is a class, `Trade` is a record.** An order changes over its lifetime: its remaining quantity decreases as it gets filled, and only `fill()` can change it (no setters). A trade is a historical fact that never changes, so it is an immutable record, validated in its compact constructor.
- **The engine only matches orders.** Checking whether a buyer has the money or a seller has the shares belongs to the API layer in front of the engine. This keeps the engine small, fast and easy to test.

## Getting started

Requirements: **JDK 25** and **Maven 3.9+**.

```bash
git clone https://github.com/shers0a/ExchangeJ.git
cd ExchangeJ
mvn verify
```

`mvn verify` compiles the project and runs all tests.

Project layout (multi-module Maven):

| Module | Purpose |
|---|---|
| `engine` | Pure Java, no frameworks: domain model, order book and matching |

## Roadmap

- [x] Domain model (`Order`, `Trade`, `Side`) with validation and unit tests
- [x] CI with GitHub Actions
- [x] Order book with price-time priority matching
- [x] Market orders and cancellations
- [ ] One dedicated thread per symbol (single-writer model, inspired by LMAX)
- [ ] REST API with Spring Boot
- [ ] Persistence: PostgreSQL, Flyway, Testcontainers
- [ ] Real-time market data over WebSocket
- [ ] Simulated trading bots and JMH benchmarks
- [ ] Docker Compose setup
