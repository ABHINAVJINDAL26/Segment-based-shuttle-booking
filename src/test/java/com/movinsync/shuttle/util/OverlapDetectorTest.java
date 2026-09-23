package com.movinsync.shuttle.util;

import com.movinsync.shuttle.entity.Booking;
import com.movinsync.shuttle.entity.Stop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OverlapDetector Unit Tests")
class OverlapDetectorTest {

    private OverlapDetector detector;

    @BeforeEach
    void setUp() {
        detector = new OverlapDetector();
    }

    private Booking createBooking(int startSeq, int endSeq) {
        Stop fromStop = Stop.builder().name("Stop" + startSeq).sequenceNum(startSeq).build();
        Stop toStop = Stop.builder().name("Stop" + endSeq).sequenceNum(endSeq).build();
        return Booking.builder()
                .fromStop(fromStop)
                .toStop(toStop)
                .status(Booking.BookingStatus.CONFIRMED)
                .build();
    }

    @Test
    @DisplayName("Empty bookings list should have no conflict")
    void testEmptyBookings_NoConflict() {
        assertFalse(detector.hasConflict(List.of(), 1, 4));
    }

    @Test
    @DisplayName("Touching segments: A->B (1->2) and B->D (2->4) should NOT conflict (seat reuse!)")
    void testTouchingSegments_NoConflict() {
        List<Booking> bookings = List.of(createBooking(1, 2));
        assertFalse(detector.hasConflict(bookings, 2, 4),
                "Seat should be available for B->D when already booked for A->B");
    }

    @Test
    @DisplayName("Disjoint segments: A->B (1->2) and C->D (3->4) should NOT conflict")
    void testDisjointSegments_NoConflict() {
        List<Booking> bookings = List.of(createBooking(1, 2));
        assertFalse(detector.hasConflict(bookings, 3, 4));
    }

    @Test
    @DisplayName("Overlapping segments: A->C (1->3) and B->D (2->4) should CONFLICT")
    void testPartialOverlap_HasConflict() {
        List<Booking> bookings = List.of(createBooking(1, 3));
        assertTrue(detector.hasConflict(bookings, 2, 4),
                "Segment [2,4] should conflict with existing [1,3]");
    }

    @Test
    @DisplayName("Exact identical segment: A->C (1->3) and A->C (1->3) should CONFLICT")
    void testExactMatch_HasConflict() {
        List<Booking> bookings = List.of(createBooking(1, 3));
        assertTrue(detector.hasConflict(bookings, 1, 3));
    }

    @Test
    @DisplayName("Enclosing segment: [1,4] requested while [2,3] exists should CONFLICT")
    void testEnclosingSegment_HasConflict() {
        List<Booking> bookings = List.of(createBooking(2, 3));
        assertTrue(detector.hasConflict(bookings, 1, 4));
    }

    @Test
    @DisplayName("Subset segment: [2,3] requested while [1,4] exists should CONFLICT")
    void testSubsetSegment_HasConflict() {
        List<Booking> bookings = List.of(createBooking(1, 4));
        assertTrue(detector.hasConflict(bookings, 2, 3));
    }

    @Test
    @DisplayName("Multiple bookings on same seat: [1,2] and [3,4] existing, requesting [2,3] should NOT conflict")
    void testFillingGapBetweenBookings_NoConflict() {
        List<Booking> bookings = List.of(
                createBooking(1, 2),
                createBooking(3, 4)
        );
        assertFalse(detector.hasConflict(bookings, 2, 3),
                "Gap [2,3] between [1,2] and [3,4] should be free");
    }

    @Test
    @DisplayName("Multiple bookings: [1,2] and [3,4] existing, requesting [1,3] should CONFLICT with [1,2]")
    void testMultipleBookings_OneConflicts() {
        List<Booking> bookings = List.of(
                createBooking(1, 2),
                createBooking(3, 4)
        );
        assertTrue(detector.hasConflict(bookings, 1, 3));
    }

    @Test
    @DisplayName("Raw sequence overload hasConflict test")
    void testRawSequenceOverload() {
        List<int[]> existing = new ArrayList<>();
        existing.add(new int[]{1, 2});
        existing.add(new int[]{3, 5});

        assertFalse(detector.hasConflictRaw(existing, 2, 3));
        assertTrue(detector.hasConflictRaw(existing, 2, 4));
    }
}
