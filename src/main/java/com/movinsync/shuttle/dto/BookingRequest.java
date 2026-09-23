package com.movinsync.shuttle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BookingRequest {

    @NotBlank(message = "fromStop is required")
    private String fromStop;

    @NotBlank(message = "toStop is required")
    private String toStop;
}
