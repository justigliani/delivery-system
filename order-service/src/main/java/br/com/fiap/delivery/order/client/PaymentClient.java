package br.com.fiap.delivery.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private static final String PAYMENT_URL = "http://PAYMENT-SERVICE/payments";

    private final RestTemplate restTemplate;

    public PaymentClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Retryable(
            includes = RestClientException.class,
            maxRetries = 3,
            delay = 500,
            multiplier = 2.0,
            jitter = 100,
            maxDelay = 3000
    )
    public PaymentResponse pay(BigDecimal amount) {
        log.info("Requesting payment of {}", amount);
        try {
            PaymentResponse response = restTemplate.postForObject(
                    PAYMENT_URL, new PaymentRequest(amount), PaymentResponse.class);
            log.info("Payment approved by instance {}", response.instance());
            return response;
        } catch (RestClientException e) {
            log.warn("Payment attempt failed: {}", e.getMessage());
            throw e;
        }
    }
}