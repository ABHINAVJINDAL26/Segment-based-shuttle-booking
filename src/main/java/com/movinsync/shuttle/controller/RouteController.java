package com.movinsync.shuttle.controller;

import com.movinsync.shuttle.dto.RouteRequest;
import com.movinsync.shuttle.dto.StopRequest;
import com.movinsync.shuttle.entity.Route;
import com.movinsync.shuttle.entity.Stop;
import com.movinsync.shuttle.service.RouteService;
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
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Tag(name = "Routes", description = "Endpoints for shuttle routes and stops management")
@SecurityRequirement(name = "bearerAuth")
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new shuttle route (ADMIN only)")
    public ResponseEntity<Route> createRoute(@Valid @RequestBody RouteRequest request) {
        Route route = routeService.createRoute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(route);
    }

    @PostMapping("/{routeId}/stops")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a stop to a route with sequence number and arrival time (ADMIN only)")
    public ResponseEntity<Stop> addStop(
            @PathVariable Long routeId,
            @Valid @RequestBody StopRequest request) {
        Stop stop = routeService.addStop(routeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(stop);
    }

    @GetMapping
    @Operation(summary = "List all shuttle routes")
    public ResponseEntity<List<Route>> getAllRoutes() {
        return ResponseEntity.ok(routeService.getAllRoutes());
    }

    @GetMapping("/{routeId}")
    @Operation(summary = "Get route details by route ID")
    public ResponseEntity<Route> getRoute(@PathVariable Long routeId) {
        return ResponseEntity.ok(routeService.getRoute(routeId));
    }

    @GetMapping("/{routeId}/stops")
    @Operation(summary = "Get all stops for a route in sequence order")
    public ResponseEntity<List<Stop>> getStops(@PathVariable Long routeId) {
        return ResponseEntity.ok(routeService.getStops(routeId));
    }
}
