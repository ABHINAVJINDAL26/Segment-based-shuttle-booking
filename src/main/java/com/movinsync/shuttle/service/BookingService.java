package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.BookingRequest;
import com.movinsync.shuttle.dto.BookingResponse;
import com.movinsync.shuttle.entity.*;
import com.movinsync.shuttle.exception.*;
import com.movinsync.shuttle.repository.*;
import com.movinsync.shuttle.util.OverlapDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BookingService — the heart of the segment-based booking system.
 *
 * Algorithm:
 *   1. Validate trip (must be SCHEDULED or ACTIVE)
 *   2. Resolve stop names → Stop entities (validate ordering)
 *   3. Check for duplicate booking by this user on same trip+segment
 *   4. Iterate all seats for the trip
 *   5. For each seat: acquire PESSIMISTIC_WRITE lock, load CONFIRMED bookings,
 *      run OverlapDetector.hasConflict()
 *   6. First seat with no conflict → create Booking, commit, return CONFIRMED
 *   7. If all seats conflict → add to waitlist
 *
 * Concurrency safety:
 *   - @Transactional ensures atomicity
 *   - PESSIMISTIC_WRITE row lock on seat prevents two concurrent
 *     transactions from allocating the same seat simultaneously
 *   - Only one thread can hold the lock at a time; the other waits
 *     and re-checks after acquiring the lock
 *
 * Complexity:
 *   Time:  O(S × B) where S = seats, B = avg bookings per seat
 *   Space: O(B) per seat evaluation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final StopRepository stopRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final WaitlistService waitlistService;
    private final OverlapDetector overlapDetector;

    // ---------------------------------------------------------------
    // Book a seat
    // ---------------------------------------------------------------

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public BookingResponse book(Long tripId, BookingRequest request) {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        validateTripBookable(trip);

        Stop fromStop = resolveStop(trip.getRoute().getId(), request.getFromStop());
        Stop toStop   = resolveStop(trip.getRoute().getId(), request.getToStop());

        validateSegmentOrder(fromStop, toStop);

        // Duplicate booking guard
        if (bookingRepository.existsActiveBooking(
                user.getId(), tripId, fromStop.getId(), toStop.getId())) {
            throw new DuplicateBookingException(
                    "You already have an active booking for this segment.");
        }

        log.info("Booking attempt: userId={}, tripId={}, segment={}→{}",
                user.getId(), tripId, fromStop.getName(), toStop.getName());

        // Seat search — O(S × B)
        List<Seat> seats = seatRepository.findByTripId(tripId);

        for (Seat seat : seats) {
            // Pessimistic write lock — only one transaction at a time can
            // inspect/modify this seat row. This prevents the last-seat race.
            Seat lockedSeat = seatRepository.findByIdWithLock(seat.getId())
                    .orElseThrow();

            List<Booking> activeBookings = bookingRepository
                    .findActiveBookingsBySeatIdWithStops(lockedSeat.getId());

            if (!overlapDetector.hasConflict(activeBookings,
                    fromStop.getSequenceNum(), toStop.getSequenceNum())) {

                Booking booking = Booking.builder()
                        .trip(trip)
                        .seat(lockedSeat)
                        .user(user)
                        .fromStop(fromStop)
                        .toStop(toStop)
                        .status(Booking.BookingStatus.CONFIRMED)
                        .build();

                Booking saved = bookingRepository.save(booking);

                log.info("Booking CONFIRMED: bookingId={}, seat={}, userId={}, segment={}→{}",
                        saved.getId(), lockedSeat.getSeatNumber(),
                        user.getId(), fromStop.getName(), toStop.getName());

                return BookingResponse.builder()
                        .bookingId(saved.getId())
                        .tripId(tripId)
                        .seatNumber(lockedSeat.getSeatNumber())
                        .fromStop(fromStop.getName())
                        .toStop(toStop.getName())
                        .status("CONFIRMED")
                        .createdAt(saved.getCreatedAt())
                        .build();
            }
        }

        // No seat available — join waitlist
        log.info("No seat available, adding to waitlist: userId={}, tripId={}", user.getId(), tripId);
        WaitlistEntry entry = waitlistService.addToWaitlist(trip, user, fromStop, toStop);

        return BookingResponse.builder()
                .tripId(tripId)
                .fromStop(fromStop.getName())
                .toStop(toStop.getName())
                .status("WAITLISTED")
                .message("No seat available. You are at waitlist position: " + entry.getPosition())
                .build();
    }

    // ---------------------------------------------------------------
    // Cancel a booking
    // ---------------------------------------------------------------

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public BookingResponse cancel(Long bookingId) {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        // Only the owner or an ADMIN can cancel
        boolean isOwner = booking.getUser().getId().equals(user.getId());
        boolean isAdmin  = user.getRole() == User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new UnauthorizedException("You can only cancel your own bookings.");
        }

        if (booking.getStatus() != Booking.BookingStatus.CONFIRMED) {
            throw new InvalidSegmentException(
                    "Booking " + bookingId + " is already " + booking.getStatus());
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        log.info("Booking CANCELLED: bookingId={}, userId={}", bookingId, user.getId());

        // After cancellation, try to promote eligible waitlisted passenger
        waitlistService.promoteFromWaitlist(booking.getTrip(), booking.getSeat(),
                booking.getFromStop(), booking.getToStop());

        return BookingResponse.builder()
                .bookingId(booking.getId())
                .tripId(booking.getTrip().getId())
                .seatNumber(booking.getSeat().getSeatNumber())
                .fromStop(booking.getFromStop().getName())
                .toStop(booking.getToStop().getName())
                .status("CANCELLED")
                .build();
    }

    // ---------------------------------------------------------------
    // Get booking
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public Booking getBooking(Long bookingId) {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        boolean isOwner = booking.getUser().getId().equals(user.getId());
        boolean isAdmin  = user.getRole() == User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new UnauthorizedException("You can only view your own bookings.");
        }

        return booking;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void validateTripBookable(Trip trip) {
        if (trip.getStatus() == Trip.TripStatus.CANCELLED) {
            throw new InvalidSegmentException("Trip " + trip.getId() + " is CANCELLED.");
        }
        if (trip.getStatus() == Trip.TripStatus.COMPLETED) {
            throw new InvalidSegmentException("Trip " + trip.getId() + " is COMPLETED.");
        }
    }

    private Stop resolveStop(Long routeId, String stopName) {
        return stopRepository.findByRouteIdAndName(routeId, stopName)
                .orElseThrow(() -> new InvalidSegmentException(
                        "Stop '" + stopName + "' not found on this route."));
    }

    private void validateSegmentOrder(Stop from, Stop to) {
        if (from.getSequenceNum() >= to.getSequenceNum()) {
            throw new InvalidSegmentException(
                    "Source stop '" + from.getName() + "' (seq=" + from.getSequenceNum() +
                    ") must be before destination stop '" + to.getName() +
                    "' (seq=" + to.getSequenceNum() + ").");
        }
    }
}
