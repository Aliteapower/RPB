# Backend Snapshot and Time Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add tested, backward-compatible typed idempotency snapshot codecs and a fixed-clock Store business-time capability without changing or rewiring the existing seating workflows yet.

**Architecture:** The idempotency module owns a small generic codec contract and a Jackson implementation. Reservation, Queue, and Walk-in each own an immutable snapshot record plus a thin Spring codec that preserves the current JSON field names. The Store module owns business-date derivation from an injected `Clock` and `Store.timezone()`.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Jackson, JUnit 5, AssertJ, Maven.

## Global Constraints

- Do not change any existing API path, request shape, response shape, HTTP status, or stable error code.
- Do not add or modify database schema, migrations, indexes, constraints, or stored data.
- Do not change Reservation, QueueTicket, WalkIn, Seating, Cleaning, DiningTable, or TableGroup state-machine transitions.
- Preserve App Gate checks, tenant isolation, store isolation, idempotency behaviour, audit records, business events, transition logs, and transaction boundaries.
- Preserve existing frontend routes, query parameters, visible workflow order, stable selectors relied on by current validation, and i18n keys.
- Use the PostgreSQL runtime referenced by `target/local-postgres-current.txt` for any later local runtime or database validation.
- Perform one capability extraction or one workflow migration at a time. Each change must be independently reviewable and reversible.
- Do not create a generic framework merely to reduce line counts. Share only behaviour that is already duplicated and stable.
- This plan must not modify the three existing seating application services. They consume these foundations in later workflow-specific plans.

---

## File Structure

Create the following focused units:

- `src/main/java/com/rpb/reservation/idempotency/application/codec/IdempotencySnapshotCodec.java` — serialization contract.
- `src/main/java/com/rpb/reservation/idempotency/application/codec/IdempotencySnapshotException.java` — one stable internal failure type for blank, malformed, or unserializable payloads.
- `src/main/java/com/rpb/reservation/idempotency/application/codec/JacksonIdempotencySnapshotCodec.java` — shared Jackson mechanics only.
- `src/main/java/com/rpb/reservation/reservation/application/snapshot/ReservationArrivedDirectSeatingSnapshot.java` — Reservation replay payload.
- `src/main/java/com/rpb/reservation/reservation/application/snapshot/ReservationArrivedDirectSeatingSnapshotCodec.java` — Reservation codec bean.
- `src/main/java/com/rpb/reservation/queue/application/snapshot/SeatingFromCalledQueueSnapshot.java` — Queue replay payload.
- `src/main/java/com/rpb/reservation/queue/application/snapshot/SeatingFromCalledQueueSnapshotCodec.java` — Queue codec bean.
- `src/main/java/com/rpb/reservation/walkin/application/snapshot/WalkInDirectSeatingSnapshot.java` — Walk-in replay payload.
- `src/main/java/com/rpb/reservation/walkin/application/snapshot/WalkInDirectSeatingSnapshotCodec.java` — Walk-in codec bean.
- `src/main/java/com/rpb/reservation/store/application/time/StoreBusinessTime.java` — injected-clock current time and Store-local business date.

Tests mirror these packages under `src/test/java` and use exact legacy JSON fixtures copied from the current services/tests.

### Task 1: Generic Idempotency Snapshot Codec

**Files:**
- Create: `src/main/java/com/rpb/reservation/idempotency/application/codec/IdempotencySnapshotCodec.java`
- Create: `src/main/java/com/rpb/reservation/idempotency/application/codec/IdempotencySnapshotException.java`
- Create: `src/main/java/com/rpb/reservation/idempotency/application/codec/JacksonIdempotencySnapshotCodec.java`
- Test: `src/test/java/com/rpb/reservation/idempotency/application/codec/JacksonIdempotencySnapshotCodecTest.java`

**Interfaces:**
- Consumes: Spring-managed `com.fasterxml.jackson.databind.ObjectMapper`.
- Produces: `String encode(T snapshot)` and `T decode(String payload)` with `IdempotencySnapshotException` on invalid input.

- [ ] **Step 1: Write the failing Jackson codec tests**

