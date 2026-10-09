package br.com.fiap.delivery.order.client;

import java.math.BigDecimal;

public record PaymentRequest(BigDecimal amount) {
}