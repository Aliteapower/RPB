package com.rpb.reservation.customerauth.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(20)
public class CustomerPasswordRequestSecurityFilter extends OncePerRequestFilter {
    private static final String BASE="/api/v1/public/customer-auth/";
    private static final Set<String> PATHS=Set.of("activate","password-login","password-change","recovery-email-code","recovery-email-confirm","password-reset-code","password-reset");
    protected void doFilterInternal(HttpServletRequest r,HttpServletResponse p,FilterChain chain)throws ServletException,IOException {
        String path=r.getRequestURI();
        if(path.startsWith(BASE)&&PATHS.contains(path.substring(BASE.length()))||path.matches("/api/v1/stores/[^/]+/tenant-admin/customers/[^/]+/activation-codes")){
            p.setHeader("Cache-Control","private, no-store");p.setHeader("Referrer-Policy","no-referrer");
            String site=r.getHeader("Sec-Fetch-Site");
            if(!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod())&&(!"1".equals(r.getHeader("X-RPB-Customer-Auth"))||"cross-site".equals(site)||"same-site".equals(site))){
                p.setStatus(403);p.setContentType("application/json;charset=UTF-8");p.getWriter().write("{\"success\":false,\"error\":\"request_invalid\"}");return;
            }
        }
        chain.doFilter(r,p);
    }
}
