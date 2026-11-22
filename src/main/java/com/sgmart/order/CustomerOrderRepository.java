package com.sgmart.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByOrderNo(String orderNo);

    /**
     * Locks the order row until the transaction ends. A customer edit and a store accept
     * on the same order run one after the other, and the second one sees the new status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.orderNo = :orderNo")
    Optional<CustomerOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    List<CustomerOrder> findByTrackingTokenHashIn(Collection<String> hashes);

    List<CustomerOrder> findByCustomerIdOrderByPlacedAtDesc(Long customerId);

    /** Admin order queue: new orders first, then accepted, on the way, delivered, rejected, cancelled; newest first in each. */
    @Query(value = """
            SELECT o FROM CustomerOrder o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:fromTime IS NULL OR o.placedAt >= :fromTime)
              AND (:toTime IS NULL OR o.placedAt < :toTime)
            ORDER BY CASE o.status
                       WHEN com.sgmart.order.OrderStatus.PLACED THEN 0
                       WHEN com.sgmart.order.OrderStatus.ACCEPTED THEN 1
                       WHEN com.sgmart.order.OrderStatus.OUT_FOR_DELIVERY THEN 2
                       WHEN com.sgmart.order.OrderStatus.DELIVERED THEN 3
                       WHEN com.sgmart.order.OrderStatus.REJECTED THEN 4
                       ELSE 5 END,
                     o.placedAt DESC
            """,
            countQuery = """
            SELECT COUNT(o) FROM CustomerOrder o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:fromTime IS NULL OR o.placedAt >= :fromTime)
              AND (:toTime IS NULL OR o.placedAt < :toTime)
            """)
    Page<CustomerOrder> findForAdmin(@Param("status") OrderStatus status,
                                     @Param("fromTime") Instant fromTime,
                                     @Param("toTime") Instant toTime,
                                     Pageable pageable);
}
