package com.movinsync.shuttle.controller;

import com.movinsync.shuttle.dto.BookingRequest;
import com.movinsync.shuttle.dto.BookingResponse;
import com.movinsync.shuttle.entity.Booking;
import com.movinsync.shuttle.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Endpoints for booking segment seats, viewing, and cancelling reservations")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/trips/{tripId}/bookings")
    @Operation(summary = "Book a seat for a route segment (atomic allocation or auto-waitlist)")
    public ResponseEntity<BookingResponse> bookSeat(
            @PathVariable Long tripId,
            @Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.book(tripId, request);
        HttpStatus status = "CONFIRMED".equals(response.getStatus()) ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @DeleteMapping("/bookings/{bookingId}")
    @Operation(summary = "Cancel a confirmed booking (vacates seat segment & triggers auto-promotion)")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long bookingId) {
        BookingResponse response = bookingService.cancel(bookingId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/bookings/{bookingId}/no-show")
    @Operation(summary = "Mark a confirmed booking as NO_SHOW and trigger waitlist promotion")
    public ResponseEntity<BookingResponse> markNoShow(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.markNoShow(bookingId));
    }

    @GetMapping("/bookings/{bookingId}")
    @Operation(summary = "Get details of a specific booking")
    public ResponseEntity<Booking> getBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId));
    }

    @GetMapping("/my-bookings")
    @Operation(summary = "List the current user's confirmed bookings")
    public ResponseEntity<java.util.List<BookingResponse>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }
}
