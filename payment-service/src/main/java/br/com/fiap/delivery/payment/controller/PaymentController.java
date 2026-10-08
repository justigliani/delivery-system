package br.com.fiap.delivery.payment.controller;

import br.com.fiap.delivery.payment.dto.PaymentRequest;
import br.com.fiap.delivery.payment.dto.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final Random random = new Random();
    private final int port;

    public PaymentController(@Value("${server.port}") int port) {
        this.port = port;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        log.info("Payment request for amount {} handled by instance on port {}", request.amount(), port);

        if (random.nextBoolean()) {
            log.warn("Simulated failure on port {}", port);
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(new PaymentResponse("APPROVED", port));
    }
}
