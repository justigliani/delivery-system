package br.com.fiap.delivery.payment.dto;

import java.math.BigDecimal;

public record PaymentRequest(BigDecimal amount) {
}