```java
package com.rpb.reservation.idempotency.application.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JacksonIdempotencySnapshotCodecTest {

    private final ExampleCodec codec = new ExampleCodec(new ObjectMapper());

    @Test
    void roundTripsRecordsUsingCompactJson() {
        ExampleSnapshot snapshot = new ExampleSnapshot(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "seated",
            List.of("occupied", "occupied")
        );

        String payload = codec.encode(snapshot);

        assertThat(payload).isEqualTo(
            "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"status\":\"seated\",\"memberStatuses\":[\"occupied\",\"occupied\"]}"
        );
        assertThat(codec.decode(payload)).isEqualTo(snapshot);
    }

    @Test
    void rejectsBlankAndMalformedPayloadsWithOneFailureType() {
        assertThatThrownBy(() -> codec.decode(" "))
            .isInstanceOf(IdempotencySnapshotException.class)
            .hasMessage("idempotency_snapshot_payload_required");
        assertThatThrownBy(() -> codec.decode("{not-json}"))
            .isInstanceOf(IdempotencySnapshotException.class)
            .hasMessage("idempotency_snapshot_decode_failed");
    }

    private record ExampleSnapshot(UUID id, String status, List<String> memberStatuses) {
    }

    private static final class ExampleCodec extends JacksonIdempotencySnapshotCodec<ExampleSnapshot> {
        private ExampleCodec(ObjectMapper objectMapper) {
            super(objectMapper, ExampleSnapshot.class);
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
mvn -q "-Dtest=JacksonIdempotencySnapshotCodecTest" test
```

Expected: compilation fails because the codec contract and classes do not exist.

- [ ] **Step 3: Implement the codec contract and exception**

```java
package com.rpb.reservation.idempotency.application.codec;

public interface IdempotencySnapshotCodec<T> {
    String encode(T snapshot);

    T decode(String payload);
}
```

```java
package com.rpb.reservation.idempotency.application.codec;

public final class IdempotencySnapshotException extends RuntimeException {

    public IdempotencySnapshotException(String message) {
        super(message);
    }

    public IdempotencySnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Implement the shared Jackson mechanics**

```java
package com.rpb.reservation.idempotency.application.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

