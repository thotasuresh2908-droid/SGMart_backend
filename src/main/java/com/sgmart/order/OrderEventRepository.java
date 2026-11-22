package com.sgmart.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrderIdInOrderByCreatedAtAscIdAsc(Collection<Long> orderIds);
}
