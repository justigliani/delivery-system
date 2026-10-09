package br.com.fiap.delivery.review.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "review_summaries")
public class ReviewSummary {

    @Id
    private Long dishId;

    private String dishName;
    private long ratingSum;
    private int ratingCount;
    private double average;

    protected ReviewSummary() {
    }

    public ReviewSummary(Long dishId, String dishName) {
        this.dishId = dishId;
        this.dishName = dishName;
    }

    public void addRatings(String dishName, long sum, int count) {
        this.dishName = dishName;
        this.ratingSum += sum;
        this.ratingCount += count;
        this.average = (double) ratingSum / ratingCount;
    }

    public Long getDishId() { return dishId; }
    public String getDishName() { return dishName; }
    public long getRatingSum() { return ratingSum; }
    public int getRatingCount() { return ratingCount; }
    public double getAverage() { return average; }
}