package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.AvailabilityResponse;
import com.movinsync.shuttle.entity.Booking;
import com.movinsync.shuttle.entity.Seat;
import com.movinsync.shuttle.entity.Stop;
import com.movinsync.shuttle.entity.Trip;
import com.movinsync.shuttle.exception.InvalidSegmentException;
import com.movinsync.shuttle.exception.TripNotFoundException;
import com.movinsync.shuttle.repository.BookingRepository;
import com.movinsync.shuttle.repository.SeatRepository;
import com.movinsync.shuttle.repository.StopRepository;
import com.movinsync.shuttle.repository.TripRepository;
import com.movinsync.shuttle.util.OverlapDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * AvailabilityService — computes available physical seats for any requested segment
 * using segment-overlap detection, backed by Redis caching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final OverlapDetector overlapDetector;

    @Transactional(readOnly = true)
    @Cacheable(value = "availability", key = "#tripId + '_' + #fromStop + '_' + #toStop")
    public AvailabilityResponse getAvailability(Long tripId, String fromStop, String toStop) {
        log.info("Calculating availability (cache miss): tripId={}, segment={}->{}", tripId, fromStop, toStop);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        if (trip.getStatus() == Trip.TripStatus.CANCELLED || trip.getStatus() == Trip.TripStatus.COMPLETED) {
            throw new InvalidSegmentException("Trip is " + trip.getStatus());
        }

        Stop srcStop = stopRepository.findByRouteIdAndName(trip.getRoute().getId(), fromStop)
                .orElseThrow(() -> new InvalidSegmentException("Stop '" + fromStop + "' not found on route"));

        Stop destStop = stopRepository.findByRouteIdAndName(trip.getRoute().getId(), toStop)
                .orElseThrow(() -> new InvalidSegmentException("Stop '" + toStop + "' not found on route"));

        if (srcStop.getSequenceNum() >= destStop.getSequenceNum()) {
            throw new InvalidSegmentException("From stop must precede To stop in route sequence.");
        }

        List<Seat> seats = seatRepository.findByTripId(tripId);
        List<Integer> availableSeatNumbers = new ArrayList<>();

        int reqStart = srcStop.getSequenceNum();
        int reqEnd = destStop.getSequenceNum();

        for (Seat seat : seats) {
            List<Booking> activeBookings = bookingRepository.findActiveBookingsBySeatIdWithStops(seat.getId());
            if (!overlapDetector.hasConflict(activeBookings, reqStart, reqEnd)) {
                availableSeatNumbers.add(seat.getSeatNumber());
            }
        }

        return AvailabilityResponse.builder()
                .tripId(tripId)
                .from(fromStop)
                .to(toStop)
                .availableSeats(availableSeatNumbers)
                .availableCount(availableSeatNumbers.size())
                .build();
    }
}
