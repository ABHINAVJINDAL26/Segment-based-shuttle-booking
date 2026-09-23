package com.movinsync.shuttle.controller;

import com.movinsync.shuttle.dto.AvailabilityResponse;
import com.movinsync.shuttle.dto.TripRequest;
import com.movinsync.shuttle.entity.Trip;
import com.movinsync.shuttle.service.AvailabilityService;
import com.movinsync.shuttle.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "Endpoints for managing trips and checking segment-based seat availability")
@SecurityRequirement(name = "bearerAuth")
public class TripController {

    private final TripService tripService;
    private final AvailabilityService availabilityService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Schedule a new trip with seat capacity (ADMIN only)")
    public ResponseEntity<Trip> createTrip(@Valid @RequestBody TripRequest request) {
        Trip trip = tripService.createTrip(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(trip);
    }

    @GetMapping
    @Operation(summary = "List all trips")
    public ResponseEntity<List<Trip>> getAllTrips() {
        return ResponseEntity.ok(tripService.getAllTrips());
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "Get trip details by trip ID")
    public ResponseEntity<Trip> getTrip(@PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.getTrip(tripId));
    }

    @PutMapping("/{tripId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update trip status: SCHEDULED, ACTIVE, COMPLETED, CANCELLED (ADMIN only)")
    public ResponseEntity<Trip> updateTripStatus(
            @PathVariable Long tripId,
            @RequestParam Trip.TripStatus status) {
        return ResponseEntity.ok(tripService.updateTripStatus(tripId, status));
    }

    @GetMapping("/{tripId}/availability")
    @Operation(summary = "Query available physical seats for a specific route segment (from -> to)")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @PathVariable Long tripId,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(availabilityService.getAvailability(tripId, from, to));
    }
}
