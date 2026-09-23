package com.movinsync.shuttle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityResponse {
    private Long tripId;
    private String from;
    private String to;
    private List<Integer> availableSeats;
    private int availableCount;
}
