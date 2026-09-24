package com.movinsync.shuttle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VehicleRequest {

    @NotBlank
    @Size(max = 30)
    private String registrationNumber;

    @Min(1)
    private Integer capacity;
}
