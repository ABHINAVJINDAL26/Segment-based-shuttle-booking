package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.BookingRequest;
import com.movinsync.shuttle.dto.BookingResponse;
import com.movinsync.shuttle.entity.*;
import com.movinsync.shuttle.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrency Integration Tests — Pessimistic Locking & Zero Double Booking")
class ConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private StopRepository stopRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private WaitlistRepository waitlistRepository;

    private Trip testTrip;
    private final List<User> testUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Clear all previous data in correct foreign key order
        bookingRepository.deleteAll();
        waitlistRepository.deleteAll();
        seatRepository.deleteAll();
        tripRepository.deleteAll();
        stopRepository.deleteAll();
        routeRepository.deleteAll();
        userRepository.deleteAll();

        // Create 1 route with 4 stops: Stop A (1) -> Stop B (2) -> Stop C (3) -> Stop D (4)
        Route route = routeRepository.save(Route.builder()
                .name("Express Corridor")
                .description("Main campus route")
                .build());

        stopRepository.save(Stop.builder().route(route).name("Stop A").sequenceNum(1).arrivalTime("08:00").build());
        stopRepository.save(Stop.builder().route(route).name("Stop B").sequenceNum(2).arrivalTime("08:20").build());
        stopRepository.save(Stop.builder().route(route).name("Stop C").sequenceNum(3).arrivalTime("08:40").build());
        stopRepository.save(Stop.builder().route(route).name("Stop D").sequenceNum(4).arrivalTime("09:00").build());

        // Trip with capacity = 1 (exactly ONE seat)
        testTrip = tripRepository.save(Trip.builder()
                .route(route)
                .tripDate(LocalDate.now().plusDays(2))
                .capacity(1)
                .status(Trip.TripStatus.SCHEDULED)
                .build());

        // Create the single physical seat
        seatRepository.save(Seat.builder()
                .trip(testTrip)
                .seatNumber(1)
                .build());

        // Create 10 distinct users to simulate 10 simultaneous passenger booking requests
        testUsers.clear();
        for (int i = 1; i <= 10; i++) {
            User user = userRepository.save(User.builder()
                    .name("Passenger " + i)
                    .email("passenger" + i + "@company.com")
                    .passwordHash("secret123")
                    .role(User.Role.EMPLOYEE)
                    .build());
            testUsers.add(user);
        }
    }

    @Test
    @DisplayName("Simulate 10 concurrent booking requests for 1 seat on same segment: Exactly 1 CONFIRMED, 9 WAITLISTED")
    void testConcurrentBookings_NoDoubleBooking() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<BookingResponse> responses = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger confirmedCount = new AtomicInteger(0);
        AtomicInteger waitlistedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                User user = testUsers.get(index);
                // Set thread-local security context for the current user
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.emptyList()));

                BookingRequest request = new BookingRequest();
                request.setFromStop("Stop A");
                request.setToStop("Stop C");

                readyLatch.countDown();
                try {
                    startLatch.await(); // Wait for simultaneous gun start

                    BookingResponse response = bookingService.book(testTrip.getId(), request);
                    responses.add(response);

                    if ("CONFIRMED".equals(response.getStatus())) {
                        confirmedCount.incrementAndGet();
                    } else if ("WAITLISTED".equals(response.getStatus())) {
                        waitlistedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                    finishLatch.countDown();
                }
            });
        }

        // Wait until all threads are initialized and ready
        readyLatch.await(5, TimeUnit.SECONDS);
        // Trigger all 10 threads concurrently
        startLatch.countDown();
        // Wait for all threads to finish
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "All threads should complete within timeout");
        executor.shutdown();

        // Assertions:
        assertEquals(0, errorCount.get(), "No unexpected errors should occur");
        assertEquals(1, confirmedCount.get(), "EXACTLY 1 booking should be CONFIRMED for the single seat");
        assertEquals(9, waitlistedCount.get(), "EXACTLY 9 remaining requests should be safely WAITLISTED");

        // Verify in database:
        List<Booking> confirmedBookings = bookingRepository.findActiveBookingsBySeatIdWithStops(
                seatRepository.findByTripId(testTrip.getId()).get(0).getId());
        assertEquals(1, confirmedBookings.size(), "Database must contain exactly 1 confirmed booking for the seat");

        List<WaitlistEntry> waitlist = waitlistRepository.findByTripIdAndStatusOrderByPositionAscCreatedAtAsc(
                testTrip.getId(), WaitlistEntry.WaitlistStatus.WAITING);
        assertEquals(9, waitlist.size(), "Waitlist must contain exactly 9 entries");

        // Verify FIFO order positions 1 to 9
        for (int i = 0; i < waitlist.size(); i++) {
            assertEquals(i + 1, waitlist.get(i).getPosition(), "Waitlist positions must be sequentially 1 to 9");
        }
    }
}
