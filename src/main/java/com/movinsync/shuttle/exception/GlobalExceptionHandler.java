package com.movinsync.shuttle.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ----------------------------------------------------------------
    // Standard error body
    // ----------------------------------------------------------------
    private ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status, String error, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    // ----------------------------------------------------------------
    // Domain exceptions
    // ----------------------------------------------------------------

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RouteNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRouteNotFound(RouteNotFoundException ex) {
        log.warn("Route not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTripNotFound(TripNotFoundException ex) {
        log.warn("Trip not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(VehicleNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleVehicleNotFound(VehicleNotFoundException ex) {
        log.warn("Vehicle not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBookingNotFound(BookingNotFoundException ex) {
        log.warn("Booking not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidSegmentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSegment(InvalidSegmentException ex) {
        log.warn("Invalid segment: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_SEGMENT", ex.getMessage());
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleSeatUnavailable(SeatUnavailableException ex) {
        log.info("Seat unavailable: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, "SEAT_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(DuplicateBookingException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateBooking(DuplicateBookingException ex) {
        log.warn("Duplicate booking: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, "DUPLICATE_BOOKING", ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("User already exists: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, "UNAUTHORIZED", ex.getMessage());
    }

    // ----------------------------------------------------------------
    // Validation errors
    // ----------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", 400);
        body.put("error", "VALIDATION_FAILED");
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // ----------------------------------------------------------------
    // Auth errors
    // ----------------------------------------------------------------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt");
        return buildError(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid email or password.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex) {
        log.error("Persistence failure", ex);
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "PERSISTENCE_UNAVAILABLE",
                "The booking service is temporarily unavailable. Please retry shortly.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    // ----------------------------------------------------------------
    // Fallback
    // ----------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.");
    }
}
