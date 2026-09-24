package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.TripRequest;
import com.movinsync.shuttle.entity.Route;
import com.movinsync.shuttle.entity.Seat;
import com.movinsync.shuttle.entity.Trip;
import com.movinsync.shuttle.exception.RouteNotFoundException;
import com.movinsync.shuttle.exception.TripNotFoundException;
import com.movinsync.shuttle.exception.VehicleNotFoundException;
import com.movinsync.shuttle.repository.RouteRepository;
import com.movinsync.shuttle.repository.SeatRepository;
import com.movinsync.shuttle.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final SeatRepository seatRepository;
    private final com.movinsync.shuttle.repository.VehicleRepository vehicleRepository;

    @Transactional
    @CacheEvict(value = "trips", allEntries = true)
    public Trip createTrip(TripRequest request) {
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new RouteNotFoundException(request.getRouteId()));

        Trip trip = Trip.builder()
                .route(route)
                .tripDate(request.getTripDate())
                .capacity(request.getCapacity())
                .status(Trip.TripStatus.SCHEDULED)
                .build();

        Trip savedTrip = tripRepository.save(trip);

        // Create physical seats for this trip
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= request.getCapacity(); i++) {
            seats.add(Seat.builder()
                    .trip(savedTrip)
                    .seatNumber(i)
                    .build());
        }
        seatRepository.saveAll(seats);

        log.info("Trip created: id={}, route={}, date={}, capacity={}",
                savedTrip.getId(), route.getName(), request.getTripDate(), request.getCapacity());

        return savedTrip;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "trips", key = "#tripId")
    public Trip getTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
    }

    @Transactional(readOnly = true)
    public List<Trip> getAllTrips() {
        return tripRepository.findAll();
    }

    @Transactional
    @CacheEvict(value = {"trips", "availability"}, allEntries = true)
    public Trip updateTripStatus(Long tripId, Trip.TripStatus status) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        trip.setStatus(status);
        return tripRepository.save(trip);
    }

    @Transactional
    @CacheEvict(value = {"trips", "availability"}, allEntries = true)
    public Trip updateCurrentStop(Long tripId, int currentStopSequence) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        if (currentStopSequence < 1) {
            throw new IllegalArgumentException("Current stop sequence must be at least 1.");
        }
        trip.setCurrentStopSequence(currentStopSequence);
        return tripRepository.save(trip);
    }

    @Transactional
    @CacheEvict(value = {"trips", "availability"}, allEntries = true)
    public Trip assignVehicle(Long tripId, Long vehicleId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        var vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        if (!vehicle.isActive()) {
            throw new IllegalArgumentException("Vehicle is inactive.");
        }
        if (vehicle.getCapacity() < trip.getCapacity()) {
            throw new IllegalArgumentException("Vehicle capacity cannot be smaller than trip capacity.");
        }
        trip.setVehicle(vehicle);
        return tripRepository.save(trip);
    }
}
