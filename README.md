# Office Shuttle Segment-Based Seat Booking System

> **MoveInSync Backend Case Study (2026)** — Production-grade Spring Boot backend service managing segment-based seat bookings for corporate employee shuttles, featuring dynamic seat reuse, concurrency control, and dynamic FIFO waitlisting.

---

## 🚌 Problem Statement & Overview

In an office shuttle network, a shuttle travels sequentially across multiple stops on a route:
$$\text{Stop 1} \longrightarrow \text{Stop 2} \longrightarrow \dots \longrightarrow \text{Stop } N$$

Passengers book seats for specific sub-segments rather than the entire route (e.g., from Stop A to Stop B, or Stop B to Stop D).

### Core Challenges Solved:
1. **Dynamic Seat Reuse**: A single physical seat can be booked by passenger 1 for segment $[A, B]$ and passenger 2 for segment $[B, D]$ without conflict.
2. **Overlap Detection Algorithm**: Prevents double-booking if two passengers request conflicting segments (e.g., $[A, C]$ and $[B, D]$ overlap between $[B, C]$).
3. **Concurrency & Race Conditions**: Prevents race conditions when multiple concurrent users attempt to book the last seat for an overlapping segment.
4. **Dynamic Waitlisting & Auto-Promotion**: Passengers unable to get a confirmed seat are placed on a FIFO waitlist. If a booking is cancelled, waitlisted passengers are automatically evaluated and promoted.
5. **High-Throughput Caching**: Redis caches route metadata and seat availability to minimize database pressure on hot read paths.

---

## 🏛 System Architecture & Tech Stack

```
                              ┌──────────────────┐
                              │  Client / UI     │
                              │ (Postman, Web)   │
                              └────────┬─────────┘
                                       │ HTTP / REST
                                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Spring Boot 3.2.5 (Java 17 / 21)                                       │
│                                                                        │
│  [Spring Security]  ── JWT Bearer Authentication Filter                │
│                                                                        │
│  [REST Controllers] Auth, Route, Trip, Booking, Waitlist               │
│                                                                        │
│  [Service Layer]    - OverlapDetector (Interval scheduling logic)      │
│                     - BookingService (Pessimistic write locking)       │
│                     - AvailabilityService (@Cacheable Redis)           │
│                     - WaitlistService (FIFO auto-promotion)            │
│                                                                        │
│  [Data Layer]       Spring Data JPA Repositories                       │
└───────────────────────┬───────────────────────────────┬────────────────┘
                        │                               │
                        ▼                               ▼
            ┌───────────────────────┐       ┌───────────────────────┐
            │ PostgreSQL 15         │       │ Redis 7               │
            │ Relational persistence│       │ Fast cache for        │
            │ Pessimistic Row Locks │       │ Availability & Routes │
            └───────────────────────┘       └───────────────────────┘
```

- **Framework**: Spring Boot 3.2.5, Spring Security 6, Spring Data JPA
- **Language**: Java 17+ (tested with Java 21)
- **Database**: PostgreSQL 15 with Flyway database migration
- **Cache**: Redis 7
- **Security**: Stateless JWT (JSON Web Tokens) with BCrypt password hashing
- **Testing**: JUnit 5, Mockito, H2 In-Memory DB, SpringBootTest multi-threaded concurrency
- **Documentation**: OpenAPI 3.0 / Swagger UI (`/swagger-ui.html`)
- **Containerization**: Docker multi-stage build & Docker Compose

---

## 🧮 Core Algorithm: Segment Overlap Detection

Routes are sequenced from Stop $1$ to $N$. Each stop has an integer `sequence_num`.

### Mathematical Overlap Condition:
Given existing confirmed segment $[X_s, X_d]$ and requested segment $[Y_s, Y_d]$:

$$\text{Overlap} \iff (Y_s < X_d) \land (Y_d > X_s)$$

### Segment Scenarios:
| Scenario | Existing Segment | Requested Segment | Overlap? | Action |
|---|---|---|---|---|
| **Touching (Seat Reuse)** | $A \to B$ $[1, 2]$ | $B \to D$ $[2, 4]$ | **No** | ✅ **Seat Reused** |
| **Disjoint** | $A \to B$ $[1, 2]$ | $C \to D$ $[3, 4]$ | **No** | ✅ **Allowed** |
| **Partial Overlap** | $A \to C$ $[1, 3]$ | $B \to D$ $[2, 4]$ | **Yes** ($2 < 3 \land 4 > 1$) | ❌ **Rejected / Waitlist** |
| **Identical** | $A \to C$ $[1, 3]$ | $A \to C$ $[1, 3]$ | **Yes** | ❌ **Rejected / Waitlist** |
| **Enclosing** | $B \to C$ $[2, 3]$ | $A \to D$ $[1, 4]$ | **Yes** | ❌ **Rejected / Waitlist** |
| **Subset** | $A \to D$ $[1, 4]$ | $B \to C$ $[2, 3]$ | **Yes** | ❌ **Rejected / Waitlist** |

**Complexity**: $O(B)$ per seat check, where $B$ is the number of active bookings on that seat. For $S$ physical seats on a shuttle, the total seat search is $O(S \times B)$.

---

## 🔒 Concurrency Strategy & Pessimistic Locking

To eliminate race conditions when multiple users book the last seat concurrently:

