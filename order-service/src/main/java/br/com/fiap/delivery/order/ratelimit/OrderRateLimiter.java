package br.com.fiap.delivery.order.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrderRateLimiter {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final int requestsPerSecond;
    private double availableTokens;
    private long lastRefillNanos;

    public OrderRateLimiter(@Value("${orders.rate-limit.per-second:20}") int requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
        this.availableTokens = requestsPerSecond;
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (availableTokens >= 1) {
            availableTokens -= 1;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / NANOS_PER_SECOND;
        availableTokens = Math.min(requestsPerSecond, availableTokens + elapsedSeconds * requestsPerSecond);
        lastRefillNanos = now;
    }
}