//package org.spacehub.configuration;
//
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.spacehub.service.RateLimitService;
//import org.springframework.lang.NonNull;
//import org.springframework.stereotype.Component;
//import org.springframework.web.servlet.HandlerInterceptor;
//
//@Component
//public class RateLimitInterceptor implements HandlerInterceptor {
//
//  private final RateLimitService rateLimitService;
//
//  public RateLimitInterceptor(RateLimitService rateLimitService) {
//    this.rateLimitService = rateLimitService;
//  }
//
//  @Override
//  public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
//                           @NonNull Object handler)
//    throws Exception {
//
//    String clientKey = getClientKey(request);
//    boolean allowed = rateLimitService.tryConsume(clientKey);
//
//    if (!allowed) {
//      response.setStatus(429);
//      response.getWriter().write("Rate limit exceeded. Try again later.");
//      return false;
//    }
//
//    return true;
//  }
//
//  private String getClientKey(HttpServletRequest request) {
//    return request.getRemoteAddr();
//  }
//}
//
