package com.sgmart.customer;

import com.sgmart.common.ApiException;
import com.sgmart.common.PhoneNumbers;
import com.sgmart.customer.CustomerDtos.CustomerAdminView;
import com.sgmart.customer.CustomerDtos.CustomerProfile;
import com.sgmart.customer.CustomerDtos.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final Clock clock;

    public CustomerService(CustomerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Called on every "Continue with Google". Finds the shopper by Google id or creates one. */
    @Transactional
    public Customer findOrCreateFromGoogle(String googleSub, String email, String displayName) {
        return repository.findByGoogleSub(googleSub)
                .map(existing -> {
                    existing.setEmail(email);
                    return existing;
                })
                .orElseGet(() -> repository.save(Customer.fromGoogle(googleSub, email, displayName)));
    }

    /** Guest checkout: the customer is matched by mobile number, or created on the first order. */
    @Transactional
    public Customer findOrCreateGuest(String name, String mobile) {
        return repository.findByMobile(mobile).orElseGet(() -> repository.save(Customer.guest(name, mobile)));
    }

    @Transactional(readOnly = true)
    public Customer get(Long customerId) {
        return load(customerId);
    }

    @Transactional(readOnly = true)
    public CustomerProfile profile(Long customerId) {
        return CustomerProfile.of(load(customerId));
    }

    @Transactional
    public CustomerProfile updateProfile(Long customerId, UpdateProfileRequest request) {
        Customer me = load(customerId);
        me.setName(request.name().trim());
        if (request.mobile() != null && !request.mobile().isBlank()) {
            attachMobile(me, PhoneNumbers.normalize(request.mobile(), "mobile"));
        }
        return CustomerProfile.of(repository.save(me));
    }

    /**
     * Gives a signed-in shopper this mobile number. If the number already placed guest orders,
     * those orders, addresses, verification and delivered count move into this account.
     * A number linked to another Google account can't be taken.
     */
    @Transactional
    public Customer attachMobile(Long customerId, String normalizedMobile) {
        Customer me = load(customerId);
        attachMobile(me, normalizedMobile);
        return repository.save(me);
    }

    private void attachMobile(Customer me, String mobile) {
        if (mobile.equals(me.getMobile())) {
            return;
        }
        Optional<Customer> owner = repository.findByMobile(mobile);
        if (owner.isPresent()) {
            mergeGuestInto(owner.get(), me);
        } else {
            me.clearVerification();
        }
        me.setMobile(mobile);
    }

    private void mergeGuestInto(Customer guest, Customer me) {
        if (guest.getGoogleSub() != null) {
            throw ApiException.conflict("MOBILE_IN_USE",
                    "This mobile number is linked to another Google account. Call the store if it's your number.");
        }
        repository.moveOrders(guest.getId(), me.getId());
        repository.moveAddresses(guest.getId(), me.getId());
        repository.deleteCart(guest.getId());
        me.absorb(guest);
        repository.delete(guest);
        repository.flush(); // free the unique mobile before this account takes it
    }

    /** After a delivery: one more delivered order, so the number is no longer a New customer. */
    @Transactional
    public void recordDelivery(Long customerId) {
        Customer c = load(customerId);
        c.setDeliveredOrdersCount(c.getDeliveredOrdersCount() + 1);
    }

    // ------------------------------------------------------------------ admin actions

    /** "Mark verified" after the store called the number. Removes the New customer badge. */
    @Transactional
    public CustomerAdminView markVerified(Long customerId, Long adminId) {
        Customer c = load(customerId);
        c.markVerifiedByStore(adminId, Instant.now(clock));
        return CustomerAdminView.of(c);
    }

    @Transactional
    public CustomerAdminView block(Long customerId, String reason) {
        Customer c = load(customerId);
        c.block(reason.trim(), Instant.now(clock));
        return CustomerAdminView.of(c);
    }

    @Transactional
    public CustomerAdminView unblock(Long customerId) {
        Customer c = load(customerId);
        c.unblock();
        return CustomerAdminView.of(c);
    }

    @Transactional(readOnly = true)
    public CustomerAdminView adminView(Long customerId) {
        return CustomerAdminView.of(load(customerId));
    }

    @Transactional(readOnly = true)
    public List<CustomerAdminView> blocked() {
        return repository.findAllByBlockedTrueOrderByBlockedAtDesc().stream().map(CustomerAdminView::of).toList();
    }

    private Customer load(Long customerId) {
        return repository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer not found."));
    }
}
