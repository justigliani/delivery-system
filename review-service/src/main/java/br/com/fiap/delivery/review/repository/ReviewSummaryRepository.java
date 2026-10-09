package br.com.fiap.delivery.review.repository;

import br.com.fiap.delivery.review.entity.ReviewSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewSummaryRepository extends JpaRepository<ReviewSummary, Long> {

    List<ReviewSummary> findAllByOrderByAverageDescRatingCountDesc();
}