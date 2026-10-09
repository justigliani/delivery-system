package br.com.fiap.delivery.order.loader;

import br.com.fiap.delivery.order.entity.Dish;
import br.com.fiap.delivery.order.repository.DishRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private final DishRepository dishRepository;

    public DataLoader(DishRepository dishRepository) {
        this.dishRepository = dishRepository;
    }

    @Override
    public void run(String... args) {
        if (dishRepository.count() > 0) {
            return;
        }
        dishRepository.saveAll(List.of(
                new Dish("House Burger", "Brioche bun, beef patty and cheddar", new BigDecimal("39.90"), 10),
                new Dish("Pizza Margherita", "Tomato sauce, mozzarella and basil", new BigDecimal("45.00"), 50),
                new Dish("Veggie Bowl", "Rice, chickpeas, roasted vegetables and tahini", new BigDecimal("32.00"), 50),
                new Dish("Pasta Carbonara", "Spaghetti, bacon, egg and pecorino", new BigDecimal("38.50"), 50),
                new Dish("Chocolate Brownie", "Warm brownie with vanilla ice cream", new BigDecimal("18.00"), 50)
        ));
    }
}