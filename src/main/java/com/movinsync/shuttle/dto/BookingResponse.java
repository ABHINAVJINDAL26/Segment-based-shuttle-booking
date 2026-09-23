package com.movinsync.shuttle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private Long tripId;
    private Integer seatNumber;
    private String fromStop;
    private String toStop;
    private String status;
    private LocalDateTime createdAt;
    private String message;
}
