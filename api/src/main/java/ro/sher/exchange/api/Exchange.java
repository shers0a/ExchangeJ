package ro.sher.exchange.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import ro.sher.exchange.MarketDataListener;
import ro.sher.exchange.SymbolEngine;

// The whole exchange: one SymbolEngine (one thread, one order book) per configured symbol.
// Spring creates a single instance and gives it to the controllers.
@Component
public class Exchange{
    // filled once in the constructor and never changed afterwards, so many HTTP threads can read it without locks
    private final Map<String, SymbolEngine> engines;
    // HTTP requests run on many threads at once, so the id counter must be atomic
    private final AtomicLong nextOrderId = new AtomicLong(1);

    public Exchange(ExchangeProperties properties, MarketDataListener listener){
        Map<String, SymbolEngine> engines = new LinkedHashMap<>();
        for(String symbol : properties.symbols()){
            engines.put(symbol, new SymbolEngine(symbol, listener));
        }
        // read-only view that keeps the configured order (Map.copyOf would not)
        this.engines = Collections.unmodifiableMap(engines);
    }

    @PostConstruct
    public void start(){
        engines.values().forEach(SymbolEngine::start);
    }

    @PreDestroy
    public void stop() throws InterruptedException{
        for(SymbolEngine engine : engines.values()){
            engine.stop();
        }
    }

    public SymbolEngine engine(String symbol){
        SymbolEngine engine = engines.get(symbol);
        if(engine == null){
            throw new UnknownSymbolException(symbol);
        }
        return engine;
    }

    public Set<String> symbols(){
        return engines.keySet();
    }

    // The API hands out order ids, so they are unique across all symbols and a client can never pick or reuse one.
    public long nextOrderId(){
        return nextOrderId.getAndIncrement();
    }
}
