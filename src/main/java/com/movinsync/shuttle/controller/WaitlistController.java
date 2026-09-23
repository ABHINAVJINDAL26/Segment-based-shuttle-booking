package com.movinsync.shuttle.controller;

import com.movinsync.shuttle.dto.WaitlistResponse;
import com.movinsync.shuttle.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Waitlist", description = "Endpoints for checking and managing waitlist status")
@SecurityRequirement(name = "bearerAuth")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @GetMapping("/waitlist")
    @Operation(summary = "Get all waitlist entries for current logged-in user")
    public ResponseEntity<List<WaitlistResponse>> getMyWaitlist() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(waitlistService.getUserWaitlist(email));
    }

    @GetMapping("/trips/{tripId}/waitlist")
    @Operation(summary = "Get all active waitlist entries for a specific trip ordered by FIFO position")
    public ResponseEntity<List<WaitlistResponse>> getTripWaitlist(@PathVariable Long tripId) {
        return ResponseEntity.ok(waitlistService.getTripWaitlist(tripId));
    }

    @DeleteMapping("/waitlist/{waitlistId}")
    @Operation(summary = "Cancel a waitlist position")
    public ResponseEntity<Void> cancelWaitlist(@PathVariable Long waitlistId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        waitlistService.cancelWaitlist(waitlistId, email);
        return ResponseEntity.noContent().build();
    }
}