```java
@Transactional
public BookingResponse book(Long tripId, BookingRequest request) {
    // 1. Validate segment and user uniqueness
    // 2. Query physical seats for the trip
    for (Seat seat : seats) {
        // Acquire PESSIMISTIC_WRITE lock on seat row: SELECT ... FOR UPDATE
        Seat lockedSeat = seatRepository.findByIdWithLock(seat.getId()).orElseThrow();
        List<Booking> activeBookings = bookingRepository.findActiveBookingsBySeatIdWithStops(lockedSeat.getId());
        
        if (!overlapDetector.hasConflict(activeBookings, fromSeq, toSeq)) {
            // Allocate seat and persist booking
            return confirmBooking(...);
        }
    }
    // 3. If no seat has conflict-free availability, safely add to Waitlist (FIFO)
    return addToWaitlist(...);
}
```

---

## 📋 API Endpoints Reference

| Method | Endpoint | Description | Role |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new employee | Public |
| `POST` | `/api/auth/login` | Login and obtain JWT token | Public |
| `POST` | `/api/routes` | Create new route | `ADMIN` |
| `POST` | `/api/routes/{routeId}/stops` | Add sequenced stop to route | `ADMIN` |
| `GET` | `/api/routes` | List all routes | Authenticated |
| `GET` | `/api/routes/{routeId}/stops` | Get stops for route in order | Authenticated |
| `POST` | `/api/trips` | Schedule trip with seat capacity | `ADMIN` |
| `GET` | `/api/trips` | List all scheduled trips | Authenticated |
| `GET` | `/api/trips/{tripId}/availability?from=A&to=B` | Query available seats for segment | Authenticated |
| `POST` | `/api/trips/{tripId}/bookings` | Book segment seat or join waitlist | Authenticated |
| `DELETE` | `/api/bookings/{bookingId}` | Cancel booking (triggers auto-promotion) | Authenticated |
| `GET` | `/api/bookings/{bookingId}` | Get booking details | Authenticated |
| `GET` | `/api/waitlist` | Get current user's waitlist entries | Authenticated |
| `GET` | `/api/trips/{tripId}/waitlist` | View FIFO waitlist for trip | `ADMIN` |
| `PUT` | `/api/bookings/{bookingId}/no-show` | Mark a booking as NO_SHOW and promote waitlist | Owner / `ADMIN` |
| `PATCH` | `/api/trips/{tripId}/position?currentStopSequence=N` | Update active trip position | `ADMIN` |
| `POST` | `/api/vehicles` | Register a shuttle vehicle | `ADMIN` |
| `PUT` | `/api/trips/{tripId}/vehicle/{vehicleId}` | Assign/reassign a vehicle | `ADMIN` |

Public registration always creates an `EMPLOYEE`. Administrator accounts must be provisioned by a trusted database migration or operator workflow; clients cannot self-assign the `ADMIN` role.

### Operational behavior

- A trip in `ACTIVE` status rejects bookings whose boarding stop has already been passed. Administrators advance the trip position with the position endpoint.
- Cancellation and no-show both release the segment and trigger earliest-eligible waitlist promotion. Waitlist positions remain monotonic across cancellations and promotions.
- Vehicle reassignment validates that the vehicle is active and has capacity at least equal to the trip capacity. Existing seat numbers remain stable because seats belong to the trip, not the vehicle.
- Booking, cancellation, no-show, status changes, and trip-position changes evict availability cache entries. Redis cache operation failures are logged and fall back to the database path.
- `/actuator/health`, `/actuator/metrics`, and `/actuator/prometheus` expose health and monitoring data. Custom counters include `shuttle.booking.confirmed`, `shuttle.booking.waitlisted`, `shuttle.booking.cancelled`, and `shuttle.booking.no_show`.

### Recovery and trade-offs

- PostgreSQL is the source of truth; Redis is an optimization layer and may be restarted or flushed without data loss. Use the PostgreSQL backup policy for recovery: daily full backups plus WAL/point-in-time recovery in production.
- Pessimistic seat-row locking favors correctness over maximum write throughput. Availability uses an `O(S × B)` scan, which is simple and reliable for normal shuttle capacities; larger fleets should add an interval index or precomputed segment bitmap.
- Waitlist promotion selects the earliest waiting entry whose requested interval fits a currently available seat. This preserves fairness without blocking unrelated segments behind an incompatible request.

---

## 🚀 Quick Start Guide

### Prerequisites
- Docker & Docker Compose **OR**
- Java 17+ and Maven 3.8+ with local PostgreSQL & Redis instances

### 1. Running with Docker Compose (Recommended)
```bash
docker compose up --build
```
This spins up:
- PostgreSQL on port `5432`
- Redis on port `6379`
- Spring Boot App on port `8080`

### 2. Running Locally with Maven
```bash
# For quick local development with H2 + in-memory cache
mvn clean test
$env:SPRING_PROFILES_ACTIVE='local'; mvn spring-boot:run -DskipTests
# app will start on http://localhost:9090

# Or, if you want to run against PostgreSQL + Redis locally:
# Ensure PostgreSQL is running on localhost:5432 (database: office_shuttle)
# Ensure Redis is running on localhost:6379
# $env:SPRING_PROFILES_ACTIVE='prod'; mvn spring-boot:run -DskipTests
# app will start on http://localhost:8080
```

### 3. Interactive Swagger Documentation
Open your browser and navigate to:
```
http://localhost:8080/swagger-ui.html
```

---

## 🧪 Testing Suite

Run all unit, integration, and multi-threaded concurrency tests:
```bash
mvn test
```

### Tests Included:
1. `OverlapDetectorTest`: Unit tests verifying segment interval algebra (touching, disjoint, subset, enclosing, multiple intervals).
2. `BookingServiceTest`: Unit tests using Mockito verifying segment validation, duplicate prevention, seat reuse, and waitlist fallback.
3. `ConcurrencyTest`: Multi-threaded integration test firing 10 concurrent requests at 1 remaining seat to verify pessimistic locking and zero double-booking.
