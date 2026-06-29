package com.rpb.reservation.walkin.auth;

import com.rpb.reservation.auth.application.AuthApplicationService;
import com.rpb.reservation.auth.security.AuthCookieService;
import com.rpb.reservation.auth.security.AuthSessionFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "test"})
@ConditionalOnProperty(prefix = "rpb.local-auth", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LocalAuthProperties.class)
public class LocalRuntimeSecurityConfiguration {

    @Bean
    SecurityFilterChain localRuntimeSecurityFilterChain(
        HttpSecurity http,
        ObjectProvider<AuthCookieService> cookieServiceProvider,
        ObjectProvider<AuthApplicationService> authServiceProvider
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable);

        AuthCookieService cookieService = cookieServiceProvider.getIfAvailable();
        AuthApplicationService authService = authServiceProvider.getIfAvailable();
        if (cookieService != null && authService != null) {
            http.addFilterBefore(new AuthSessionFilter(cookieService, authService), UsernamePasswordAuthenticationFilter.class);
        }

        return http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/me/apps").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/walk-ins/direct-seating").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/walk-ins/queue").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/seatings/*/cleaning/start").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/seatings/*/table-switch").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/cleanings/*/complete").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/reservations/today").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/reservations/calendar-summary").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/reservations/*/share-info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/public/reservation-shares/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/staff-home/overview").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/tables").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/tables/temporary-groups").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/stores/*/tables/temporary-groups/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/check-in").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/seating/direct").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/seating/check-in-direct").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/queue").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/cancel").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/no-show").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/complete").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/customers/phone-lookup").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/queue-tickets").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/queue-display/state").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/queue-display/media/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/call").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/skip").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/rejoin").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/cancel").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/seating/direct").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/tenants/*").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/tenants/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/restore").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/logo").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/tenants/*/logo").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/*/logo/media/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/profile").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/profile").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/profile/logo").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/profile/logo").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/profile/social-links").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/profile/social-links/*").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/profile/social-links/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/profile/social-links/*/logo").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/profile/social-links/*/logo").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/product-lines").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/product-lines").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/product-lines/*").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/product-lines/*/prices").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/*/product-subscriptions").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/purchase").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/*/renew").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/*/suspend").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/*/cancel").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/*/convert-from-legacy").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/call-screen/text-seed").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/call-screen/text-seed").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/reservation/share-template-seed").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/reservation/share-template-seed").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/call-screen/media-seed").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/call-screen/media-seed").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/call-screen/media").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/call-screen/media/*").permitAll()
                .requestMatchers("/api/v1/stores/*/tenant-admin/**").permitAll()
                .anyRequest().denyAll()
            )
            .build();
    }
}
