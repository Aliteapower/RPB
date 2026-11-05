package com.rpb.reservation.wallet.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Browser CSRF boundary for cookie-authenticated wallet mutations. No CORS permission is introduced. */
@Component
@Order(20)
public class WalletRequestSecurityFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        boolean wallet=request.getRequestURI().matches("/api/v1/(?:public/)?stores/[^/]+/wallet(?:/.*)?");
        boolean write=!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
        String site=request.getHeader("Sec-Fetch-Site");
        if(wallet && write && (!"1".equals(request.getHeader("X-RPB-Wallet")) || "cross-site".equals(site) || "same-site".equals(site))) {
            response.setStatus(403);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":\"WALLET_FORBIDDEN\",\"message\":\"请从钱包页面提交操作\"}");return;
        }
        if(wallet) {response.setHeader("Cache-Control","private, no-store");response.setHeader("Referrer-Policy","no-referrer");}
        chain.doFilter(request,response);
    }
}
