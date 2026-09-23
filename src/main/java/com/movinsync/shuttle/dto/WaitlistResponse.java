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
public class WaitlistResponse {
    private Long waitlistId;
    private Long tripId;
    private String fromStop;
    private String toStop;
    private Integer position;
    private String status;
    private LocalDateTime createdAt;
}
