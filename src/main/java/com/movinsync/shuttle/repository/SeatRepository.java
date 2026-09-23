package com.movinsync.shuttle.repository;

import com.movinsync.shuttle.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByTripId(Long tripId);

    Optional<Seat> findByTripIdAndSeatNumber(Long tripId, Integer seatNumber);

    /**
     * Pessimistic write lock on a seat row to prevent concurrent double-booking.
     * This is the key concurrency guard for the last-seat race condition.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdWithLock(@Param("id") Long id);
}
