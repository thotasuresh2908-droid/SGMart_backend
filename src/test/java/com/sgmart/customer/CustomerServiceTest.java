package com.sgmart.customer;

import com.sgmart.common.ApiException;
import com.sgmart.customer.CustomerDtos.CustomerProfile;
import com.sgmart.customer.CustomerDtos.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerService service = new CustomerService(repository, java.time.Clock.systemUTC());
    private Customer me;

    @BeforeEach
    void setUp() {
        me = Customer.fromGoogle("google-1", "priya@gmail.com", "Priya");
        ReflectionTestUtils.setField(me, "id", 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(me));
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void guestOrdersMoveIntoTheGoogleAccount() {
        Customer guest = Customer.guest("Priya S", "9876543210");
        ReflectionTestUtils.setField(guest, "id", 9L);
        guest.markVerifiedByStore(1L, Instant.parse("2026-11-10T05:00:00Z"));
        guest.setDeliveredOrdersCount(2);
        when(repository.findByMobile("9876543210")).thenReturn(Optional.of(guest));

        CustomerProfile profile = service.updateProfile(5L, new UpdateProfileRequest("Priya Sharma", "98765 43210"));

        verify(repository).moveOrders(9L, 5L);
        verify(repository).moveAddresses(9L, 5L);
        verify(repository).delete(guest);
        assertThat(profile.name()).isEqualTo("Priya Sharma");
        assertThat(profile.mobile()).isEqualTo("9876543210");
        assertThat(profile.mobileVerified()).isTrue();
        assertThat(profile.deliveredOrders()).isEqualTo(2);
    }

    @Test
    void mobileOfAnotherGoogleAccountIsRefused() {
        Customer other = Customer.fromGoogle("google-2", "someone@gmail.com", "Someone");
        ReflectionTestUtils.setField(other, "id", 7L);
        other.setMobile("9876543210");
        when(repository.findByMobile("9876543210")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateProfile(5L, new UpdateProfileRequest("Priya", "9876543210")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("MOBILE_IN_USE");
        verify(repository, never()).moveOrders(anyLong(), anyLong());
    }

    @Test
    void newMobileStartsUnverified() {
        me.markVerifiedByStore(1L, Instant.now());
        me.setMobile("9000000001");
        when(repository.findByMobile("9000000002")).thenReturn(Optional.empty());

        CustomerProfile profile = service.updateProfile(5L, new UpdateProfileRequest("Priya", "9000000002"));

        assertThat(profile.mobile()).isEqualTo("9000000002");
        assertThat(profile.mobileVerified()).isFalse();
    }

    @Test
    void emptyMobileKeepsTheCurrentOne() {
        me.setMobile("9000000001");
        CustomerProfile profile = service.updateProfile(5L, new UpdateProfileRequest("Priya", ""));
        assertThat(profile.mobile()).isEqualTo("9000000001");
    }

    @Test
    void returningGoogleShopperIsFoundNotDuplicated() {
        when(repository.findByGoogleSub("google-1")).thenReturn(Optional.of(me));
        Customer found = service.findOrCreateFromGoogle("google-1", "new.email@gmail.com", "Priya");
        assertThat(found).isSameAs(me);
        assertThat(found.getEmail()).isEqualTo("new.email@gmail.com");
        verify(repository, never()).save(any(Customer.class));
    }
}
