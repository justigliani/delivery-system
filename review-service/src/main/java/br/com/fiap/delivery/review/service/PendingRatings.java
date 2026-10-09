package br.com.fiap.delivery.review.service;

public record PendingRatings(String dishName, long sum, int count) {

    public static PendingRatings of(String dishName, int rating) {
        return new PendingRatings(dishName, rating, 1);
    }

    public PendingRatings plus(PendingRatings other) {
        return new PendingRatings(other.dishName(), sum + other.sum(), count + other.count());
    }
}