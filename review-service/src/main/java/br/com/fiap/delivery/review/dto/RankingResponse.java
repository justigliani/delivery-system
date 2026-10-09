package br.com.fiap.delivery.review.dto;

public record RankingResponse(Long dishId, String dishName, double average, int count) {
}