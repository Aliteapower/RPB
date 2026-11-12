package com.rpb.reservation.winestorage.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class StorageRequestSecurityFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
  if(request.getRequestURI().matches("/api/v1/(public/)?stores/[^/]+/wine-storage(?:/.*)?")){
   response.setHeader("Cache-Control","private, no-store");response.setHeader("Referrer-Policy","no-referrer");
   String site=request.getHeader("Sec-Fetch-Site");
   if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())&&(!"1".equals(request.getHeader("X-RPB-Storage"))||"cross-site".equals(site)||"same-site".equals(site))){
    response.setStatus(403);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":\"STORAGE_FORBIDDEN\",\"message\":\"请从存酒卡页面操作\"}");return;
   }
  }
  chain.doFilter(request,response);
 }
}
