package com.sgmart.delivery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long> {

    Optional<DeliveryPartner> findByMobile(String mobile);

    List<DeliveryPartner> findAllByOrderByActiveDescNameAsc();

    /** Locks the partner row so two orders can't be given to the same available partner at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DeliveryPartner d WHERE d.id = :id")
    Optional<DeliveryPartner> findByIdForUpdate(@Param("id") Long id);
}
