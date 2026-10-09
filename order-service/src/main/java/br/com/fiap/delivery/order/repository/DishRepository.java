package br.com.fiap.delivery.order.repository;

import br.com.fiap.delivery.order.entity.Dish;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DishRepository extends JpaRepository<Dish, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Dish d WHERE d.id = :id")
    Optional<Dish> findByIdForUpdate(@Param("id") Long id);
}