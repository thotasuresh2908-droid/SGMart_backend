package com.sgmart.delivery;

import com.sgmart.common.ApiException;
import com.sgmart.delivery.DeliveryPartnerDtos.CreatePartnerRequest;
import com.sgmart.delivery.DeliveryPartnerDtos.PartnerView;
import com.sgmart.delivery.DeliveryPartnerDtos.UpdatePartnerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryPartnerServiceTest {

    private final DeliveryPartnerRepository repository = mock(DeliveryPartnerRepository.class);
    private final DeliveryPartnerService service = new DeliveryPartnerService(repository,
            mock(NamedParameterJdbcTemplate.class), Clock.systemUTC());

    private DeliveryPartner existing(long id, String name, String mobile, PartnerStatus status) {
        DeliveryPartner p = new DeliveryPartner(name, mobile, VehicleType.BIKE);
        p.setStatus(status);
        ReflectionTestUtils.setField(p, "id", id);
        when(repository.findById(id)).thenReturn(Optional.of(p));
        when(repository.findByMobile(mobile)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void newPartnerIsAvailableWithACleanMobile() {
        when(repository.findByMobile("9000011111")).thenReturn(Optional.empty());
        when(repository.save(any(DeliveryPartner.class))).thenAnswer(inv -> inv.getArgument(0));
        PartnerView p = service.create(new CreatePartnerRequest(" Kiran Rao ", "+91 90000 11111", VehicleType.SCOOTER));
        assertThat(p.name()).isEqualTo("Kiran Rao");
        assertThat(p.mobile()).isEqualTo("9000011111");
        assertThat(p.status()).isEqualTo(PartnerStatus.AVAILABLE);
    }

    @Test
    void sameMobileTwiceIsRefused() {
        existing(1, "Ravi Kumar", "9848012345", PartnerStatus.AVAILABLE);
        assertThatThrownBy(() -> service.create(new CreatePartnerRequest("Someone", "9848012345", VehicleType.BIKE)))
                .isInstanceOf(ApiException.class).hasMessage("This mobile number belongs to Ravi Kumar.");
    }

    @Test
    void partnerOnADeliveryCantGoOffDutyOrBeDeactivated() {
        existing(2, "Salma Begum", "9949023456", PartnerStatus.ON_DELIVERY);
        assertThatThrownBy(() -> service.setStatus(2L, PartnerStatus.OFF_DUTY))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PARTNER_BUSY");
        assertThatThrownBy(() -> service.update(2L,
                new UpdatePartnerRequest("Salma Begum", "9949023456", VehicleType.SCOOTER, false)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PARTNER_BUSY");
    }

    @Test
    void onDeliveryCantBeSetByHand() {
        existing(1, "Ravi Kumar", "9848012345", PartnerStatus.AVAILABLE);
        assertThatThrownBy(() -> service.setStatus(1L, PartnerStatus.ON_DELIVERY))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_STATUS");
    }

    @Test
    void offDutyPartnerCanBeMarkedAvailable() {
        DeliveryPartner arjun = existing(3, "Arjun Reddy", "9000034567", PartnerStatus.OFF_DUTY);
        service.setStatus(3L, PartnerStatus.AVAILABLE);
        assertThat(arjun.getStatus()).isEqualTo(PartnerStatus.AVAILABLE);
    }
}
