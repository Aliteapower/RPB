package com.rpb.reservation.catalog.api;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class CatalogRequestSecurityFilter extends OncePerRequestFilter {
 protected void doFilterInternal(HttpServletRequest r,HttpServletResponse p,FilterChain c)throws IOException,ServletException{
  boolean catalog=r.getRequestURI().matches("/api/v1/stores/[^/]+/catalog(?:/.*)?");
  if(catalog){p.setHeader("Cache-Control","private, no-store");p.setHeader("Referrer-Policy","no-referrer");String site=r.getHeader("Sec-Fetch-Site");if(!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod())&&(!"1".equals(r.getHeader("X-RPB-Catalog"))||"cross-site".equals(site)||"same-site".equals(site))){p.setStatus(403);p.setContentType("application/json;charset=UTF-8");p.getWriter().write("{\"code\":\"CATALOG_FORBIDDEN\",\"message\":\"请从产品管理页面操作\"}");return;}}
  c.doFilter(r,p);
 }
}
