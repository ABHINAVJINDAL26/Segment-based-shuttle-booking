package com.movinsync.shuttle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class StopRequest {

    @NotBlank(message = "Stop name is required")
    private String name;

    @NotNull(message = "Sequence number is required")
    @Min(value = 1, message = "Sequence must be >= 1")
    private Integer sequenceNum;

    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$",
             message = "Arrival time must be in HH:mm format")
    private String arrivalTime;
}
