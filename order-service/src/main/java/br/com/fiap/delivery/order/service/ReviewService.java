package br.com.fiap.delivery.order.service;

import br.com.fiap.delivery.order.config.RabbitConfig;
import br.com.fiap.delivery.order.dto.ReviewMessage;
import br.com.fiap.delivery.order.dto.ReviewRequest;
import br.com.fiap.delivery.order.entity.Dish;
import br.com.fiap.delivery.order.exception.NotFoundException;
import br.com.fiap.delivery.order.exception.ValidationException;
import br.com.fiap.delivery.order.repository.DishRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final DishRepository dishRepository;
    private final RabbitTemplate rabbitTemplate;

    public ReviewService(DishRepository dishRepository, RabbitTemplate rabbitTemplate) {
        this.dishRepository = dishRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ReviewRequest request) {
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new ValidationException("Rating must be between 1 and 5");
        }
        if (request.dishId() == null) {
            throw new NotFoundException("Dish not found");
        }

        Dish dish = dishRepository.findById(request.dishId())
                .orElseThrow(() -> new NotFoundException("Dish not found"));

        ReviewMessage message = new ReviewMessage(
                dish.getId(), dish.getName(), request.rating(), request.comment());

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        log.info("Review published for dish {} with rating {}", dish.getId(), request.rating());
    }
}