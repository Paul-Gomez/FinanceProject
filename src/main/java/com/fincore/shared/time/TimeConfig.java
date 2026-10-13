package com.fincore.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Un Clock inyectable permite probar caducidades (tokens, bloqueos, vencimientos) sin esperar. */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
