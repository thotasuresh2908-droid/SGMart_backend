package com.sgmart.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByGoogleSub(String googleSub);

    Optional<Customer> findByMobile(String mobile);

    List<Customer> findAllByBlockedTrueOrderByBlockedAtDesc();

    /** Used when a Google shopper claims a mobile number that placed guest orders. */
    @Modifying
    @Query(value = "UPDATE orders SET customer_id = :toId WHERE customer_id = :fromId", nativeQuery = true)
    int moveOrders(@Param("fromId") Long fromId, @Param("toId") Long toId);

    @Modifying
    @Query(value = "UPDATE customer_addresses SET customer_id = :toId WHERE customer_id = :fromId", nativeQuery = true)
    int moveAddresses(@Param("fromId") Long fromId, @Param("toId") Long toId);

    @Modifying
    @Query(value = "DELETE FROM cart_items WHERE customer_id = :customerId", nativeQuery = true)
    int deleteCart(@Param("customerId") Long customerId);
}
