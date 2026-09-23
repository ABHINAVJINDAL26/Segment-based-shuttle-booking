package com.movinsync.shuttle.repository;

import com.movinsync.shuttle.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByTripId(Long tripId);

    /**
     * Returns all CONFIRMED bookings for a given seat.
     * Used by the overlap detection algorithm.
     */
    @Query("SELECT b FROM Booking b WHERE b.seat.id = :seatId AND b.status = 'CONFIRMED'")
    List<Booking> findActiveBookingsBySeatId(@Param("seatId") Long seatId);

    /**
     * Returns all CONFIRMED bookings for a given seat with stop details eagerly loaded.
     * Avoids N+1 query in overlap detection.
     */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.fromStop
            JOIN FETCH b.toStop
            WHERE b.seat.id = :seatId
              AND b.status = 'CONFIRMED'
           """)
    List<Booking> findActiveBookingsBySeatIdWithStops(@Param("seatId") Long seatId);

    /**
     * Checks if a user already has a CONFIRMED booking for the same trip+segment.
     * Prevents duplicate bookings.
     */
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.user.id = :userId
              AND b.trip.id = :tripId
              AND b.fromStop.id = :fromStopId
              AND b.toStop.id = :toStopId
              AND b.status = 'CONFIRMED'
           """)
    boolean existsActiveBooking(
            @Param("userId") Long userId,
            @Param("tripId") Long tripId,
            @Param("fromStopId") Long fromStopId,
            @Param("toStopId") Long toStopId
    );
}
