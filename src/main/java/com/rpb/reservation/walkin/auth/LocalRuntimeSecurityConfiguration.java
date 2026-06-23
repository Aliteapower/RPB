package com.rpb.reservation.walkin.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "test"})
@ConditionalOnProperty(prefix = "rpb.local-auth", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LocalAuthProperties.class)
public class LocalRuntimeSecurityConfiguration {

    @Bean
    SecurityFilterChain localRuntimeSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/me/apps").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/walk-ins/direct-seating").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/seatings/*/cleaning/start").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/cleanings/*/complete").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/reservations/today").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/check-in").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/seating/direct").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/queue").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/cancel").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/customers/phone-lookup").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/queue-tickets").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/call").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/skip").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/rejoin").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/seating/direct").permitAll()
                .anyRequest().denyAll()
            )
            .build();
    }
}
