package com.sgmart.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class DeliveryPartnerDtos {

    private DeliveryPartnerDtos() {
    }

    /** A card in Admin > Delivery partners. currentOrders = orders they are delivering now. */
    public record PartnerView(Long id, String name, String mobile, VehicleType vehicleType, PartnerStatus status,
                              boolean active, int deliveriesToday, int deliveriesTotal, List<String> currentOrders) {
    }

    public record CreatePartnerRequest(
            @NotBlank(message = "Enter the partner's name.")
            @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters.")
            String name,
            @NotBlank(message = "Enter a valid 10-digit mobile number.")
            String mobile,
            @NotNull(message = "Choose Bike, Scooter or Bicycle.")
            VehicleType vehicleType) {
    }

    public record UpdatePartnerRequest(
            @NotBlank(message = "Enter the partner's name.")
            @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters.")
            String name,
            @NotBlank(message = "Enter a valid 10-digit mobile number.")
            String mobile,
            @NotNull(message = "Choose Bike, Scooter or Bicycle.")
            VehicleType vehicleType,
            @NotNull(message = "Choose active or inactive.")
            Boolean active) {
    }

    /** "Mark as available" / "Mark as off duty". ON_DELIVERY can't be set by hand. */
    public record StatusRequest(@NotNull(message = "Choose Available or Off duty.") PartnerStatus status) {
    }
}