public abstract class JacksonIdempotencySnapshotCodec<T> implements IdempotencySnapshotCodec<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> snapshotType;

    protected JacksonIdempotencySnapshotCodec(ObjectMapper objectMapper, Class<T> snapshotType) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "object_mapper_required");
        this.snapshotType = Objects.requireNonNull(snapshotType, "snapshot_type_required");
    }

    @Override
    public final String encode(T snapshot) {
        if (snapshot == null) {
            throw new IdempotencySnapshotException("idempotency_snapshot_required");
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IdempotencySnapshotException("idempotency_snapshot_encode_failed", exception);
        }
    }

    @Override
    public final T decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IdempotencySnapshotException("idempotency_snapshot_payload_required");
        }
        try {
            return objectMapper.readValue(payload, snapshotType);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IdempotencySnapshotException("idempotency_snapshot_decode_failed", exception);
        }
    }
}
```

- [ ] **Step 5: Run the codec tests**

Run:

```powershell
mvn -q "-Dtest=JacksonIdempotencySnapshotCodecTest" test
```

Expected: 2 tests pass with 0 failures and 0 errors.

- [ ] **Step 6: Commit the generic codec**

```powershell
git add src/main/java/com/rpb/reservation/idempotency/application/codec src/test/java/com/rpb/reservation/idempotency/application/codec
git commit -m "refactor: add typed idempotency snapshot codec"
```

### Task 2: Reservation Arrived Direct Seating Snapshot

**Files:**
- Create: `src/main/java/com/rpb/reservation/reservation/application/snapshot/ReservationArrivedDirectSeatingSnapshot.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/snapshot/ReservationArrivedDirectSeatingSnapshotCodec.java`
- Test: `src/test/java/com/rpb/reservation/reservation/application/snapshot/ReservationArrivedDirectSeatingSnapshotCodecTest.java`

**Interfaces:**
- Consumes: `JacksonIdempotencySnapshotCodec<T>` from Task 1.
- Produces: a bean assignable to `IdempotencySnapshotCodec<ReservationArrivedDirectSeatingSnapshot>`.

- [ ] **Step 1: Write the legacy compatibility test**

```java
package com.rpb.reservation.reservation.application.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationArrivedDirectSeatingSnapshotCodecTest {

    private static final String LEGACY_PAYLOAD = """
        {"reservationId":"11111111-1111-1111-1111-111111111111","reservationCode":"R-SEAT-1","reservationStatus":"seated","seatingId":"22222222-2222-2222-2222-222222222222","resourceType":"dining_table","resourceId":"33333333-3333-3333-3333-333333333333","partySizeSnapshot":4,"seatingStatus":"occupied","seatingResourceStatus":"active","tableStatus":"occupied","groupMemberStatuses":[],"alreadySeated":false}
        """.trim();

    private final ReservationArrivedDirectSeatingSnapshotCodec codec =
        new ReservationArrivedDirectSeatingSnapshotCodec(new ObjectMapper());

    @Test
    void decodesLegacyPayloadAndWritesTheSameFieldContract() {
        ReservationArrivedDirectSeatingSnapshot snapshot = codec.decode(LEGACY_PAYLOAD);

        assertThat(snapshot.reservationId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(snapshot.reservationCode()).isEqualTo("R-SEAT-1");
        assertThat(snapshot.groupMemberStatuses()).isEmpty();
        assertThat(snapshot.alreadySeated()).isFalse();
        assertThat(codec.encode(snapshot)).isEqualTo(LEGACY_PAYLOAD);
    }

    @Test
    void preservesNullableTableStatusAndCopiesMemberStatuses() {
        ReservationArrivedDirectSeatingSnapshot snapshot = new ReservationArrivedDirectSeatingSnapshot(
            UUID.randomUUID(), "R-2", "seated", UUID.randomUUID(), "table_group", UUID.randomUUID(), 6,
            "occupied", "active", null, List.of("occupied", "occupied"), true
        );

        ReservationArrivedDirectSeatingSnapshot decoded = codec.decode(codec.encode(snapshot));

        assertThat(decoded.tableStatus()).isNull();
        assertThat(decoded.groupMemberStatuses()).containsExactly("occupied", "occupied");
        assertThat(decoded.alreadySeated()).isTrue();
    }
}
```

- [ ] **Step 2: Run the Reservation codec test and verify it fails**

```powershell
mvn -q "-Dtest=ReservationArrivedDirectSeatingSnapshotCodecTest" test
```

Expected: compilation fails because the Reservation snapshot and codec do not exist.

- [ ] **Step 3: Implement the immutable Reservation snapshot**

```java
package com.rpb.reservation.reservation.application.snapshot;

import java.util.List;
import java.util.UUID;

public record ReservationArrivedDirectSeatingSnapshot(
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    UUID seatingId,
    String resourceType,
    UUID resourceId,
    int partySizeSnapshot,
    String seatingStatus,
    String seatingResourceStatus,
    String tableStatus,
    List<String> groupMemberStatuses,
    boolean alreadySeated
) {
    public ReservationArrivedDirectSeatingSnapshot {
        groupMemberStatuses = groupMemberStatuses == null ? List.of() : List.copyOf(groupMemberStatuses);
    }
}
```

- [ ] **Step 4: Implement the Reservation codec bean**

```java
package com.rpb.reservation.reservation.application.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.idempotency.application.codec.JacksonIdempotencySnapshotCodec;
import org.springframework.stereotype.Component;

@Component
public final class ReservationArrivedDirectSeatingSnapshotCodec
    extends JacksonIdempotencySnapshotCodec<ReservationArrivedDirectSeatingSnapshot> {

    public ReservationArrivedDirectSeatingSnapshotCodec(ObjectMapper objectMapper) {
        super(objectMapper, ReservationArrivedDirectSeatingSnapshot.class);
    }
}
```

- [ ] **Step 5: Run the Reservation codec tests**

```powershell
mvn -q "-Dtest=ReservationArrivedDirectSeatingSnapshotCodecTest" test
```

Expected: 2 tests pass.

- [ ] **Step 6: Commit the Reservation snapshot foundation**

```powershell
git add src/main/java/com/rpb/reservation/reservation/application/snapshot src/test/java/com/rpb/reservation/reservation/application/snapshot
git commit -m "refactor: add reservation seating snapshot codec"
```

### Task 3: Seating From Called Queue Snapshot

**Files:**
- Create: `src/main/java/com/rpb/reservation/queue/application/snapshot/SeatingFromCalledQueueSnapshot.java`
- Create: `src/main/java/com/rpb/reservation/queue/application/snapshot/SeatingFromCalledQueueSnapshotCodec.java`
- Test: `src/test/java/com/rpb/reservation/queue/application/snapshot/SeatingFromCalledQueueSnapshotCodecTest.java`

**Interfaces:**
- Consumes: `JacksonIdempotencySnapshotCodec<T>` from Task 1.
- Produces: a bean assignable to `IdempotencySnapshotCodec<SeatingFromCalledQueueSnapshot>`.

- [ ] **Step 1: Write the Queue legacy compatibility test**

```java
package com.rpb.reservation.queue.application.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeatingFromCalledQueueSnapshotCodecTest {

    private static final String LEGACY_PAYLOAD = """
        {"queueTicketId":"11111111-1111-1111-1111-111111111111","queueTicketNumber":22,"queueTicketStatus":"seated","reservationId":"22222222-2222-2222-2222-222222222222","reservationCode":"R-Q-SEAT-1","reservationStatus":"seated","seatingId":"33333333-3333-3333-3333-333333333333","resourceType":"dining_table","resourceId":"44444444-4444-4444-4444-444444444444","partySizeSnapshot":4,"seatingStatus":"occupied","seatingResourceStatus":"active","tableStatus":"occupied","groupMemberStatuses":[],"alreadySeated":false}
        """.trim();

    private final SeatingFromCalledQueueSnapshotCodec codec =
        new SeatingFromCalledQueueSnapshotCodec(new ObjectMapper());

    @Test
    void decodesLegacyReservationBackedQueuePayload() {
        SeatingFromCalledQueueSnapshot snapshot = codec.decode(LEGACY_PAYLOAD);

        assertThat(snapshot.queueTicketNumber()).isEqualTo(22);
        assertThat(snapshot.reservationCode()).isEqualTo("R-Q-SEAT-1");
        assertThat(snapshot.alreadySeated()).isFalse();
        assertThat(codec.encode(snapshot)).isEqualTo(LEGACY_PAYLOAD);
    }

    @Test
    void roundTripsWalkInBackedQueuePayloadWithNullReservationFields() {
        SeatingFromCalledQueueSnapshot snapshot = new SeatingFromCalledQueueSnapshot(
            UUID.randomUUID(), 23, "seated", null, null, null, UUID.randomUUID(),
            "table_group", UUID.randomUUID(), 5, "occupied", "active", null,
            List.of("occupied", "occupied"), true
        );

        SeatingFromCalledQueueSnapshot decoded = codec.decode(codec.encode(snapshot));

        assertThat(decoded.reservationId()).isNull();
        assertThat(decoded.reservationCode()).isNull();
        assertThat(decoded.groupMemberStatuses()).containsExactly("occupied", "occupied");
    }
}
```

- [ ] **Step 2: Run the Queue codec test and verify it fails**

```powershell
mvn -q "-Dtest=SeatingFromCalledQueueSnapshotCodecTest" test
```

Expected: compilation fails because the Queue snapshot and codec do not exist.

- [ ] **Step 3: Implement the immutable Queue snapshot**

```java
package com.rpb.reservation.queue.application.snapshot;

import java.util.List;
import java.util.UUID;

public record SeatingFromCalledQueueSnapshot(
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    UUID seatingId,
    String resourceType,
    UUID resourceId,
    int partySizeSnapshot,
    String seatingStatus,
    String seatingResourceStatus,
    String tableStatus,
    List<String> groupMemberStatuses,
    boolean alreadySeated
) {
    public SeatingFromCalledQueueSnapshot {
        groupMemberStatuses = groupMemberStatuses == null ? List.of() : List.copyOf(groupMemberStatuses);
    }
}
```

- [ ] **Step 4: Implement the Queue codec bean**

```java
package com.rpb.reservation.queue.application.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.idempotency.application.codec.JacksonIdempotencySnapshotCodec;
import org.springframework.stereotype.Component;

@Component
public final class SeatingFromCalledQueueSnapshotCodec
    extends JacksonIdempotencySnapshotCodec<SeatingFromCalledQueueSnapshot> {

    public SeatingFromCalledQueueSnapshotCodec(ObjectMapper objectMapper) {
        super(objectMapper, SeatingFromCalledQueueSnapshot.class);
    }
}
```

- [ ] **Step 5: Run the Queue codec tests**

```powershell
mvn -q "-Dtest=SeatingFromCalledQueueSnapshotCodecTest" test
```

Expected: 2 tests pass.

- [ ] **Step 6: Commit the Queue snapshot foundation**

```powershell
git add src/main/java/com/rpb/reservation/queue/application/snapshot src/test/java/com/rpb/reservation/queue/application/snapshot
git commit -m "refactor: add queue seating snapshot codec"
```

### Task 4: Walk-In Direct Seating Snapshot

**Files:**
- Create: `src/main/java/com/rpb/reservation/walkin/application/snapshot/WalkInDirectSeatingSnapshot.java`
- Create: `src/main/java/com/rpb/reservation/walkin/application/snapshot/WalkInDirectSeatingSnapshotCodec.java`
- Test: `src/test/java/com/rpb/reservation/walkin/application/snapshot/WalkInDirectSeatingSnapshotCodecTest.java`

**Interfaces:**
- Consumes: `JacksonIdempotencySnapshotCodec<T>` from Task 1.
- Produces: a bean assignable to `IdempotencySnapshotCodec<WalkInDirectSeatingSnapshot>`.

- [ ] **Step 1: Write the Walk-in legacy compatibility test**

```java
package com.rpb.reservation.walkin.application.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalkInDirectSeatingSnapshotCodecTest {

    private static final String LEGACY_PAYLOAD = """
        {"walkInId":"11111111-1111-1111-1111-111111111111","seatingId":"22222222-2222-2222-2222-222222222222","resourceType":"dining_table","resourceId":"33333333-3333-3333-3333-333333333333","partySizeSnapshot":2}
        """.trim();

    private final WalkInDirectSeatingSnapshotCodec codec =
        new WalkInDirectSeatingSnapshotCodec(new ObjectMapper());

    @Test
    void decodesLegacyPayloadAndWritesTheSameFieldContract() {
        WalkInDirectSeatingSnapshot snapshot = codec.decode(LEGACY_PAYLOAD);

        assertThat(snapshot.walkInId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(snapshot.partySizeSnapshot()).isEqualTo(2);
        assertThat(codec.encode(snapshot)).isEqualTo(LEGACY_PAYLOAD);
    }
}
```

- [ ] **Step 2: Run the Walk-in codec test and verify it fails**

```powershell
mvn -q "-Dtest=WalkInDirectSeatingSnapshotCodecTest" test
```

Expected: compilation fails because the Walk-in snapshot and codec do not exist.

- [ ] **Step 3: Implement the Walk-in snapshot and codec**

```java
package com.rpb.reservation.walkin.application.snapshot;

import java.util.UUID;

public record WalkInDirectSeatingSnapshot(
    UUID walkInId,
    UUID seatingId,
    String resourceType,
    UUID resourceId,
    int partySizeSnapshot
) {
}
```

```java
package com.rpb.reservation.walkin.application.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.idempotency.application.codec.JacksonIdempotencySnapshotCodec;
import org.springframework.stereotype.Component;

@Component
public final class WalkInDirectSeatingSnapshotCodec
    extends JacksonIdempotencySnapshotCodec<WalkInDirectSeatingSnapshot> {

    public WalkInDirectSeatingSnapshotCodec(ObjectMapper objectMapper) {
        super(objectMapper, WalkInDirectSeatingSnapshot.class);
    }
}
```

- [ ] **Step 4: Run the Walk-in codec test**

```powershell
mvn -q "-Dtest=WalkInDirectSeatingSnapshotCodecTest" test
```

Expected: 1 test passes.

- [ ] **Step 5: Commit the Walk-in snapshot foundation**

```powershell
git add src/main/java/com/rpb/reservation/walkin/application/snapshot src/test/java/com/rpb/reservation/walkin/application/snapshot
git commit -m "refactor: add walk-in seating snapshot codec"
```

### Task 5: Store Business-Time Capability

**Files:**
- Create: `src/main/java/com/rpb/reservation/store/application/time/StoreBusinessTime.java`
- Test: `src/test/java/com/rpb/reservation/store/application/time/StoreBusinessTimeTest.java`

**Interfaces:**
- Consumes: the existing `Clock` bean from `common/time/SystemClockConfiguration` and `com.rpb.reservation.store.domain.Store`.
- Produces: an injectable Spring bean with `OffsetDateTime now()`, `Instant instant()`, and `BusinessDate businessDate(Store store)`.

- [ ] **Step 1: Write fixed-clock and timezone-boundary tests**

```java
package com.rpb.reservation.store.application.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreBusinessTimeTest {

    private static final Instant NOW = Instant.parse("2026-07-16T16:30:00Z");
    private final StoreBusinessTime businessTime = new StoreBusinessTime(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void exposesTheInjectedCurrentTime() {
        assertThat(businessTime.instant()).isEqualTo(NOW);
        assertThat(businessTime.now()).isEqualTo(OffsetDateTime.parse("2026-07-16T16:30:00Z"));
    }

    @Test
    void derivesBusinessDateFromStoreTimezoneAcrossUtcMidnightBoundary() {
        Store singaporeStore = store("Asia/Singapore");

        BusinessDate result = businessTime.businessDate(singaporeStore);

        assertThat(result.value()).isEqualTo(LocalDate.of(2026, 7, 17));
    }

    @Test
    void preservesTheCurrentUtcFallbackForAnInvalidTimezone() {
        Store invalidTimezoneStore = store("invalid/timezone");

        BusinessDate result = businessTime.businessDate(invalidTimezoneStore);

        assertThat(result.value()).isEqualTo(LocalDate.of(2026, 7, 16));
    }

    private static Store store(String timezone) {
        TenantId tenantId = new TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        return new Store(
            new StoreId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
            tenantId,
            "STORE-1",
            timezone,
            "zh-CN",
            "active"
        );
    }
}
```

- [ ] **Step 2: Run the Store business-time test and verify it fails**

```powershell
mvn -q "-Dtest=StoreBusinessTimeTest" test
```

Expected: compilation fails because `StoreBusinessTime` does not exist.

- [ ] **Step 3: Implement Store business time from the injected clock**

```java
package com.rpb.reservation.store.application.time;

import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.store.domain.Store;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class StoreBusinessTime {

    private final Clock clock;

    public StoreBusinessTime(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock_required");
    }

    public Instant instant() {
        return Instant.now(clock);
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public BusinessDate businessDate(Store store) {
        Objects.requireNonNull(store, "store_required");
        return new BusinessDate(LocalDate.now(clock.withZone(zoneId(store.timezone()))));
    }

    private static ZoneId zoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }
}
```

- [ ] **Step 4: Run the Store business-time tests**

```powershell
mvn -q "-Dtest=StoreBusinessTimeTest" test
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit the Store time foundation**

```powershell
git add src/main/java/com/rpb/reservation/store/application/time src/test/java/com/rpb/reservation/store/application/time
git commit -m "refactor: add store business time boundary"
```

### Task 6: Foundation Regression Gate

**Files:**
- Verify only; no new production file.
- Test: all files created by Tasks 1–5 plus existing focused seating tests.

**Interfaces:**
- Consumes: all codec and time foundations from this plan.
- Produces: evidence that unused foundations do not change current workflow wiring or behaviour.

- [ ] **Step 1: Run all new foundation tests together**

```powershell
mvn -q "-Dtest=JacksonIdempotencySnapshotCodecTest,ReservationArrivedDirectSeatingSnapshotCodecTest,SeatingFromCalledQueueSnapshotCodecTest,WalkInDirectSeatingSnapshotCodecTest,StoreBusinessTimeTest" test
```

Expected: 10 tests pass with 0 failures and 0 errors.

- [ ] **Step 2: Run the existing seating workflow tests**

```powershell
mvn -q "-Dtest=ReservationArrivedDirectSeatingApplicationServiceTest,SeatingFromCalledQueueApplicationServiceTest,WalkInDirectSeatingApplicationServiceTest" test
```

Expected: all existing tests pass; the service constructors and workflow code remain unchanged.

- [ ] **Step 3: Run compile and diff checks**

```powershell
mvn -q -DskipTests compile
git diff --check
git status --short
```

Expected: compile succeeds, `git diff --check` prints nothing, and status contains only intentional uncommitted work if a previous task was not committed.

- [ ] **Step 4: Record the phase result in the implementation handoff**

Record these exact facts in the eventual implementation report:

```text
Backend snapshot/time foundations added.
Existing seating services not rewired in this phase.
Legacy Reservation, Queue, and Walk-in snapshot fixtures decode successfully.
Store business date is fixed-clock tested for Asia/Singapore and invalid-timezone UTC fallback.
No API, schema, migration, state-machine, App Gate, transaction, or workflow change.
```
