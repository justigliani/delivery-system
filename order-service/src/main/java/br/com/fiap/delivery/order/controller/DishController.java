package br.com.fiap.delivery.order.controller;

import br.com.fiap.delivery.order.entity.Dish;
import br.com.fiap.delivery.order.exception.NotFoundException;
import br.com.fiap.delivery.order.repository.DishRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dishes")
public class DishController {

    private final DishRepository dishRepository;

    public DishController(DishRepository dishRepository) {
        this.dishRepository = dishRepository;
    }

    @GetMapping
    public List<Dish> findAll() {
        return dishRepository.findAll();
    }

    @GetMapping("/{id}")
    public Dish findById(@PathVariable Long id) {
        return dishRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dish not found"));
    }
}