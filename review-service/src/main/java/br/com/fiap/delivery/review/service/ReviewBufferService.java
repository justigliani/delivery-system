package br.com.fiap.delivery.review.service;

import br.com.fiap.delivery.review.config.RabbitConfig;
import br.com.fiap.delivery.review.dto.ReviewMessage;
import br.com.fiap.delivery.review.entity.ReviewSummary;
import br.com.fiap.delivery.review.repository.ReviewSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReviewBufferService {

    private static final Logger log = LoggerFactory.getLogger(ReviewBufferService.class);

    private final ConcurrentHashMap<Long, PendingRatings> buffer = new ConcurrentHashMap<>();
    private final ReviewSummaryRepository repository;

    public ReviewBufferService(ReviewSummaryRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void receive(ReviewMessage message) {
        if (message.dishId() == null || message.rating() < 1 || message.rating() > 5) {
            log.warn("Discarding invalid review: {}", message);
            return;
        }

        buffer.merge(message.dishId(),
                PendingRatings.of(message.dishName(), message.rating()),
                PendingRatings::plus);

        log.info("Buffered review for dish {} with rating {}", message.dishId(), message.rating());
    }

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        int dishes = 0;
        int reviews = 0;

        for (Long dishId : buffer.keySet()) {
            PendingRatings pending = buffer.remove(dishId);
            if (pending == null) {
                continue;
            }

            ReviewSummary summary = repository.findById(dishId)
                    .orElseGet(() -> new ReviewSummary(dishId, pending.dishName()));
            summary.addRatings(pending.dishName(), pending.sum(), pending.count());
            repository.save(summary);

            dishes++;
            reviews += pending.count();
        }

        log.info("Flushed {} reviews for {} dishes to the database", reviews, dishes);
    }
}