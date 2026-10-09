package br.com.fiap.delivery.review.controller;

import br.com.fiap.delivery.review.dto.RankingResponse;
import br.com.fiap.delivery.review.repository.ReviewSummaryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reviews")
public class RankingController {

    private final ReviewSummaryRepository repository;

    public RankingController(ReviewSummaryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/ranking")
    public List<RankingResponse> ranking() {
        return repository.findAllByOrderByAverageDescRatingCountDesc().stream()
                .map(summary -> new RankingResponse(
                        summary.getDishId(),
                        summary.getDishName(),
                        Math.round(summary.getAverage() * 10) / 10.0,
                        summary.getRatingCount()))
                .toList();
    }
}