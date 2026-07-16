package com.rpb.reservation.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemClockConfiguration {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
