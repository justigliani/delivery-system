package br.com.fiap.delivery.order.controller;

import br.com.fiap.delivery.order.dto.ReviewRequest;
import br.com.fiap.delivery.order.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody ReviewRequest request) {
        reviewService.publish(request);
        return ResponseEntity.accepted().build();
    }
}