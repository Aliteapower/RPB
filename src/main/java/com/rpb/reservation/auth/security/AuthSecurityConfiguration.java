package com.rpb.reservation.auth.security;

import com.rpb.reservation.auth.application.AuthApplicationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class AuthSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    @ConditionalOnProperty(prefix = "rpb.local-auth", name = "enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain authSecurityFilterChain(
        HttpSecurity http,
        ObjectProvider<AuthCookieService> cookieServiceProvider,
        ObjectProvider<AuthApplicationService> authServiceProvider
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        AuthCookieService cookieService = cookieServiceProvider.getIfAvailable();
        AuthApplicationService authService = authServiceProvider.getIfAvailable();
        if (cookieService != null && authService != null) {
            http.addFilterBefore(new AuthSessionFilter(cookieService, authService), UsernamePasswordAuthenticationFilter.class);
        }

        return http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/me/apps").permitAll()
                .requestMatchers("/api/v1/**").permitAll()
                .anyRequest().permitAll()
            )
            .build();
    }
}
