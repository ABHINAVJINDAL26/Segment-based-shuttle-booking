package com.movinsync.shuttle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RouteRequest {

    @NotBlank(message = "Route name is required")
    @Size(min = 2, max = 100)
    private String name;

    private String description;
}
