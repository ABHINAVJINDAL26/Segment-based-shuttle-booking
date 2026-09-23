package com.movinsync.shuttle.repository;

import com.movinsync.shuttle.entity.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StopRepository extends JpaRepository<Stop, Long> {

    List<Stop> findByRouteIdOrderBySequenceNumAsc(Long routeId);

    Optional<Stop> findByRouteIdAndName(Long routeId, String name);

    @Query("SELECT MAX(s.sequenceNum) FROM Stop s WHERE s.route.id = :routeId")
    Optional<Integer> findMaxSequenceByRouteId(@Param("routeId") Long routeId);

    boolean existsByRouteIdAndSequenceNum(Long routeId, Integer sequenceNum);

    boolean existsByRouteIdAndName(Long routeId, String name);
}
