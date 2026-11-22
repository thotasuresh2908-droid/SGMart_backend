package com.sgmart.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** What shoppers see: active, not removed, in stock, in an active category. */
    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true AND p.deletedAt IS NULL AND p.stockQty > 0
              AND p.category.active = true
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:q IS NULL OR LOWER(p.name) LIKE :q)
            """)
    Page<Product> findForShop(@Param("categoryId") Long categoryId, @Param("q") String q, Pageable pageable);

    /** Any product that hasn't been removed (admin views). */
    @EntityGraph(attributePaths = "category")
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findLive(@Param("id") Long id);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByDeletedAtIsNull();

    /**
     * Locks the product row (SELECT ... FOR UPDATE) until the transaction ends,
     * so two orders can never sell the same last unit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
