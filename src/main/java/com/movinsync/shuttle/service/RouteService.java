package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.RouteRequest;
import com.movinsync.shuttle.dto.StopRequest;
import com.movinsync.shuttle.entity.Route;
import com.movinsync.shuttle.entity.Stop;
import com.movinsync.shuttle.exception.InvalidSegmentException;
import com.movinsync.shuttle.exception.RouteNotFoundException;
import com.movinsync.shuttle.repository.RouteRepository;
import com.movinsync.shuttle.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;

    @Transactional
    @CacheEvict(value = "routes", allEntries = true)
    public Route createRoute(RouteRequest request) {
        Route route = Route.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        Route saved = routeRepository.save(route);
        log.info("Route created: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"routes", "stops"}, allEntries = true)
    public Stop addStop(Long routeId, StopRequest request) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RouteNotFoundException(routeId));

        // Validate uniqueness
        if (stopRepository.existsByRouteIdAndSequenceNum(routeId, request.getSequenceNum())) {
            throw new InvalidSegmentException(
                    "Stop with sequence " + request.getSequenceNum() + " already exists on this route.");
        }
        if (stopRepository.existsByRouteIdAndName(routeId, request.getName())) {
            throw new InvalidSegmentException(
                    "Stop with name '" + request.getName() + "' already exists on this route.");
        }

        Stop stop = Stop.builder()
                .route(route)
                .name(request.getName())
                .sequenceNum(request.getSequenceNum())
                .arrivalTime(request.getArrivalTime())
                .build();

        Stop saved = stopRepository.save(stop);
        log.info("Stop added: routeId={}, stop={}, seq={}", routeId, saved.getName(), saved.getSequenceNum());
        return saved;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "routes", key = "#routeId")
    public Route getRoute(Long routeId) {
        return routeRepository.findById(routeId)
                .orElseThrow(() -> new RouteNotFoundException(routeId));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "routes", key = "'all'")
    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "stops", key = "#routeId")
    public List<Stop> getStops(Long routeId) {
        routeRepository.findById(routeId)
                .orElseThrow(() -> new RouteNotFoundException(routeId));
        return stopRepository.findByRouteIdOrderBySequenceNumAsc(routeId);
    }
}
