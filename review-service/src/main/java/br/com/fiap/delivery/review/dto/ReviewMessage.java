package br.com.fiap.delivery.review.dto;

public record ReviewMessage(Long dishId, String dishName, int rating, String comment) {
}