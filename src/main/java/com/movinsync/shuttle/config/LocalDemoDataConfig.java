package com.movinsync.shuttle.config;

import com.movinsync.shuttle.entity.*;
import com.movinsync.shuttle.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalDemoDataConfig implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final TripRepository tripRepository;
    private final SeatRepository seatRepository;
    private final VehicleRepository vehicleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        // Seed users if empty
        if (userRepository.count() == 0) {
            log.info("Seeding default demo users...");
            userRepository.saveAll(List.of(
                    User.builder()
                            .name("Rahul Sharma")
                            .email("rahul@company.com")
                            .passwordHash(passwordEncoder.encode("User123!"))
                            .role(User.Role.EMPLOYEE)
                            .build(),
                    User.builder()
                            .name("Priya Patel")
                            .email("priya@company.com")
                            .passwordHash(passwordEncoder.encode("User123!"))
                            .role(User.Role.EMPLOYEE)
                            .build(),
                    User.builder()
                            .name("Amit Kumar")
                            .email("amit@company.com")
                            .passwordHash(passwordEncoder.encode("User123!"))
                            .role(User.Role.EMPLOYEE)
                            .build(),
                    User.builder()
                            .name("System Admin")
                            .email("admin@movinsync.com")
                            .passwordHash(passwordEncoder.encode("Admin123!"))
                            .role(User.Role.ADMIN)
                            .build()
            ));
        }

        // Seed route, stops, vehicle, trip and seats if trip list is empty
        if (tripRepository.count() == 0) {
            log.info("Seeding default shuttle route, stops, trip, and seats...");

            Route route = routeRepository.save(Route.builder()
                    .name("Tech Corridor Express")
                    .description("Main shuttle route connecting Tech Park to City Center")
                    .build());

            stopRepository.saveAll(List.of(
                    Stop.builder().route(route).name("Stop A").sequenceNum(1).arrivalTime("08:00 AM").build(),
                    Stop.builder().route(route).name("Stop B").sequenceNum(2).arrivalTime("08:20 AM").build(),
                    Stop.builder().route(route).name("Stop C").sequenceNum(3).arrivalTime("08:40 AM").build(),
                    Stop.builder().route(route).name("Stop D").sequenceNum(4).arrivalTime("09:00 AM").build()
            ));

            Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                    .registrationNumber("KA-01-EQ-1234")
                    .capacity(2)
                    .active(true)
                    .build());

            Trip trip = tripRepository.save(Trip.builder()
                    .route(route)
                    .tripDate(LocalDate.now())
                    .capacity(2)
                    .status(Trip.TripStatus.SCHEDULED)
                    .vehicle(vehicle)
                    .currentStopSequence(1)
                    .build());

            seatRepository.saveAll(List.of(
                    Seat.builder().trip(trip).seatNumber(1).build(),
                    Seat.builder().trip(trip).seatNumber(2).build()
            ));

            log.info("Seed data ready! Trip #{} initialized with 2 physical seats and 4 stops.", trip.getId());
        }
    }
}