# Office Shuttle -- Segment-Based Seat Booking System

A production-oriented backend case study for an office shuttle system
where a single physical seat can be reused by multiple passengers on
different, non-overlapping route segments.

> **Case Study Basis:** LPU Backend Case Studies 2026\
> **Core Challenge:** Segment-based seat availability, interval overlap
> detection, concurrency-safe booking, waitlist management, cancellation
> and efficient seat reuse.

------------------------------------------------------------------------

## Table of Contents

1.  [Project Overview](#1-project-overview)
2.  [Problem Statement](#2-problem-statement)
3.  [Why This Is Different From Normal Bus
    Booking](#3-why-this-is-different-from-normal-bus-booking)
4.  [Core Concept](#4-core-concept)
5.  [Functional Requirements](#5-functional-requirements)
6.  [Business Rules](#6-business-rules)
7.  [End-to-End Examples](#7-end-to-end-examples)
8.  [System Architecture](#8-system-architecture)
9.  [Recommended Technology Stack](#9-recommended-technology-stack)
10. [Database Design](#10-database-design)
11. [ER Model](#11-er-model)
12. [Domain Model / OOP Design](#12-domain-model--oop-design)
13. [API Design](#13-api-design)
14. [Booking Algorithm](#14-booking-algorithm)
15. [Overlap Detection](#15-overlap-detection)
16. [Concurrency and Last-Seat Race](#16-concurrency-and-last-seat-race)
17. [Waitlist and Cancellation](#17-waitlist-and-cancellation)
18. [Caching Strategy](#18-caching-strategy)
19. [Error and Exception Handling](#19-error-and-exception-handling)
20. [Authentication and
    Authorization](#20-authentication-and-authorization)
21. [Monitoring and Logging](#21-monitoring-and-logging)
22. [Failure Handling and Recovery](#22-failure-handling-and-recovery)
23. [Time and Space Complexity](#23-time-and-space-complexity)
24. [System Design Trade-offs](#24-system-design-trade-offs)
25. [Validation Rules](#25-validation-rules)
26. [Edge Cases](#26-edge-cases)
27. [Testing Strategy](#27-testing-strategy)
28. [Postman / cURL Examples](#28-postman--curl-examples)
29. [Suggested Project Structure](#29-suggested-project-structure)
30. [Implementation Roadmap](#30-implementation-roadmap)
31. [Local Setup](#31-local-setup)
32. [Environment Variables](#32-environment-variables)
33. [Docker](#33-docker)
34. [Git and Commit Strategy](#34-git-and-commit-strategy)
35. [README / Submission Checklist](#35-readme--submission-checklist)
36. [Demo Requirements](#36-demo-requirements)
37. [Interview / Viva Questions](#37-interview--viva-questions)
38. [Final Understanding](#38-final-understanding)

------------------------------------------------------------------------

# 1. Project Overview

The Office Shuttle Segment-Based Seat Booking System is a backend
application for managing shuttle routes, stops, trips, seats and
passenger bookings.

The key feature is **segment-based seat reuse**.

Consider a shuttle route:

``` text
A → B → C → D
```

If Seat #5 is booked by Passenger 1 for:

``` text
A → B
```

the same physical Seat #5 can be booked by Passenger 2 for:

``` text
B → D
```

because the two passengers do not occupy the seat on the same road
segment.

However, if Seat #5 already has:

``` text
A → C
```

then a new:

``` text
B → D
```

request cannot use Seat #5 because the journeys overlap between B and C.

The central problem is therefore an **interval/segment overlap
problem**, not a simple "seat sold/not sold" problem.

------------------------------------------------------------------------

# 2. Problem Statement

An office shuttle runs on a fixed route with several ordered stops and
fixed timings.

Example:

``` text
A → B → C → D

A = 10:00
B = 10:15
C = 10:30
D = 10:45
```

A trip has a fixed number of physical seats, for example:

``` text
Capacity = 40
```

Employees can request a journey from one stop to a later stop.

The system must:

-   Manage routes and ordered stops.
-   Manage trips operating on a particular day.
-   Maintain physical seats.
-   Accept segment-based booking requests.
-   Determine whether a seat is available for the requested segment.
-   Reuse the same seat for non-overlapping segments.
-   Prevent overlapping bookings on the same seat.
-   Handle simultaneous booking requests correctly.
-   Maintain a fair waitlist.
-   Promote eligible waitlisted passengers after cancellation.
-   Handle no-show and mid-route boarding edge cases.
-   Provide efficient availability checks.
-   Provide robust authentication, error handling, monitoring, caching
    and failure recovery.
-   Document complexity and system design trade-offs.

------------------------------------------------------------------------

# 3. Why This Is Different From Normal Bus Booking

A normal bus booking system often treats a seat as:

``` text
Seat 5 = AVAILABLE
Seat 5 = BOOKED
```

That approach is insufficient here.

The same seat may be:

``` text
A → B     Passenger 1
B → D     Passenger 2
```

at the same time on the overall trip but not at the same time
physically.

Therefore the real state is closer to:

``` text
Seat 5
 ├── A → B
 ├── B → D
 └── D → ...
```

A seat is free for a new request only when its existing booking
intervals do not overlap with the requested interval.

------------------------------------------------------------------------

# 4. Core Concept

## 4.1 Route

A route is an ordered sequence of stops.

``` text
A → B → C → D
```

## 4.2 Stop

Each stop has:

-   ID
-   name
-   sequence/order
-   arrival time

Example:

    Sequence Stop   Time
  ---------- ------ -------
           1 A      10:00
           2 B      10:15
           3 C      10:30
           4 D      10:45

## 4.3 Trip

A trip represents a route operating on a particular date.

Example:

``` text
Route: Office Route 1
Date: 2026-10-01
Capacity: 40
```

## 4.4 Seat

A trip contains physical seats.

``` text
Seat 1
Seat 2
...
Seat 40
```

## 4.5 Booking

A booking contains:

``` text
Passenger
Trip
Seat
Source Stop
Destination Stop
Status
```

The important part is the source/destination segment.

------------------------------------------------------------------------

# 5. Functional Requirements

## 5.1 Authentication

Implement secure user authentication.

Suggested functionality:

-   Register
-   Login
-   Password hashing
-   Token/session generation
-   Authentication middleware
-   Role-based authorization if needed

------------------------------------------------------------------------

## 5.2 Route Management

Admins/operators should be able to:

-   Create routes.
-   Add ordered stops.
-   Update route information.
-   View route details.
-   Validate stop order.

Example:

``` text
Route: R1

1. Office
2. Stop B
3. Stop C
4. Campus
```

------------------------------------------------------------------------

## 5.3 Trip Management

A trip should contain:

-   Route
-   Date
-   Seat capacity
-   Status

Possible statuses:

``` text
SCHEDULED
ACTIVE
COMPLETED
CANCELLED
```

------------------------------------------------------------------------

## 5.4 Seat Management

For each trip:

``` text
Seat 1
Seat 2
Seat 3
...
Seat N
```

The system must track bookings associated with each seat.

------------------------------------------------------------------------

## 5.5 Segment Availability

The user requests:

``` text
From: B
To: D
```

The system must determine which physical seats can accommodate B→D.

This is the core functionality.

------------------------------------------------------------------------

## 5.6 Booking

The booking process should:

1.  Validate the user.
2.  Validate the trip.
3.  Validate source/destination.
4.  Find a usable seat.
5.  Check interval conflicts.
6.  Atomically reserve the seat.
7.  Create the booking.
8.  Return booking details.

------------------------------------------------------------------------

## 5.7 Cancellation

A passenger can cancel an active booking according to the system rules.

Cancellation should:

1.  Mark/release the booking.
2.  Free the corresponding segment.
3.  Trigger waitlist evaluation.
4.  Promote an eligible passenger if appropriate.

------------------------------------------------------------------------

## 5.8 Waitlist

If no suitable seat is available:

``` text
User → Waitlist
```

Maintain:

``` text
position
created_at
segment
status
```

The case study requires a fair first-come-first-served ordering.

------------------------------------------------------------------------

# 6. Business Rules

## Rule 1 --- Source must be before destination

Valid:

``` text
A → B
B → D
```

Invalid:

``` text
D → A
```

------------------------------------------------------------------------

## Rule 2 --- Same stop boundary does not create overlap

Valid:

``` text
A → B
B → D
```

These segments touch at B but do not overlap.

------------------------------------------------------------------------

## Rule 3 --- Actual overlap is not allowed

Invalid:

``` text
A → C
B → D
```

because:

``` text
B → C
```

is shared.

------------------------------------------------------------------------

## Rule 4 --- Availability is segment-based

Do not calculate availability simply as:

``` text
capacity - totalBookings
```

That would be incorrect.

Instead determine whether each physical seat can accommodate the
requested segment.

------------------------------------------------------------------------

## Rule 5 --- Concurrent booking must be atomic

If two users request the same last usable seat for overlapping segments
at the same time:

``` text
User A → success
User B → failure / waitlist
```

Both must not succeed.

------------------------------------------------------------------------

## Rule 6 --- Waitlist must be fair

Default rule:

``` text
First Come → First Served
```

However, a waitlisted passenger must also be compatible with the newly
available seat segment.

------------------------------------------------------------------------

# 7. End-to-End Examples

## Example 1 --- Non-overlapping bookings

Existing:

``` text
Seat 5 → A → B
```

New:

``` text
B → D
```

Result:

``` text
ALLOW
```

Seat 5 can be reused.

------------------------------------------------------------------------

## Example 2 --- Overlapping bookings

Existing:

``` text
Seat 5 → A → C
```

New:

``` text
B → D
```

Result:

``` text
REJECT Seat 5
```

Try another seat.

------------------------------------------------------------------------

## Example 3 --- Touching boundary

Existing:

``` text
Seat 6 → A → B
```

New:

``` text
B → C
```

Result:

``` text
ALLOW
```

Because they only meet at B.

------------------------------------------------------------------------

## Example 4 --- Multiple intervals

Seat 7:

``` text
A → B
C → D
```

New:

``` text
B → C
```

This is compatible with both existing intervals and can be assigned to
Seat 7.

------------------------------------------------------------------------

# 8. System Architecture

Recommended layered architecture:

``` text
Client
  |
  v
REST API
  |
  v
Controller Layer
  |
  v
Service Layer
  |
  +------------------+
  |                  |
  v                  v
Repository        Cache
  |
  v
Database
```

Cross-cutting components:

``` text
Authentication
Validation
Exception Handler
Logging
Monitoring
Transaction Management
```

------------------------------------------------------------------------

# 9. Recommended Technology Stack

The original case study prefers an object-oriented language such as
Java, C# or Python.

A strong implementation choice is:

### Backend

``` text
Java
Spring Boot
Spring Web
Spring Data JPA
Spring Security
```

### Database

``` text
PostgreSQL
```

### Cache

``` text
Redis
```

### Testing

``` text
JUnit
Mockito
Spring Boot Test
```

### API Testing

``` text
Postman
cURL
```

### Containerization

``` text
Docker
Docker Compose
```

### Version Control

``` text
Git
GitHub
```

The exact technology is a design choice. The important part is that the
architecture demonstrates the required backend concepts.

------------------------------------------------------------------------

# 10. Database Design

The following is a suggested schema. The original case study does not
prescribe exact table names, so these are implementation
recommendations.

## users

``` text
id
name
email
password_hash
role
created_at
updated_at
```

## routes

``` text
id
name
description
created_at
updated_at
```

## stops

``` text
id
route_id
name
sequence
arrival_time
```

## trips

``` text
id
route_id
trip_date
capacity
status
created_at
updated_at
```

## seats

``` text
id
trip_id
seat_number
```

## bookings

``` text
id
trip_id
seat_id
user_id
from_stop_id
to_stop_id
status
created_at
updated_at
```

## waitlist

``` text
id
trip_id
user_id
from_stop_id
to_stop_id
position
status
created_at
```

------------------------------------------------------------------------

# 11. ER Model

Conceptually:

``` text
USER
 |
 | 1:N
 v
BOOKING
 |       \
 |        \
 v         v
TRIP ----> SEAT
 |
 v
ROUTE
 |
 v
STOP
```

Waitlist:

``` text
USER
 |
 v
WAITLIST
 |
 v
TRIP
```

A more detailed relationship view:

``` text
Route 1 ───── N Stop

Route 1 ───── N Trip

Trip 1 ───── N Seat

Trip 1 ───── N Booking

User 1 ───── N Booking

Trip 1 ───── N WaitlistEntry

User 1 ───── N WaitlistEntry
```

------------------------------------------------------------------------

# 12. Domain Model / OOP Design

Suggested classes:

``` text
User
Route
Stop
Trip
Seat
Booking
WaitlistEntry
```

Services:

``` text
AuthenticationService
RouteService
TripService
AvailabilityService
BookingService
WaitlistService
```

Repositories:

``` text
UserRepository
RouteRepository
StopRepository
TripRepository
SeatRepository
BookingRepository
WaitlistRepository
```

Controllers:

``` text
AuthController
RouteController
TripController
BookingController
WaitlistController
```

Use OOP principles meaningfully:

### Encapsulation

Keep booking state and validation inside appropriate domain/service
boundaries.

### Abstraction

Expose service interfaces instead of making controllers contain business
logic.

### Polymorphism

Can be used where multiple allocation strategies or notification
strategies are needed.

### Inheritance

Use only where there is a genuine "is-a" relationship. Do not add
inheritance just to satisfy a checklist.

------------------------------------------------------------------------

# 13. API Design

These are suggested APIs.

## Authentication

### POST /api/auth/register

Request:

``` json
{
  "name": "Abhinav",
  "email": "user@example.com",
  "password": "StrongPassword123"
}
```

### POST /api/auth/login

Request:

``` json
{
  "email": "user@example.com",
  "password": "StrongPassword123"
}
```

------------------------------------------------------------------------

## Routes

### POST /api/routes

``` json
{
  "name": "Office Route 1"
}
```

### POST /api/routes/{routeId}/stops

``` json
{
  "name": "B",
  "sequence": 2,
  "arrivalTime": "10:15"
}
```

### GET /api/routes/{routeId}

Returns route and ordered stops.

------------------------------------------------------------------------

## Trips

### POST /api/trips

``` json
{
  "routeId": 1,
  "tripDate": "2026-10-01",
  "capacity": 40
}
```

------------------------------------------------------------------------

## Availability

### GET /api/trips/{tripId}/availability?from=B&to=D

Example response:

``` json
{
  "tripId": 101,
  "from": "B",
  "to": "D",
  "availableSeats": [5, 7, 9],
  "availableCount": 3
}
```

------------------------------------------------------------------------

## Booking

### POST /api/trips/{tripId}/bookings

``` json
{
  "fromStop": "B",
  "toStop": "D"
}
```

Example success:

``` json
{
  "bookingId": 501,
  "seatNumber": 5,
  "fromStop": "B",
  "toStop": "D",
  "status": "CONFIRMED"
}
```

------------------------------------------------------------------------

## Cancellation

### DELETE /api/bookings/{bookingId}

Example:

``` json
{
  "bookingId": 501,
  "status": "CANCELLED"
}
```

------------------------------------------------------------------------

## Waitlist

### POST /api/trips/{tripId}/waitlist

``` json
{
  "fromStop": "B",
  "toStop": "D"
}
```

------------------------------------------------------------------------

# 14. Booking Algorithm

High-level flow:

``` text
Receive booking request
        |
        v
Authenticate user
        |
        v
Validate trip
        |
        v
Validate source/destination
        |
        v
Find candidate seats
        |
        v
Check existing intervals
        |
        +---- conflict ----> Try next seat
        |
        +---- no conflict --> Reserve seat
                                |
                                v
                           Create booking
                                |
                                v
                              Commit
```

Pseudo-code:

``` text
book(trip, user, from, to):

    validate(trip)
    validateSegment(from, to)

    begin transaction

    seats = findCandidateSeats(trip)

    for seat in seats:
        lock/check seat allocation

        bookings = getActiveBookings(seat)

        if noOverlap(bookings, from, to):
            create booking
            commit
            return success

    rollback

    add user to waitlist / return unavailable
```

------------------------------------------------------------------------

# 15. Overlap Detection

The case study defines the non-overlap condition as:

``` text
Yd <= Xs OR Ys >= Xd
```

Where:

``` text
Existing = [Xs, Xd]
Requested = [Ys, Yd]
```

If this condition is true, the intervals do not overlap.

Otherwise they overlap.

Equivalent overlap logic:

``` text
overlap =
    requestedStart < existingEnd
    AND
    requestedEnd > existingStart
```

Use the route stop sequence/index rather than relying only on
human-readable stop names.

Example:

``` text
A = 1
B = 2
C = 3
D = 4
```

Existing:

``` text
A → C
[1, 3]
```

Requested:

``` text
B → D
[2, 4]
```

Check:

``` text
2 < 3  -> true
4 > 1  -> true
```

Therefore:

``` text
OVERLAP
```

------------------------------------------------------------------------

# 16. Concurrency and Last-Seat Race

This is one of the most important parts of the case study.

## Problem

Suppose only Seat 5 can serve:

``` text
B → D
```

At the same time:

``` text
User A → B→D
User B → B→D
```

If both transactions do:

``` text
1. Read availability
2. See Seat 5
3. Insert booking
```

both may succeed incorrectly.

------------------------------------------------------------------------

## Required solution

Make the critical operation atomic.

Possible approaches include:

-   Database transactions.
-   Row-level locking.
-   Pessimistic locking.
-   Appropriate isolation levels.
-   Unique constraints where applicable.
-   Serializable/controlled allocation where justified.

The exact strategy should be documented in the project.

The important invariant is:

> Two overlapping active bookings must never successfully occupy the
> same physical seat.

------------------------------------------------------------------------

# 17. Waitlist and Cancellation

## Joining waitlist

If no seat is available:

``` text
Booking attempt
      |
      v
No compatible seat
      |
      v
Create waitlist entry
      |
      v
Assign position
```

Example:

``` text
1. User A
2. User B
3. User C
```

------------------------------------------------------------------------

## Cancellation

Suppose:

``` text
Seat 5 → A→C
```

is cancelled.

Now the system must inspect waitlisted requests.

Example:

``` text
Waitlist:
1. User X → A→D
2. User Y → C→D
3. User Z → A→B
```

The system must determine which request can fit into the freed seat
considering all remaining bookings.

The promotion policy should be documented.

The case study specifically asks to explain how the system chooses among
multiple eligible passengers.

------------------------------------------------------------------------

# 18. Caching Strategy

Caching is a plus point in the case study.

Good candidates:

``` text
Route details
Ordered stops
Trip metadata
Frequently requested availability
```

Example:

``` text
GET availability(B,D)
        |
        v
      Redis
    /       \
 HIT         MISS
 |             |
Return       Database
                |
                v
              Redis
```

Important:

Availability becomes stale when:

-   Booking occurs.
-   Cancellation occurs.
-   Trip changes.
-   Seat allocation changes.

Therefore cache invalidation/update must be handled carefully.

Possible eviction policy:

``` text
TTL
LRU
```

Choose and document the strategy.

------------------------------------------------------------------------

# 19. Error and Exception Handling

Use centralized error handling.

Suggested error responses:

``` json
{
  "timestamp": "2026-10-01T10:15:00Z",
  "status": 409,
  "error": "SEAT_UNAVAILABLE",
  "message": "No seat is available for the requested segment."
}
```

Suggested exceptions:

``` text
UserNotFoundException
TripNotFoundException
RouteNotFoundException
InvalidSegmentException
SeatUnavailableException
BookingNotFoundException
DuplicateBookingException
UnauthorizedException
```

HTTP examples:

``` text
400 → Invalid request
401 → Authentication required
403 → Forbidden
404 → Resource not found
409 → Booking conflict
500 → Unexpected server error
```

Do not expose sensitive internal stack traces to API clients.

------------------------------------------------------------------------

# 20. Authentication and Authorization

Minimum authentication flow:

``` text
Register
   |
Password Hash
   |
Login
   |
Verify credentials
   |
Issue token/session
   |
Authenticated API access
```

Suggested roles:

``` text
ADMIN
EMPLOYEE
```

Possible authorization:

``` text
ADMIN
- Create routes
- Configure trips
- Manage capacity

EMPLOYEE
- View trips
- Check availability
- Book
- Cancel
- Join waitlist
```

------------------------------------------------------------------------

# 21. Monitoring and Logging

The case study explicitly expects monitoring.

Useful metrics:

``` text
Request count
Request latency
Booking success count
Booking conflict count
Waitlist count
Cancellation count
Database latency
Cache hit/miss ratio
Exception count
```

Logs should include useful context:

``` text
requestId
userId
tripId
bookingId
operation
status
latency
errorCode
```

Example:

``` text
INFO booking.request trip=101 user=25 from=B to=D
INFO booking.success trip=101 seat=5 booking=501
WARN booking.conflict trip=101 requested=B-D
```

Avoid logging passwords, tokens or sensitive credentials.

------------------------------------------------------------------------

# 22. Failure Handling and Recovery

The case study asks for fault-tolerant mechanisms, backup/recovery and
error recovery procedures.

Consider:

## Database failure

Application should return a controlled error rather than corrupting
booking state.

## Transaction failure

Rollback partial operations.

Example:

``` text
Seat selected
    ↓
Booking insert fails
    ↓
Transaction rollback
    ↓
Seat remains available
```

## Cache failure

The application should ideally continue using the database if the cache
is unavailable.

## Restart recovery

Persist authoritative booking data in the database rather than relying
on in-memory state.

## Backup

Document a database backup strategy appropriate to the deployment
environment.

------------------------------------------------------------------------

# 23. Time and Space Complexity

The case study explicitly asks for complexity analysis and efficient
algorithms/data structures.

A simple implementation might do:

``` text
For every seat:
    For every booking on that seat:
        check overlap
```

If there are:

``` text
S = number of seats
B = bookings
```

this can become expensive under load.

Possible optimization ideas mentioned by the case study include:

-   Sorting bookings by stop.
-   Interval-based structures.
-   Segment trees.
-   Other indexed representations.

The README should state the actual complexity of the implemented
solution.

Do not claim an optimized complexity unless the implementation really
provides it.

------------------------------------------------------------------------

# 24. System Design Trade-offs

Document why you made important choices.

Examples:

## PostgreSQL vs in-memory map

PostgreSQL provides durability and transactional consistency.

## Redis vs database-only

Redis can reduce repeated reads, but introduces cache invalidation
complexity.

## Pessimistic locking vs optimistic concurrency

Pessimistic locking can simplify preventing simultaneous allocation but
may reduce concurrency under contention.

Optimistic approaches can improve concurrency but require conflict
detection/retry.

## Simple scan vs interval data structure

Simple scanning is easier to implement and explain.

Advanced interval structures can improve performance but increase
implementation complexity.

The goal is not to claim one universal "best" solution. Explain the
trade-off for the workload you designed for.

------------------------------------------------------------------------

# 25. Validation Rules

Suggested validations:

### Route

``` text
Route name required
At least two stops
Stop sequence unique
```

### Stop

``` text
Name required
Sequence positive
Arrival time valid
```

### Trip

``` text
Route exists
Date valid
Capacity > 0
```

### Booking

``` text
Trip exists
User authenticated
Source exists
Destination exists
Source sequence < destination sequence
Trip active/scheduled
```

### Cancellation

``` text
Booking exists
Booking belongs to requesting user or authorized admin
Booking is cancellable
```

------------------------------------------------------------------------

# 26. Edge Cases

The original case study specifically calls attention to several edge
cases.

Test at least:

1.  Empty data.
2.  Invalid input.
3.  Duplicate requests.
4.  Source after destination.
5.  Same source and destination.
6.  A→B followed by B→D.
7.  A→C followed by B→D.
8.  Multiple bookings on one seat.
9.  No seats available.
10. Two simultaneous last-seat requests.
11. Cancellation while waitlist promotion occurs.
12. No-show.
13. Mid-route boarding.
14. Trip moved to another bus overnight.
15. Invalid trip ID.
16. Invalid seat ID.
17. Cancelled trip.
18. Already cancelled booking.
19. Repeated cancellation.
20. Cache unavailable.
21. Database temporarily unavailable.

------------------------------------------------------------------------

# 27. Testing Strategy

## Unit Testing

Important unit tests:

``` text
testNonOverlappingSegments()
testOverlappingSegments()
testTouchingSegments()
testInvalidSegment()
testSeatSelection()
testWaitlistOrdering()
```

------------------------------------------------------------------------

## Integration Testing

Test:

``` text
API → Service → Repository → Database
```

Examples:

``` text
Create route
Create trip
Book segment
Check availability
Cancel booking
Promote waitlist
```

------------------------------------------------------------------------

## Concurrency Testing

This is especially important.

Run multiple requests simultaneously:

``` text
10 users
1 last available seat
same segment
```

Expected:

``` text
1 confirmed
9 rejected/waitlisted
```

The exact outcome depends on the business rule for unavailable requests,
but the key invariant is that overlapping bookings cannot all receive
the same physical seat.

------------------------------------------------------------------------

# 28. Postman / cURL Examples

## Login

``` bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "StrongPassword123"
  }'
```

------------------------------------------------------------------------

## Availability

``` bash
curl "http://localhost:8080/api/trips/101/availability?from=B&to=D" \
  -H "Authorization: Bearer <TOKEN>"
```

------------------------------------------------------------------------

## Booking

``` bash
curl -X POST http://localhost:8080/api/trips/101/bookings \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "fromStop": "B",
    "toStop": "D"
  }'
```

------------------------------------------------------------------------

## Cancellation

``` bash
curl -X DELETE http://localhost:8080/api/bookings/501 \
  -H "Authorization: Bearer <TOKEN>"
```

------------------------------------------------------------------------

# 29. Suggested Project Structure

For Java/Spring Boot:

``` text
office-shuttle-booking/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/shuttle/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       ├── entity/
│   │   │       ├── dto/
│   │   │       ├── exception/
│   │   │       ├── security/
│   │   │       ├── config/
│   │   │       └── util/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │
│   └── test/
│       └── java/
│
├── docker/
│
├── docs/
│   ├── architecture.md
│   ├── api.md
│   └── er-diagram.png
│
├── postman/
│   └── office-shuttle.postman_collection.json
│
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

------------------------------------------------------------------------

# 30. Implementation Roadmap

## Phase 1 --- Project Setup

-   Create Spring Boot project.
-   Configure PostgreSQL.
-   Configure JPA.
-   Configure migrations if used.
-   Create basic package structure.

## Phase 2 --- Database

Create:

``` text
users
routes
stops
trips
seats
bookings
waitlist
```

## Phase 3 --- Authentication

Implement:

``` text
register
login
password hashing
authentication
authorization
```

## Phase 4 --- Route and Stops

Implement:

``` text
create route
add stops
get route
validate stop order
```

## Phase 5 --- Trips

Implement:

``` text
create trip
list trips
trip details
capacity
```

## Phase 6 --- Booking

Implement:

``` text
availability
overlap detection
seat allocation
booking persistence
```

## Phase 7 --- Concurrency

Implement and test:

``` text
transaction
locking/concurrency strategy
last-seat race protection
```

## Phase 8 --- Cancellation + Waitlist

Implement:

``` text
cancel
free segment
find eligible waitlist users
promote
```

## Phase 9 --- Reliability

Add:

``` text
exception handling
logging
monitoring
failure handling
```

## Phase 10 --- Performance

Add:

``` text
indexes
caching
efficient interval lookup
```

## Phase 11 --- Testing

Add:

``` text
unit tests
integration tests
concurrency tests
edge-case tests
```

## Phase 12 --- Submission

Prepare:

``` text
README
ER diagram
architecture diagram
Postman collection
screenshots
demo video
GitHub repository
meaningful commit history
```

------------------------------------------------------------------------

# 31. Local Setup

## Prerequisites

Install:

``` text
Java 17+
Maven
PostgreSQL
Git
Docker (optional)
```

If Redis is used:

``` text
Redis
```

------------------------------------------------------------------------

## Clone

``` bash
git clone <your-github-repository-url>
cd office-shuttle-booking
```

------------------------------------------------------------------------

## Configure Database

Example:

``` text
Database: office_shuttle
Username: postgres
Password: your_password
Host: localhost
Port: 5432
```

------------------------------------------------------------------------

## Run Application

``` bash
./mvnw spring-boot:run
```

Windows:

``` bash
mvnw.cmd spring-boot:run
```

------------------------------------------------------------------------

# 32. Environment Variables

Do not hard-code secrets.

Example:

``` env
DB_URL=jdbc:postgresql://localhost:5432/office_shuttle
DB_USERNAME=postgres
DB_PASSWORD=your_password

JWT_SECRET=change_me

REDIS_HOST=localhost
REDIS_PORT=6379
```

Never commit real credentials to GitHub.

------------------------------------------------------------------------

# 33. Docker

Suggested services:

``` text
application
postgres
redis
```

Conceptually:

``` text
Docker Compose
    |
    +--- Backend
    |
    +--- PostgreSQL
    |
    +--- Redis
```

Build:

``` bash
docker compose build
```

Run:

``` bash
docker compose up
```

Stop:

``` bash
docker compose down
```

Use persistent database volumes so database state does not disappear
unexpectedly.

------------------------------------------------------------------------

# 34. Git and Commit Strategy

The case study explicitly asks for a meaningful commit history.

Do NOT do:

``` text
git add .
git commit -m "everything"
```

Prefer incremental commits:

``` text
Initial Spring Boot project setup
Add database entities
Add authentication
Add route and stop APIs
Add trip management
Implement segment overlap logic
Implement seat availability
Add atomic booking transaction
Add cancellation and waitlist
Add Redis caching
Add exception handling
Add tests
Add Docker configuration
Update README and API documentation
```

This demonstrates development progression.

------------------------------------------------------------------------

# 35. README / Submission Checklist

Before submitting, verify:

## Core

-   [ ] Authentication
-   [ ] Routes
-   [ ] Stops
-   [ ] Trips
-   [ ] Seats
-   [ ] Segment booking
-   [ ] Correct overlap detection
-   [ ] Cancellation
-   [ ] Waitlist

## System Quality

-   [ ] OOP
-   [ ] Time complexity analysis
-   [ ] Space complexity analysis
-   [ ] Concurrency handling
-   [ ] Failure handling
-   [ ] Error handling
-   [ ] Monitoring/logging
-   [ ] Caching
-   [ ] Trade-off documentation

## Engineering

-   [ ] Clean controller/service/repository layers
-   [ ] Validation
-   [ ] Unit tests
-   [ ] Integration tests
-   [ ] Concurrency tests
-   [ ] API documentation
-   [ ] ER diagram
-   [ ] Architecture diagram

## Submission

-   [ ] Public GitHub repository
-   [ ] Meaningful commit history
-   [ ] README
-   [ ] Postman/cURL examples
-   [ ] Demo video or screenshots

------------------------------------------------------------------------

# 36. Demo Requirements

The case study asks for a short demo video or screenshots showing the
main features and sample API calls.

Recommended demo flow:

### Demo 1 --- Authentication

``` text
Register
↓
Login
↓
Receive token
```

### Demo 2 --- Route

``` text
Create A→B→C→D route
```

### Demo 3 --- Trip

``` text
Create trip
Capacity = 3
```

### Demo 4 --- First booking

``` text
User A
A→B
Seat 1
```

### Demo 5 --- Seat reuse

``` text
User B
B→D
Seat 1
```

Show that this is allowed.

### Demo 6 --- Overlap

``` text
User C
A→C
```

Show that Seat 1 cannot be used because it conflicts with A→B or B→D as
applicable.

### Demo 7 --- Waitlist

Fill the available segments and show a user entering the waitlist.

### Demo 8 --- Cancellation

Cancel a booking and show eligible waitlist promotion.

### Demo 9 --- Concurrency

Show simultaneous booking requests for the last usable seat and
demonstrate that the system does not create two overlapping allocations.

------------------------------------------------------------------------

# 37. Interview / Viva Questions

## Q1. Why can't you use a simple `isBooked` boolean?

Because a physical seat can be reused for different non-overlapping
route segments.

------------------------------------------------------------------------

## Q2. What is the central algorithmic problem?

Interval/segment overlap detection.

------------------------------------------------------------------------

## Q3. Why are A→B and B→D allowed?

They only touch at B and do not overlap on the road segment.

------------------------------------------------------------------------

## Q4. Why is A→C and B→D invalid on the same seat?

Both passengers occupy the seat during B→C.

------------------------------------------------------------------------

## Q5. Why is a total `seatsSold` count incorrect?

Because availability depends on the requested segment, not on the total
number of bookings for the trip.

------------------------------------------------------------------------

## Q6. What is the last-seat race?

Two concurrent requests both observe the same last available seat and
may both attempt to reserve it.

------------------------------------------------------------------------

## Q7. How do you prevent it?

Use an atomic transactional allocation strategy with suitable database
locking/concurrency control.

------------------------------------------------------------------------

## Q8. What happens after cancellation?

The released segment is evaluated against the waitlist and an eligible
passenger can be promoted according to the documented policy.

------------------------------------------------------------------------

## Q9. Why use Redis?

To reduce repeated database reads for frequently requested data such as
route/trip/availability information, while carefully handling
invalidation.

------------------------------------------------------------------------

## Q10. What is the biggest trade-off?

A simple booking scan is easier to build but may become expensive at
scale; more advanced indexed interval structures can improve performance
at the cost of complexity.

------------------------------------------------------------------------

## Q11. Why use transactions?

Booking changes multiple pieces of state and must remain consistent if
something fails.

------------------------------------------------------------------------

## Q12. What should happen if Redis goes down?

The database should remain the source of truth and the application
should have a controlled fallback path where appropriate.

------------------------------------------------------------------------

# 38. Final Understanding

The entire case study can be reduced to this mental model:

``` text
User asks:

"I want B → D on Trip 101."
             |
             v
     Validate request
             |
             v
     Find candidate seats
             |
             v
   Check each seat's intervals
             |
       +-----+-----+
       |           |
    Conflict     Free
       |           |
   Next seat    Reserve
                   |
                   v
              Create booking
```

If every seat conflicts:

``` text
No compatible seat
       |
       v
   Waitlist
```

If a booking is cancelled:

``` text
Cancellation
     |
     v
Seat segment becomes free
     |
     v
Check waitlist
     |
     v
Find eligible passenger
     |
     v
Promote according to policy
```

For concurrent requests:

``` text
User A ──┐
         ├──> Atomic allocation ──> Only valid winner
User B ──┘
```

The central rule is:

> **A seat is available for a requested segment only when none of its
> existing bookings overlap that segment.**

Everything else---database design, APIs, transactions, waitlist,
caching, monitoring and testing---is built around making that rule
**correct, efficient, reliable and maintainable**.

------------------------------------------------------------------------

## Original Case Study Alignment

The original LPU case study asks for robust authentication, time/space
complexity analysis, fault tolerance and recovery, OOP, design
trade-offs, monitoring, caching, and error handling. It also requires
clean layering, edge-case testing, unit tests, README documentation, a
public GitHub repository with meaningful commits, and a demo containing
main features/API calls.

The case study's Office Shuttle section specifically requires:

-   Ordered routes and stops.
-   Fixed-capacity trips.
-   Segment-based seat availability.
-   Correct overlap handling.
-   Seat reuse across non-overlapping segments.
-   Efficient availability.
-   Atomic booking under concurrent load.
-   Segment-level counting rather than route-wide seat counting.
-   FIFO waitlist.
-   Smart waitlist promotion after cancellation.
-   Correct handling of interval boundaries.
-   Consideration of efficient interval data structures.
-   Concurrency protection.
-   At least no-show and mid-route boarding edge cases.

------------------------------------------------------------------------

## Project Goal

Build a backend that demonstrates not just CRUD APIs, but **real backend
engineering**:

``` text
Correctness
    +
Concurrency
    +
Performance
    +
Reliability
    +
Security
    +
Maintainability
```

That is the core expectation of this case study.
