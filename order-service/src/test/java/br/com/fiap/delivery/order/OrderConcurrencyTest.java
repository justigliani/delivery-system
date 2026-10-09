package br.com.fiap.delivery.order;

import br.com.fiap.delivery.order.client.PaymentClient;
import br.com.fiap.delivery.order.client.PaymentResponse;
import br.com.fiap.delivery.order.dto.OrderRequest;
import br.com.fiap.delivery.order.entity.Dish;
import br.com.fiap.delivery.order.exception.OutOfStockException;
import br.com.fiap.delivery.order.exception.PaymentProcessingException;
import br.com.fiap.delivery.order.repository.CustomerOrderRepository;
import br.com.fiap.delivery.order.repository.DishRepository;
import br.com.fiap.delivery.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "eureka.client.enabled=false")
class OrderConcurrencyTest {

    private static final long PROMO_DISH_ID = 1L;
    private static final int PROMO_STOCK = 10;
    private static final int REQUESTS = 50;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @MockitoBean
    private PaymentClient paymentClient;

    @BeforeEach
    void resetPromoDish() {
        orderRepository.deleteAll();
        Dish dish = dishRepository.findById(PROMO_DISH_ID).orElseThrow();
        dish.setStock(PROMO_STOCK);
        dishRepository.save(dish);
    }

    @Test
    void fiftySimultaneousOrdersConfirmExactlyTen() throws Exception {
        when(paymentClient.pay(any())).thenReturn(new PaymentResponse("APPROVED", 8081));

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < REQUESTS; i++) {
            results.add(pool.submit(() -> {
                startSignal.await();
                try {
                    orderService.createOrder(new OrderRequest(PROMO_DISH_ID, 1));
                    return true;
                } catch (OutOfStockException e) {
                    return false;
                }
            }));
        }

        startSignal.countDown();

        int confirmed = 0;
        int rejected = 0;
        for (Future<Boolean> result : results) {
            if (result.get(60, TimeUnit.SECONDS)) {
                confirmed++;
            } else {
                rejected++;
            }
        }
        pool.shutdown();

        assertEquals(PROMO_STOCK, confirmed);
        assertEquals(REQUESTS - PROMO_STOCK, rejected);
        assertEquals(0, dishRepository.findById(PROMO_DISH_ID).orElseThrow().getStock());
        assertEquals(PROMO_STOCK, orderRepository.count());
    }

    @Test
    void failedPaymentKeepsStockIntact() {
        when(paymentClient.pay(any()))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(PaymentProcessingException.class,
                () -> orderService.createOrder(new OrderRequest(PROMO_DISH_ID, 1)));

        assertEquals(PROMO_STOCK, dishRepository.findById(PROMO_DISH_ID).orElseThrow().getStock());
        assertEquals(0, orderRepository.count());
    }
}