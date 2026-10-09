package br.com.fiap.delivery.order.repository;

import br.com.fiap.delivery.order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
}