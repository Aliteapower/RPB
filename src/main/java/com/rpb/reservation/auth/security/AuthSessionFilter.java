package com.rpb.reservation.auth.security;

import com.rpb.reservation.auth.application.AuthApplicationService;
import com.rpb.reservation.auth.application.AuthSessionAuthentication;
import com.rpb.reservation.walkin.api.CurrentActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthSessionFilter extends OncePerRequestFilter {
    private final AuthCookieService cookieService;
    private final AuthApplicationService authService;

    public AuthSessionFilter(AuthCookieService cookieService, AuthApplicationService authService) {
        this.cookieService = cookieService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookieService.readSessionToken(request)
                .flatMap(authService::authenticateSession)
                .map(AuthSessionAuthentication::principal)
                .map(principal -> authentication(principal.toCurrentActor()))
                .ifPresent(authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
        }
        filterChain.doFilter(request, response);
    }

    private static UsernamePasswordAuthenticationToken authentication(CurrentActor actor) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(actor, null, List.of());
        authentication.setDetails(actor);
        return authentication;
    }
}
