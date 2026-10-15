package ro.sher.exchange.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ro.sher.exchange.MarketDataListener;

// Which listener the engines report to. For now nobody listens; the WebSocket step replaces this bean.
@Configuration
public class MarketDataConfig{
    @Bean
    public MarketDataListener marketDataListener(){
        return MarketDataListener.NONE;
    }
}
