package br.com.fiap.delivery.order.dto;

public record ReviewMessage(Long dishId, String dishName, int rating, String comment) {
}