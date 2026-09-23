package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.BookingRequest;
import com.movinsync.shuttle.dto.BookingResponse;
import com.movinsync.shuttle.entity.*;
import com.movinsync.shuttle.exception.DuplicateBookingException;
import com.movinsync.shuttle.exception.InvalidSegmentException;
import com.movinsync.shuttle.repository.*;
import com.movinsync.shuttle.util.OverlapDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Tests")
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private StopRepository stopRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WaitlistService waitlistService;
    @Spy
    private OverlapDetector overlapDetector = new OverlapDetector();

    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private BookingService bookingService;

    private User testUser;
    private Route testRoute;
    private Stop stopA;
    private Stop stopB;
    private Stop stopC;
    private Stop stopD;
    private Trip testTrip;
    private Seat testSeat;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Alice Smith")
                .email("alice@company.com")
                .role(User.Role.EMPLOYEE)
                .build();

        testRoute = Route.builder()
                .id(1L)
                .name("Route 1: Whitefield to Electronic City")
                .build();

        stopA = Stop.builder().id(1L).route(testRoute).name("Stop A").sequenceNum(1).arrivalTime("08:00").build();
        stopB = Stop.builder().id(2L).route(testRoute).name("Stop B").sequenceNum(2).arrivalTime("08:20").build();
        stopC = Stop.builder().id(3L).route(testRoute).name("Stop C").sequenceNum(3).arrivalTime("08:40").build();
        stopD = Stop.builder().id(4L).route(testRoute).name("Stop D").sequenceNum(4).arrivalTime("09:00").build();

        testTrip = Trip.builder()
                .id(100L)
                .route(testRoute)
                .tripDate(LocalDate.now().plusDays(1))
                .capacity(1)
                .status(Trip.TripStatus.SCHEDULED)
                .build();

        testSeat = Seat.builder()
                .id(10L)
                .seatNumber(1)
                .trip(testTrip)
                .build();

        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("alice@company.com");
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should successfully book a free seat for valid segment")
    void testBookSeat_Success() {
        BookingRequest request = new BookingRequest();
        request.setFromStop("Stop A");
        request.setToStop("Stop B");

        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));
        when(stopRepository.findByRouteIdAndName(1L, "Stop A")).thenReturn(Optional.of(stopA));
        when(stopRepository.findByRouteIdAndName(1L, "Stop B")).thenReturn(Optional.of(stopB));
        when(bookingRepository.existsActiveBooking(1L, 100L, 1L, 2L)).thenReturn(false);
        when(seatRepository.findByTripId(100L)).thenReturn(List.of(testSeat));
        when(seatRepository.findByIdWithLock(10L)).thenReturn(Optional.of(testSeat));
        when(bookingRepository.findActiveBookingsBySeatIdWithStops(10L)).thenReturn(List.of());

        Booking savedBooking = Booking.builder()
                .id(500L)
                .trip(testTrip)
                .seat(testSeat)
                .user(testUser)
                .fromStop(stopA)
                .toStop(stopB)
                .status(Booking.BookingStatus.CONFIRMED)
                .build();
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        BookingResponse response = bookingService.book(100L, request);

        assertNotNull(response);
        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(1, response.getSeatNumber());
        assertEquals(500L, response.getBookingId());
        assertEquals("Stop A", response.getFromStop());
        assertEquals("Stop B", response.getToStop());
        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    @DisplayName("Should throw DuplicateBookingException when user already booked this segment")
    void testBookSeat_DuplicateBookingThrowsException() {
        BookingRequest request = new BookingRequest();
        request.setFromStop("Stop A");
        request.setToStop("Stop B");

        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));
        when(stopRepository.findByRouteIdAndName(1L, "Stop A")).thenReturn(Optional.of(stopA));
        when(stopRepository.findByRouteIdAndName(1L, "Stop B")).thenReturn(Optional.of(stopB));
        when(bookingRepository.existsActiveBooking(1L, 100L, 1L, 2L)).thenReturn(true);

        assertThrows(DuplicateBookingException.class, () -> bookingService.book(100L, request));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("Should throw InvalidSegmentException when fromStop is after toStop")
    void testBookSeat_InvalidSegmentOrderThrowsException() {
        BookingRequest request = new BookingRequest();
        request.setFromStop("Stop C");
        request.setToStop("Stop A");

        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));
        when(stopRepository.findByRouteIdAndName(1L, "Stop C")).thenReturn(Optional.of(stopC));
        when(stopRepository.findByRouteIdAndName(1L, "Stop A")).thenReturn(Optional.of(stopA));

        assertThrows(InvalidSegmentException.class, () -> bookingService.book(100L, request));
    }

    @Test
    @DisplayName("Should add to waitlist when all seats conflict with requested segment")
    void testBookSeat_AllSeatsFull_Waitlisted() {
        BookingRequest request = new BookingRequest();
        request.setFromStop("Stop A");
        request.setToStop("Stop D");

        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));
        when(stopRepository.findByRouteIdAndName(1L, "Stop A")).thenReturn(Optional.of(stopA));
        when(stopRepository.findByRouteIdAndName(1L, "Stop D")).thenReturn(Optional.of(stopD));
        when(bookingRepository.existsActiveBooking(1L, 100L, 1L, 4L)).thenReturn(false);
        when(seatRepository.findByTripId(100L)).thenReturn(List.of(testSeat));
        when(seatRepository.findByIdWithLock(10L)).thenReturn(Optional.of(testSeat));

        // Existing booking on testSeat covers B->C, which conflicts with A->D
        Booking existingBooking = Booking.builder()
                .fromStop(stopB)
                .toStop(stopC)
                .status(Booking.BookingStatus.CONFIRMED)
                .build();
        when(bookingRepository.findActiveBookingsBySeatIdWithStops(10L)).thenReturn(List.of(existingBooking));

        WaitlistEntry waitlistEntry = WaitlistEntry.builder()
                .id(20L)
                .position(1)
                .trip(testTrip)
                .user(testUser)
                .build();
        when(waitlistService.addToWaitlist(eq(testTrip), eq(testUser), eq(stopA), eq(stopD)))
                .thenReturn(waitlistEntry);

        BookingResponse response = bookingService.book(100L, request);

        assertNotNull(response);
        assertEquals("WAITLISTED", response.getStatus());
        assertTrue(response.getMessage().contains("position: 1"));
        verify(waitlistService, times(1)).addToWaitlist(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should allow same physical seat to be reused for non-overlapping segment (seat sharing)")
    void testBookSeat_NonOverlappingSegmentReusesSeat() {
        // Seat 1 is booked for A->B. Request is for B->D. They touch at B but do not overlap!
        BookingRequest request = new BookingRequest();
        request.setFromStop("Stop B");
        request.setToStop("Stop D");

        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));
        when(stopRepository.findByRouteIdAndName(1L, "Stop B")).thenReturn(Optional.of(stopB));
        when(stopRepository.findByRouteIdAndName(1L, "Stop D")).thenReturn(Optional.of(stopD));
        when(bookingRepository.existsActiveBooking(1L, 100L, 2L, 4L)).thenReturn(false);
        when(seatRepository.findByTripId(100L)).thenReturn(List.of(testSeat));
        when(seatRepository.findByIdWithLock(10L)).thenReturn(Optional.of(testSeat));

        Booking existingBooking = Booking.builder()
                .fromStop(stopA)
                .toStop(stopB)
                .status(Booking.BookingStatus.CONFIRMED)
                .build();
        when(bookingRepository.findActiveBookingsBySeatIdWithStops(10L)).thenReturn(List.of(existingBooking));

        Booking newBooking = Booking.builder()
                .id(501L)
                .trip(testTrip)
                .seat(testSeat)
                .user(testUser)
                .fromStop(stopB)
                .toStop(stopD)
                .status(Booking.BookingStatus.CONFIRMED)
                .build();
        when(bookingRepository.save(any(Booking.class))).thenReturn(newBooking);

        BookingResponse response = bookingService.book(100L, request);

        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(1, response.getSeatNumber());
    }
}
