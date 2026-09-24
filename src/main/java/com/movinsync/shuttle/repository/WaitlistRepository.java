package com.movinsync.shuttle.repository;

import com.movinsync.shuttle.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    /**
     * All WAITING entries for a trip ordered by position (FIFO).
     */
    List<WaitlistEntry> findByTripIdAndStatusOrderByPositionAscCreatedAtAsc(
            Long tripId, WaitlistEntry.WaitlistStatus status);

    /**
     * Maximum position for a trip so new entries get the next position.
     */
    @Query("SELECT MAX(w.position) FROM WaitlistEntry w WHERE w.trip.id = :tripId")
    Optional<Integer> findMaxPositionByTripId(@Param("tripId") Long tripId);

    /**
     * Check whether a user already has an active waitlist entry for the same trip+segment.
     */
    @Query("""
            SELECT COUNT(w) > 0 FROM WaitlistEntry w
            WHERE w.user.id = :userId
              AND w.trip.id = :tripId
              AND w.fromStop.id = :fromStopId
              AND w.toStop.id = :toStopId
              AND w.status = 'WAITING'
           """)
    boolean existsActiveWaitlistEntry(
            @Param("userId") Long userId,
            @Param("tripId") Long tripId,
            @Param("fromStopId") Long fromStopId,
            @Param("toStopId") Long toStopId
    );

    List<WaitlistEntry> findByUserId(Long userId);
}
