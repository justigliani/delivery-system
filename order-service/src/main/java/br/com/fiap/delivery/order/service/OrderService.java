package br.com.fiap.delivery.order.service;

import br.com.fiap.delivery.order.client.PaymentClient;
import br.com.fiap.delivery.order.dto.OrderRequest;
import br.com.fiap.delivery.order.entity.CustomerOrder;
import br.com.fiap.delivery.order.entity.Dish;
import br.com.fiap.delivery.order.exception.NotFoundException;
import br.com.fiap.delivery.order.exception.OutOfStockException;
import br.com.fiap.delivery.order.exception.PaymentProcessingException;
import br.com.fiap.delivery.order.exception.ValidationException;
import br.com.fiap.delivery.order.repository.CustomerOrderRepository;
import br.com.fiap.delivery.order.repository.DishRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final DishRepository dishRepository;
    private final CustomerOrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public OrderService(DishRepository dishRepository,
                        CustomerOrderRepository orderRepository,
                        PaymentClient paymentClient) {
        this.dishRepository = dishRepository;
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
    }

    public CustomerOrder findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    @Transactional
    public CustomerOrder createOrder(OrderRequest request) {
        if (request.quantity() == null || request.quantity() < 1) {
            throw new ValidationException("Quantity must be at least 1");
        }
        if (request.dishId() == null) {
            throw new NotFoundException("Dish not found");
        }

        Dish dish = dishRepository.findByIdForUpdate(request.dishId())
                .orElseThrow(() -> new NotFoundException("Dish not found"));

        if (dish.getStock() < request.quantity()) {
            throw new OutOfStockException("Dish out of stock");
        }

        BigDecimal totalPrice = dish.getPrice().multiply(BigDecimal.valueOf(request.quantity()));

        try {
            paymentClient.pay(totalPrice);
        } catch (Exception e) {
            log.error("Payment failed after all retries for dish {}: {}", dish.getId(), e.getMessage());
            throw new PaymentProcessingException("Payment service unavailable");
        }

        dish.setStock(dish.getStock() - request.quantity());

        CustomerOrder order = new CustomerOrder(
                dish.getId(), request.quantity(), totalPrice, "CONFIRMED", LocalDateTime.now());
        return orderRepository.save(order);
    }
}