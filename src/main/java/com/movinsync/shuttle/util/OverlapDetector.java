package com.movinsync.shuttle.util;

import com.movinsync.shuttle.entity.Booking;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Core algorithm for segment overlap detection.
 *
 * Problem: A physical seat can be shared by multiple passengers on
 * non-overlapping route segments. We must detect overlaps to prevent
 * double-booking on the same physical seat.
 *
 * Overlap Rule (using sequence numbers, not stop names):
 *   Given existing segment [Xs, Xd] and requested segment [Ys, Yd]:
 *   They OVERLAP if: Ys < Xd  AND  Yd > Xs
 *   They are COMPATIBLE (no overlap) if: Yd <= Xs  OR  Ys >= Xd
 *
 * Example (A=1, B=2, C=3, D=4):
 *   Existing A→C = [1,3], Requested B→D = [2,4]
 *   Check: 2 < 3 AND 4 > 1  → TRUE  → OVERLAP (reject)
 *
 *   Existing A→B = [1,2], Requested B→D = [2,4]
 *   Check: 2 < 2 → FALSE   → NO OVERLAP (allow) ✓
 *
 * Time Complexity: O(B) per seat where B = active bookings on that seat.
 * Space Complexity: O(1) extra space per check.
 */
@Component
public class OverlapDetector {

    /**
     * Returns true if the requested segment [reqStart, reqEnd] conflicts with
     * ANY existing active booking on the same seat.
     *
     * @param existingBookings  active (CONFIRMED) bookings on the candidate seat
     * @param reqStart          sequence number of requested FROM stop
     * @param reqEnd            sequence number of requested TO stop
     * @return true if there is a conflict, false if the seat is free for this segment
     */
    public boolean hasConflict(List<Booking> existingBookings, int reqStart, int reqEnd) {
        for (Booking existing : existingBookings) {
            int existStart = existing.getFromStop().getSequenceNum();
            int existEnd   = existing.getToStop().getSequenceNum();

            // Two intervals [a,b) and [c,d) overlap iff c < b AND d > a
            // Here we treat stop sequences as point values and the segment
            // [A,B] means "occupied during travel from A until B".
            // A→B and B→D touch at B but do NOT share a road segment,
            // so we use strict inequalities: reqStart < existEnd AND reqEnd > existStart
            if (reqStart < existEnd && reqEnd > existStart) {
                return true; // conflict found
            }
        }
        return false;
    }

    /**
     * Convenience overload that accepts raw sequence numbers directly
     * (useful in unit tests and service-layer calls).
     */
    public boolean hasConflictRaw(List<int[]> existingSegments, int reqStart, int reqEnd) {
        for (int[] seg : existingSegments) {
            if (reqStart < seg[1] && reqEnd > seg[0]) {
                return true;
            }
        }
        return false;
    }
}
