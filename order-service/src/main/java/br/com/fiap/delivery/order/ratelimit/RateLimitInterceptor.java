package br.com.fiap.delivery.order.ratelimit;

import br.com.fiap.delivery.order.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final OrderRateLimiter rateLimiter;

    public RateLimitInterceptor(OrderRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("POST".equalsIgnoreCase(request.getMethod()) && !rateLimiter.tryAcquire()) {
            throw new TooManyRequestsException("Too many requests, please try again in a moment");
        }
        return true;
    }
}