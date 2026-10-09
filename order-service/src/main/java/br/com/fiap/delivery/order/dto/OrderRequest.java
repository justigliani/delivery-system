package br.com.fiap.delivery.order.dto;

public record OrderRequest(Long dishId, Integer quantity) {
}