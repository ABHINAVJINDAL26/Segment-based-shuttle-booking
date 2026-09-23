package com.movinsync.shuttle.repository;

import com.movinsync.shuttle.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByRouteId(Long routeId);

    List<Trip> findByTripDate(LocalDate date);

    List<Trip> findByRouteIdAndTripDate(Long routeId, LocalDate date);
}
