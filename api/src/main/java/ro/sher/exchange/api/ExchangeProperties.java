package ro.sher.exchange.api;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

// bound from application.yaml (exchange.symbols); the app refuses to start without at least one symbol
@ConfigurationProperties("exchange")
public record ExchangeProperties(List<String> symbols){
    public ExchangeProperties{
        if(symbols == null || symbols.isEmpty()){
            throw new IllegalArgumentException("exchange.symbols must list at least one symbol.");
        }
        symbols = List.copyOf(symbols);
    }
}
