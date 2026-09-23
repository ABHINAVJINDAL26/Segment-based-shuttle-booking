package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.WaitlistResponse;
import com.movinsync.shuttle.entity.*;
import com.movinsync.shuttle.exception.DuplicateBookingException;
import com.movinsync.shuttle.exception.InvalidSegmentException;
import com.movinsync.shuttle.exception.UnauthorizedException;
import com.movinsync.shuttle.exception.UserNotFoundException;
import com.movinsync.shuttle.repository.BookingRepository;
import com.movinsync.shuttle.repository.SeatRepository;
import com.movinsync.shuttle.repository.UserRepository;
import com.movinsync.shuttle.repository.WaitlistRepository;
import com.movinsync.shuttle.util.OverlapDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * WaitlistService — manages FIFO waitlisting when seats are fully booked,
 * and handles automated promotion upon booking cancellations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final OverlapDetector overlapDetector;

    @Transactional
    public WaitlistEntry addToWaitlist(Trip trip, User user, Stop fromStop, Stop toStop) {
        if (waitlistRepository.existsActiveWaitlistEntry(
                user.getId(), trip.getId(), fromStop.getId(), toStop.getId())) {
            throw new DuplicateBookingException("You are already on the waitlist for this segment.");
        }

        int nextPosition = waitlistRepository.findMaxPositionByTripId(trip.getId())
                .orElse(0) + 1;

        WaitlistEntry entry = WaitlistEntry.builder()
                .trip(trip)
                .user(user)
                .fromStop(fromStop)
                .toStop(toStop)
                .position(nextPosition)
                .status(WaitlistEntry.WaitlistStatus.WAITING)
                .build();

        WaitlistEntry saved = waitlistRepository.save(entry);
        log.info("User {} added to waitlist for trip {} at position {}", user.getId(), trip.getId(), nextPosition);
        return saved;
    }

    /**
     * Promotes waitlisted passengers in FIFO order when a seat segment becomes available.
     */
    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public void promoteFromWaitlist(Trip trip, Seat vacatedSeat, Stop vacatedFrom, Stop vacatedTo) {
        List<WaitlistEntry> waitingList = waitlistRepository
                .findByTripIdAndStatusOrderByPositionAsc(trip.getId(), WaitlistEntry.WaitlistStatus.WAITING);

        if (waitingList.isEmpty()) {
            return;
        }

        log.info("Checking waitlist promotion for trip {}. Total waiting: {}", trip.getId(), waitingList.size());
        List<Seat> seats = seatRepository.findByTripId(trip.getId());

        for (WaitlistEntry entry : waitingList) {
            int reqStart = entry.getFromStop().getSequenceNum();
            int reqEnd = entry.getToStop().getSequenceNum();

            for (Seat seat : seats) {
                Seat lockedSeat = seatRepository.findByIdWithLock(seat.getId())
                        .orElse(seat);

                List<Booking> activeBookings = bookingRepository
                        .findActiveBookingsBySeatIdWithStops(lockedSeat.getId());

                if (!overlapDetector.hasConflict(activeBookings, reqStart, reqEnd)) {
                    // Seat available! Promote this user
                    Booking promotedBooking = Booking.builder()
                            .trip(trip)
                            .seat(lockedSeat)
                            .user(entry.getUser())
                            .fromStop(entry.getFromStop())
                            .toStop(entry.getToStop())
                            .status(Booking.BookingStatus.CONFIRMED)
                            .build();

                    bookingRepository.save(promotedBooking);

                    entry.setStatus(WaitlistEntry.WaitlistStatus.PROMOTED);
                    waitlistRepository.save(entry);

                    log.info("Waitlist entry {} PROMOTED: user={}, trip={}, seat={}, segment={}->{}",
                            entry.getId(), entry.getUser().getId(), trip.getId(),
                            lockedSeat.getSeatNumber(), entry.getFromStop().getName(), entry.getToStop().getName());
                    break; // Move to evaluate next waitlist entry
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> getTripWaitlist(Long tripId) {
        return waitlistRepository.findByTripIdAndStatusOrderByPositionAsc(tripId, WaitlistEntry.WaitlistStatus.WAITING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> getUserWaitlist(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        return waitlistRepository.findByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void cancelWaitlist(Long waitlistId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        WaitlistEntry entry = waitlistRepository.findById(waitlistId)
                .orElseThrow(() -> new InvalidSegmentException("Waitlist entry not found: " + waitlistId));

        boolean isOwner = entry.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new UnauthorizedException("You can only cancel your own waitlist entries.");
        }

        entry.setStatus(WaitlistEntry.WaitlistStatus.CANCELLED);
        waitlistRepository.save(entry);
        log.info("Waitlist entry {} cancelled by user {}", waitlistId, user.getId());
    }

    private WaitlistResponse toResponse(WaitlistEntry entry) {
        return WaitlistResponse.builder()
                .waitlistId(entry.getId())
                .tripId(entry.getTrip().getId())
                .fromStop(entry.getFromStop().getName())
                .toStop(entry.getToStop().getName())
                .position(entry.getPosition())
                .status(entry.getStatus().name())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
