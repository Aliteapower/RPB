# Temporary Table Groups Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable staff to select two or more available dining tables and atomically seat them as a same-service temporary TableGroup.

**Architecture:** Extend the existing reservation, queue, and walk-in direct seating commands with `temporaryTableIds`. A shared table application service validates member tables and creates an occupied temporary `TableGroup` plus active members; the existing seating services remain the owners of source-specific state transitions, seating records, idempotency, audit, and response mapping. Cleaning releases member tables and ends only temporary groups, leaving fixed groups as reusable configuration.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL, Vue 3, TypeScript, Vite, Maven, npm.

---

## Scope Decisions

- Phase 1 implements atomic "combine and seat"; it does not create a standalone temporary group management endpoint.
- Reservation and queue direct seating require exactly one target among `tableId`, `tableGroupId`, and `temporaryTableIds`.
- Walk-in direct seating keeps its existing backward-compatible auto assignment when all three target fields are absent. If `temporaryTableIds` is present, it is exclusive with `tableId` and `tableGroupId`.
- `temporaryTableIds` must contain at least two distinct UUIDs.
- Capacity uses sum of member `capacityMin` and sum of member `capacityMax`.
- The existing schema already supports `table_groups.group_type = 'temporary'` and temporary statuses, so this phase has no Flyway migration.
- Existing permissions remain unchanged: `reservation.seat`, `queue.seat`, `walkin.direct_seating.create`, `table.view`, `cleaning.start`, and `cleaning.complete`.

## Existing References

- Design: `docs/superpowers/specs/2026-06-24-table-group-management-design.md`
- Reservation API contract: `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_CONTRACT.md`
- Queue API contract: `docs/api/SEATING_FROM_CALLED_QUEUE_API_CONTRACT.md`
- Walk-in API contract: `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- Table resource API contract: `docs/api/TABLE_RESOURCE_LIST_API_CONTRACT.md`
- Governance: `docs/governance/BUSINESS_GLOSSARY.md`, `docs/governance/BUSINESS_RULES.md`, `docs/governance/DATA_STANDARD.md`, `docs/governance/DATA_CHECKLIST.md`
- Architecture: `docs/architecture/ARCHITECTURE.md`

## File Structure

### Backend contracts and API mapping

- Modify: `src/main/java/com/rpb/reservation/reservation/api/SeatArrivedReservationRequest.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/command/SeatArrivedReservationCommand.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/ReservationArrivedDirectSeatingError.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/api/ReservationApiErrorCode.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiMapper.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiErrorMapper.java`
- Modify: `src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketRequest.java`
- Modify: `src/main/java/com/rpb/reservation/queue/application/command/SeatCalledQueueTicketCommand.java`
- Modify: `src/main/java/com/rpb/reservation/queue/application/SeatingFromCalledQueueError.java`
- Modify: `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorCode.java`
- Modify: `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiMapper.java`
- Modify: `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorMapper.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/api/SeatWalkInDirectlyRequest.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/application/command/SeatWalkInDirectlyCommand.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/application/WalkInDirectSeatingError.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/api/ApiErrorCode.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingApiMapper.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingApiErrorMapper.java`

### Shared temporary group capability

- Create: `src/main/java/com/rpb/reservation/table/application/TemporaryTableGroupError.java`
- Create: `src/main/java/com/rpb/reservation/table/application/TemporaryTableGroupResult.java`
- Create: `src/main/java/com/rpb/reservation/table/application/command/CreateTemporaryTableGroupForSeatingCommand.java`
- Create: `src/main/java/com/rpb/reservation/table/application/service/TemporaryTableGroupApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/table/group/policy/TemporaryTableGroupPolicy.java`
- Create: `src/main/java/com/rpb/reservation/table/group/policy/DefaultTemporaryTableGroupPolicy.java`
- Modify: `src/main/java/com/rpb/reservation/table/group/rule/DefaultTableGroupValidationRule.java`
- Modify: `src/main/java/com/rpb/reservation/table/rule/DefaultTableAvailabilityRule.java`
- Modify: `src/main/java/com/rpb/reservation/table/application/port/out/TableGroupRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/repository/TableGroupJpaRepository.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/adapter/TableGroupPersistenceAdapter.java`

### Seating and cleaning services

- Modify: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationArrivedDirectSeatingApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/queue/application/service/SeatingFromCalledQueueApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/application/service/WalkInDirectSeatingApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/cleaning/application/service/CleaningApplicationService.java`

### Table resource list

- Modify: `src/main/java/com/rpb/reservation/table/application/TableResourceItem.java`
- Modify: `src/main/java/com/rpb/reservation/table/api/TableResourceItemResponse.java`
- Modify: `src/main/java/com/rpb/reservation/table/api/TableResourceListController.java`
- Modify: `src/main/java/com/rpb/reservation/table/application/service/TableResourceListApplicationService.java`
- Modify: `src/types/tableResource.ts`

### Frontend seating UI

- Modify: `src/types/reservationArrivedDirectSeating.ts`
- Modify: `src/api/reservationArrivedDirectSeatingApi.ts`
- Modify: `src/types/seatingFromCalledQueue.ts`
- Modify: `src/api/seatingFromCalledQueueApi.ts`
- Modify: `src/types/walkInDirectSeating.ts`
- Modify: `src/api/walkInDirectSeatingApi.ts`
- Modify: `src/components/staff-table/TableResourcePicker.vue`
- Modify: `src/components/reservation-workbench/ReservationSeatDialog.vue`
- Modify: `src/pages/ReservationArrivedDirectSeatingPage.vue`
- Modify: `src/pages/SeatingFromCalledQueuePage.vue`
- Modify: `src/pages/WalkInDirectSeatingPage.vue`
- Modify: `src/pages/TableResourceListPage.vue`

### Tests and docs

- Modify: `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingControllerTest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiIntegrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/application/ReservationArrivedDirectSeatingApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueControllerTest.java`
- Modify: `src/test/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiIntegrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/queue/application/SeatingFromCalledQueueApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`
- Modify: `src/test/java/com/rpb/reservation/walkin/integration/WalkInDirectSeatingApiIntegrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/walkin/application/WalkInDirectSeatingApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/cleaning/application/CleaningApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/table/application/TableResourceListApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/table/api/TableResourceListControllerTest.java`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java`
- Modify: `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_CONTRACT.md`
- Modify: `docs/api/SEATING_FROM_CALLED_QUEUE_API_CONTRACT.md`
- Modify: `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- Modify: `docs/api/TABLE_RESOURCE_LIST_API_CONTRACT.md`
- Create: `docs/release-notes/2026-06-24-temporary-table-groups-phase-1.md`

## Task 1: API Contract And DTO Shape

**Files:**
- Modify: `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_CONTRACT.md`
- Modify: `docs/api/SEATING_FROM_CALLED_QUEUE_API_CONTRACT.md`
- Modify: `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- Modify: `src/main/java/com/rpb/reservation/reservation/api/SeatArrivedReservationRequest.java`
- Modify: `src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketRequest.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/api/SeatWalkInDirectlyRequest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingControllerTest.java`
- Modify: `src/test/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueControllerTest.java`
- Modify: `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`

- [ ] **Step 1: Write the API contract snippets**

Add this request field table wording to the reservation and queue contracts:

```markdown
| `temporaryTableIds` | Conditional | UUID array | Required only when `tableId` and `tableGroupId` are absent. Must contain at least two distinct DiningTable ids. Mutually exclusive with `tableId` and `tableGroupId`. |

Exactly one of `tableId`, `tableGroupId`, and `temporaryTableIds` must be provided.
```

Add this compatibility wording to the walk-in contract:

```markdown
| `temporaryTableIds` | No | UUID array or null | Staff-selected DiningTables for an atomic temporary TableGroup. When present, it must contain at least two distinct ids and must not be combined with `tableId` or `tableGroupId`. |

If none of `tableId`, `tableGroupId`, and `temporaryTableIds` is present, walk-in direct seating keeps the existing auto-assignment behavior.
```

Add these stable errors to all three affected seating contracts:

```markdown
| `TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED` | 400 | Fewer than two temporary member table ids were supplied. |
| `TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE` | 400 | The same member table id appeared more than once. |
| `TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE` | 409 | A member table is missing, inactive, reserved, locked, occupied, cleaning, non-combinable, actively cleaning, actively occupied, or belongs to an active temporary group. |
| `TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT` | 409 | The combined member capacity does not fit the party size. |
| `TEMPORARY_TABLE_GROUP_LOCK_CONFLICT` | 409 | A member table has an active lock. |
| `TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT` | 409 | A member table has an active same-day reservation preassignment that conflicts with this seating action. |
```

- [ ] **Step 2: Write failing controller tests for request parsing**

In `ReservationArrivedDirectSeatingControllerTest`, add:

```java
@Test
void seatArrivedReservationMapsTemporaryTableIds() throws Exception {
    UUID firstTableId = UUID.fromString("70000000-0000-0000-0000-000000000981");
    UUID secondTableId = UUID.fromString("70000000-0000-0000-0000-000000000982");
    when(applicationService.seatArrivedReservation(any()))
        .thenReturn(ReservationArrivedDirectSeatingResult.success(
            RESERVATION_ID,
            "R-SEAT-1",
            UUID.fromString("78000000-0000-0000-0000-000000000001"),
            "table_group",
            UUID.fromString("71000000-0000-0000-0000-000000009999"),
            6,
            null,
            List.of("occupied", "occupied"),
            List.of(firstTableId, secondTableId),
            "completed",
            List.of("table_group.temporary.created", "table_group.temporary.locked", "table_group.temporary.occupied", "reservation.seated", "seating.created", "table.occupied"),
            List.of(),
            List.of(),
            UUID.randomUUID()
        ));

    mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
            .header("Idempotency-Key", "idem-temp-reservation")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "temporaryTableIds": [
                    "70000000-0000-0000-0000-000000000981",
                    "70000000-0000-0000-0000-000000000982"
                  ],
                  "note": "staff_reservation_today_list"
                }
                """))
        .andExpect(status().isOk());

    ArgumentCaptor<SeatArrivedReservationCommand> commandCaptor = ArgumentCaptor.forClass(SeatArrivedReservationCommand.class);
    verify(applicationService).seatArrivedReservation(commandCaptor.capture());
    assertThat(commandCaptor.getValue().tableId()).isNull();
    assertThat(commandCaptor.getValue().tableGroupId()).isNull();
    assertThat(commandCaptor.getValue().temporaryTableIds()).containsExactly(firstTableId, secondTableId);
}
```

In `SeatingFromCalledQueueControllerTest`, add the same request shape and assert:

```java
assertThat(commandCaptor.getValue().tableId()).isNull();
assertThat(commandCaptor.getValue().tableGroupId()).isNull();
assertThat(commandCaptor.getValue().temporaryTableIds()).containsExactly(firstTableId, secondTableId);
```

In `WalkInDirectSeatingControllerTest`, add the same request shape with `partySize` and assert:

```java
assertThat(commandCaptor.getValue().tableId()).isNull();
assertThat(commandCaptor.getValue().tableGroupId()).isNull();
assertThat(commandCaptor.getValue().temporaryTableIds()).containsExactly(firstTableId, secondTableId);
```

- [ ] **Step 3: Write failing controller tests for contract rejection**

In reservation and queue controller tests, add assertions for:

```java
{"temporaryTableIds":[]}
```

Expected: HTTP 400 and `$.error.code == "TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED"`.

```java
{"temporaryTableIds":["70000000-0000-0000-0000-000000000981"]}
```

Expected: HTTP 400 and `$.error.code == "TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED"`.

```java
{"temporaryTableIds":["70000000-0000-0000-0000-000000000981","70000000-0000-0000-0000-000000000981"]}
```

Expected: HTTP 400 and `$.error.code == "TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE"`.

```java
{"tableId":"60000000-0000-0000-0000-000000000001","temporaryTableIds":["70000000-0000-0000-0000-000000000981","70000000-0000-0000-0000-000000000982"]}
```

Expected: HTTP 400 and `$.error.code == "RESOURCE_SELECTION_CONFLICT"`.

For walk-in controller tests, include `partySize: 6` in each JSON body and expect the walk-in API error code names mapped in Task 2.

- [ ] **Step 4: Run the failing controller tests**

Run:

```powershell
mvn -Dtest=ReservationArrivedDirectSeatingControllerTest,SeatingFromCalledQueueControllerTest,WalkInDirectSeatingControllerTest test
```

Expected: FAIL because DTOs do not expose `temporaryTableIds` and error enums do not contain the new temporary group codes.

- [ ] **Step 5: Update request DTOs**

Use these record signatures:

```java
public record SeatArrivedReservationRequest(
    UUID tableId,
    UUID tableGroupId,
    List<UUID> temporaryTableIds,
    String overrideReasonCode,
    String overrideNote,
    String note
) {
    public SeatArrivedReservationRequest {
        temporaryTableIds = temporaryTableIds == null ? List.of() : List.copyOf(temporaryTableIds);
    }
}
```

```java
public record SeatCalledQueueTicketRequest(
    UUID tableId,
    UUID tableGroupId,
    List<UUID> temporaryTableIds,
    String overrideReasonCode,
    String overrideNote,
    String note
) {
    public SeatCalledQueueTicketRequest {
        temporaryTableIds = temporaryTableIds == null ? List.of() : List.copyOf(temporaryTableIds);
    }
}
```

```java
public record SeatWalkInDirectlyRequest(
    Integer partySize,
    UUID customerId,
    String customerName,
    String customerNickname,
    String phoneE164,
    UUID tableId,
    UUID tableGroupId,
    List<UUID> temporaryTableIds,
    String overrideReasonCode,
    String overrideNote
) {
    public SeatWalkInDirectlyRequest {
        temporaryTableIds = temporaryTableIds == null ? List.of() : List.copyOf(temporaryTableIds);
    }
}
```

The `validateContract()` logic must count `temporaryTableIds` as present when the list is not empty. Reservation and queue reject zero selected targets. Walk-in allows zero selected targets.

- [ ] **Step 6: Commit the API contract and DTO task**

Run:

```powershell
git add docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_CONTRACT.md docs/api/SEATING_FROM_CALLED_QUEUE_API_CONTRACT.md docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md src/main/java/com/rpb/reservation/reservation/api/SeatArrivedReservationRequest.java src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketRequest.java src/main/java/com/rpb/reservation/walkin/api/SeatWalkInDirectlyRequest.java src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingControllerTest.java src/test/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueControllerTest.java src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java
git commit -m "feat: extend seating requests for temporary table groups"
```

Expected: commit succeeds.

## Task 2: Commands, Error Mapping, And Idempotency Hashes

**Files:**
- Modify: `src/main/java/com/rpb/reservation/reservation/application/command/SeatArrivedReservationCommand.java`
- Modify: `src/main/java/com/rpb/reservation/queue/application/command/SeatCalledQueueTicketCommand.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/application/command/SeatWalkInDirectlyCommand.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/ReservationArrivedDirectSeatingError.java`
- Modify: `src/main/java/com/rpb/reservation/queue/application/SeatingFromCalledQueueError.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/application/WalkInDirectSeatingError.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/api/ReservationApiErrorCode.java`
- Modify: `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorCode.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/api/ApiErrorCode.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiMapper.java`
- Modify: `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiMapper.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingApiMapper.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationArrivedDirectSeatingApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/queue/application/service/SeatingFromCalledQueueApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/walkin/application/service/WalkInDirectSeatingApplicationService.java`

- [ ] **Step 1: Update command records**

Each command record must add `List<UUID> temporaryTableIds` immediately after `tableGroupId`, with a compact constructor:

```java
public SeatArrivedReservationCommand {
    temporaryTableIds = temporaryTableIds == null ? List.of() : List.copyOf(temporaryTableIds);
}
```

Apply the same constructor pattern to `SeatCalledQueueTicketCommand` and `SeatWalkInDirectlyCommand`.

- [ ] **Step 2: Add domain error enum values**

Add these values to `ReservationArrivedDirectSeatingError` and `SeatingFromCalledQueueError`:

```java
TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED("temporary_table_group_member_required"),
TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE("temporary_table_group_member_duplicate"),
TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE("temporary_table_group_member_unavailable"),
TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT("temporary_table_group_capacity_insufficient"),
TEMPORARY_TABLE_GROUP_LOCK_CONFLICT("temporary_table_group_lock_conflict"),
TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT("temporary_table_group_preassignment_conflict"),
```

Add these values to `WalkInDirectSeatingError`:

```java
TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED("temporary_table_group_member_required"),
TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE("temporary_table_group_member_duplicate"),
TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE("temporary_table_group_member_unavailable"),
TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT("temporary_table_group_capacity_insufficient"),
TEMPORARY_TABLE_GROUP_LOCK_CONFLICT("temporary_table_group_lock_conflict"),
TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT("temporary_table_group_preassignment_conflict"),
```

- [ ] **Step 3: Add API error codes**

Reservation and queue API codes use HTTP 400 for required and duplicate, HTTP 409 for unavailable, capacity, lock, and preassignment:

```java
TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED(HttpStatus.BAD_REQUEST, "reservation.temporary_table_group_member_required"),
TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE(HttpStatus.BAD_REQUEST, "reservation.temporary_table_group_member_duplicate"),
TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE(HttpStatus.CONFLICT, "reservation.temporary_table_group_member_unavailable"),
TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "reservation.temporary_table_group_capacity_insufficient"),
TEMPORARY_TABLE_GROUP_LOCK_CONFLICT(HttpStatus.CONFLICT, "reservation.temporary_table_group_lock_conflict"),
TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT(HttpStatus.CONFLICT, "reservation.temporary_table_group_preassignment_conflict"),
```

Use `queue.seat.` prefix for queue message keys. In `ApiErrorCode`, add the same enum constants with explicit statuses:

```java
TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED(HttpStatus.BAD_REQUEST),
TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE(HttpStatus.BAD_REQUEST),
TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE(HttpStatus.CONFLICT),
TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT),
TEMPORARY_TABLE_GROUP_LOCK_CONFLICT(HttpStatus.CONFLICT),
TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT(HttpStatus.CONFLICT),
```

- [ ] **Step 4: Map API request fields into commands**

In each API mapper, pass `request.temporaryTableIds()` into the new command constructor slot:

```java
request.tableId(),
request.tableGroupId(),
request.temporaryTableIds(),
idempotencyKey.trim(),
```

- [ ] **Step 5: Normalize idempotency hashes**

Add a helper to the three application services:

```java
private static String values(List<UUID> values) {
    if (values == null || values.isEmpty()) {
        return "";
    }
    return values.stream()
        .map(UUID::toString)
        .sorted()
        .collect(java.util.stream.Collectors.joining(","));
}
```

Include it in each request hash:

```java
value(command.tableGroupId()),
values(command.temporaryTableIds()),
```

- [ ] **Step 6: Run mapping tests**

Run:

```powershell
mvn -Dtest=ReservationArrivedDirectSeatingControllerTest,SeatingFromCalledQueueControllerTest,WalkInDirectSeatingControllerTest test
```

Expected: request parsing and enum mapping tests pass; service tests that construct commands fail until their helper constructors are updated.

- [ ] **Step 7: Commit the command and error mapping task**

Run:

```powershell
git add src/main/java/com/rpb/reservation/reservation src/main/java/com/rpb/reservation/queue src/main/java/com/rpb/reservation/walkin src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingControllerTest.java src/test/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueControllerTest.java src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java
git commit -m "feat: map temporary table ids through seating commands"
```

Expected: commit succeeds.

## Task 3: Shared Temporary TableGroup Service

**Files:**
- Create: `src/main/java/com/rpb/reservation/table/application/TemporaryTableGroupError.java`
- Create: `src/main/java/com/rpb/reservation/table/application/TemporaryTableGroupResult.java`
- Create: `src/main/java/com/rpb/reservation/table/application/command/CreateTemporaryTableGroupForSeatingCommand.java`
- Create: `src/main/java/com/rpb/reservation/table/application/service/TemporaryTableGroupApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/table/group/policy/TemporaryTableGroupPolicy.java`
- Create: `src/main/java/com/rpb/reservation/table/group/policy/DefaultTemporaryTableGroupPolicy.java`
- Modify: `src/main/java/com/rpb/reservation/table/application/port/out/TableGroupRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/repository/TableGroupJpaRepository.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/adapter/TableGroupPersistenceAdapter.java`
- Test: `src/test/java/com/rpb/reservation/table/application/TemporaryTableGroupApplicationServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `TemporaryTableGroupApplicationServiceTest` with these test methods:

```java
@Test
void createForSeatingCreatesOccupiedTemporaryGroupAndMembers()

@Test
void fewerThanTwoMemberIdsAreRejected()

@Test
void duplicateMemberIdsAreRejected()

@Test
void missingInactiveCleaningOccupiedReservedLockedOrNonCombinableMemberIsRejected()

@Test
void capacityMismatchIsRejected()

@Test
void activeTableLockIsRejected()

@Test
void activeSeatingOccupancyIsRejected()

@Test
void activeCleaningIsRejected()

@Test
void activeReservationPreassignmentIsRejected()

@Test
void activeTemporaryGroupForMemberIsRejected()

@Test
void activeFixedGroupForMemberDoesNotBlock()
```

The success assertion must verify:

```java
assertThat(result.success()).isTrue();
assertThat(result.group().groupType()).isEqualTo("temporary");
assertThat(result.group().status()).isEqualTo(TableGroupStatus.OCCUPIED);
assertThat(result.group().capacity().min()).isEqualTo(6);
assertThat(result.group().capacity().max()).isEqualTo(10);
assertThat(result.group().groupCode()).startsWith("TMP-A01+A02-");
assertThat(result.members()).hasSize(2);
assertThat(tableGroupRepository.savedGroups).contains(result.group());
assertThat(tableGroupRepository.savedMembers).hasSize(2);
```

- [ ] **Step 2: Run the failing service test**

Run:

```powershell
mvn -Dtest=TemporaryTableGroupApplicationServiceTest test
```

Expected: FAIL because the service and result types do not exist.

- [ ] **Step 3: Add result and command records**

Use these exact public contracts:

```java
public enum TemporaryTableGroupError {
    MEMBER_REQUIRED,
    MEMBER_DUPLICATE,
    MEMBER_UNAVAILABLE,
    CAPACITY_INSUFFICIENT,
    LOCK_CONFLICT,
    PREASSIGNMENT_CONFLICT
}
```

```java
public record CreateTemporaryTableGroupForSeatingCommand(
    StoreScope scope,
    List<UUID> tableIds,
    PartySize partySize,
    BusinessDate businessDate,
    UUID allowedReservationId
) {
    public CreateTemporaryTableGroupForSeatingCommand {
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(partySize, "party_size_required");
        Objects.requireNonNull(businessDate, "business_date_required");
        tableIds = tableIds == null ? List.of() : List.copyOf(tableIds);
    }
}
```

```java
public record TemporaryTableGroupResult(
    boolean success,
    TemporaryTableGroupError error,
    TableGroup group,
    List<DiningTable> memberTables,
    List<TableGroupMember> members
) {
    public TemporaryTableGroupResult {
        memberTables = memberTables == null ? List.of() : List.copyOf(memberTables);
        members = members == null ? List.of() : List.copyOf(members);
    }

    public static TemporaryTableGroupResult success(TableGroup group, List<DiningTable> memberTables, List<TableGroupMember> members) {
        return new TemporaryTableGroupResult(true, null, group, memberTables, members);
    }

    public static TemporaryTableGroupResult failure(TemporaryTableGroupError error) {
        return new TemporaryTableGroupResult(false, error, null, List.of(), List.of());
    }
}
```

- [ ] **Step 4: Replace the temporary policy input stub**

Modify `TemporaryTableGroupPolicy` to use real input:

```java
RuleDecision decide(TemporaryTableGroupInput input);

record TemporaryTableGroupInput(
    List<DiningTable> memberTables,
    PartySize partySize,
    boolean duplicateMemberIds,
    boolean lockConflict,
    boolean activeOccupancyConflict,
    boolean activeCleaningConflict,
    boolean preassignmentConflict,
    boolean activeTemporaryGroupConflict
) implements RuleInput {
    public TemporaryTableGroupInput {
        memberTables = memberTables == null ? List.of() : List.copyOf(memberTables);
    }
}
```

Create `DefaultTemporaryTableGroupPolicy` with these decisions:

```java
if (input.memberTables().size() < 2) return RuleDecision.deny("temporary_table_group_member_required");
if (input.duplicateMemberIds()) return RuleDecision.deny("temporary_table_group_member_duplicate");
if (input.lockConflict()) return RuleDecision.deny("temporary_table_group_lock_conflict");
if (input.preassignmentConflict()) return RuleDecision.deny("temporary_table_group_preassignment_conflict");
if (input.activeOccupancyConflict() || input.activeCleaningConflict() || input.activeTemporaryGroupConflict()) {
    return RuleDecision.deny("temporary_table_group_member_unavailable");
}
for (DiningTable table : input.memberTables()) {
    if (table.status() != DiningTableStatus.AVAILABLE || !table.combinable()) {
        return RuleDecision.deny("temporary_table_group_member_unavailable");
    }
}
int min = input.memberTables().stream().mapToInt(table -> table.capacity().min()).sum();
int max = input.memberTables().stream().mapToInt(table -> table.capacity().max()).sum();
int partySize = input.partySize().value();
if (partySize < min || partySize > max) return RuleDecision.deny("temporary_table_group_capacity_insufficient");
return RuleDecision.allow();
```

- [ ] **Step 5: Add repository code lookup**

Add this port method:

```java
boolean existsActiveCode(StoreScope scope, String groupCode);
```

Add this JPA method:

```java
boolean existsByTenantIdAndStoreIdAndGroupCodeAndDeletedAtIsNull(UUID tenantId, UUID storeId, String groupCode);
```

Implement it in `TableGroupPersistenceAdapter`:

```java
return groupRepository.existsByTenantIdAndStoreIdAndGroupCodeAndDeletedAtIsNull(
    scope.tenantId().value(),
    scope.storeId().value(),
    groupCode
);
```

- [ ] **Step 6: Implement temporary group creation**

`TemporaryTableGroupApplicationService#createForSeating` must:

1. Reject fewer than two IDs before loading tables.
2. Reject duplicate IDs before loading tables.
3. Load each `DiningTable` by scoped `TableId`.
4. Check each table is `AVAILABLE` and `combinable`.
5. Check `tableLockRepository.existsActiveConflict(scope, "dining_table", table.id().value(), now)`.
6. Check `seatingRepository.existsActiveResourceOccupancy(scope, "dining_table", table.id().value())`.
7. Check `cleaningRepository.findActiveByResource(scope, "dining_table", table.id().value()).isPresent()`.
8. Check active temporary groups returned by `tableGroupRepository.findActiveGroupsForTable(scope, table.id())`; reject only groups with `groupType = "temporary"` and status `CREATED`, `LOCKED`, or `OCCUPIED`.
9. Check `preassignmentRepository.findActiveAssignmentForResource(scope, "dining_table", table.id().value(), businessDate)`; allow only when the assignment reservation id equals `allowedReservationId`, otherwise reject.
10. Generate `TMP-<sorted table codes>-<HHmm>` and append `-<first 8 chars of UUID>` when `existsActiveCode` returns true.
11. Save a `TableGroup` with `groupType = "temporary"` and `status = TableGroupStatus.OCCUPIED`.
12. Save one active `TableGroupMember` for each selected table.

Use this result creation:

```java
TableGroup group = new TableGroup(
    new TableGroupId(UUID.randomUUID()),
    command.scope(),
    uniqueGroupCode(command.scope(), memberTables),
    "temporary",
    new CapacityRange(capacityMin(memberTables), capacityMax(memberTables)),
    TableGroupStatus.OCCUPIED
);
TableGroup savedGroup = tableGroupRepository.save(command.scope(), group);
List<TableGroupMember> savedMembers = memberTables.stream()
    .map(table -> tableGroupRepository.saveMember(
        command.scope(),
        new TableGroupMember(UUID.randomUUID(), command.scope(), savedGroup.id(), table.id(), "member")
    ))
    .toList();
return TemporaryTableGroupResult.success(savedGroup, memberTables, savedMembers);
```

- [ ] **Step 7: Run the temporary group service test**

Run:

```powershell
mvn -Dtest=TemporaryTableGroupApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 8: Commit the shared service task**

Run:

```powershell
git add src/main/java/com/rpb/reservation/table src/test/java/com/rpb/reservation/table/application/TemporaryTableGroupApplicationServiceTest.java
git commit -m "feat: create temporary table groups for seating"
```

Expected: commit succeeds.

## Task 4: Reservation Arrived Direct Seating Integration

**Files:**
- Modify: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationArrivedDirectSeatingApplicationService.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/application/ReservationArrivedDirectSeatingApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiIntegrationTest.java`

- [ ] **Step 1: Write failing application tests**

Add these tests:

```java
@Test
void arrivedReservationCanSeatWithTemporaryTableGroup()

@Test
void temporaryTableGroupDuplicateMembersAreRejectedBeforePersistence()

@Test
void temporaryTableGroupMemberUnavailableIsRejected()

@Test
void temporaryTableGroupCapacityMismatchIsRejected()

@Test
void temporaryTableGroupPreassignmentConflictIsRejected()
```

The success test must assert:

```java
assertThat(result.success()).isTrue();
assertThat(result.resourceType()).isEqualTo("table_group");
assertThat(result.resourceId()).isEqualTo(scenario.tableGroupRepository.savedGroups.getFirst().id().value());
assertThat(scenario.tableGroupRepository.savedGroups.getFirst().groupType()).isEqualTo("temporary");
assertThat(result.occupiedTableIds()).containsExactlyInAnyOrder(A01_ID, A02_ID);
assertThat(result.events()).contains(
    "table_group.temporary.created",
    "table_group.temporary.locked",
    "table_group.temporary.occupied",
    "reservation.seated",
    "seating.created",
    "table.occupied"
);
```

- [ ] **Step 2: Run the failing reservation application tests**

Run:

```powershell
mvn -Dtest=ReservationArrivedDirectSeatingApplicationServiceTest test
```

Expected: FAIL because `temporaryTableIds` is not resolved.

- [ ] **Step 3: Inject and use the shared service**

Add `TemporaryTableGroupApplicationService temporaryTableGroupService` to constructors. In tests, pass a real service using fake repositories or a fake service that returns `TemporaryTableGroupResult` for the exact input.

In `resolveResource`, add this branch before `tableId`:

```java
if (!command.temporaryTableIds().isEmpty()) {
    TemporaryTableGroupResult result = temporaryTableGroupService.createForSeating(
        new CreateTemporaryTableGroupForSeatingCommand(
            scope,
            command.temporaryTableIds(),
            partySize,
            reservation.businessDate(),
            reservation.id().value()
        )
    );
    require(result.success(), mapTemporaryGroupError(result.error()));
    return ResourceSelection.temporaryGroup(result.group(), result.memberTables());
}
```

Extend the private `ResourceSelection` record with a `boolean temporaryGroup` field and this factory:

```java
static ResourceSelection temporaryGroup(TableGroup group, List<DiningTable> memberTables) {
    return new ResourceSelection(RESOURCE_GROUP, group.id().value(), null, group, List.copyOf(memberTables), true);
}
```

- [ ] **Step 4: Update reservation preassignment validation**

For temporary groups, reject an existing reservation-level preassignment because the UI will only offer temporary grouping when no assigned resource exists:

```java
if (selection.temporaryGroup()) {
    ReservationResourceAssignment ownAssignment = preassignmentRepository
        .findActiveAssignmentForReservation(scope, reservation.id().value())
        .orElse(null);
    if (ownAssignment != null) {
        throw new ApplicationFailure(ReservationArrivedDirectSeatingError.TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT);
    }
    return;
}
```

- [ ] **Step 5: Add temporary group evidence**

When `selection.temporaryGroup()` is true, include these event types before existing seating events:

```java
List<String> temporaryEvents = List.of(
    "table_group.temporary.created",
    "table_group.temporary.locked",
    "table_group.temporary.occupied"
);
```

State transition logs must include:

```java
newTransition("table_group", selection.resourceId(), "none", "created", "table_group.temporary.create", command, metadata(...));
newTransition("table_group", selection.resourceId(), "created", "locked", "table_group.temporary.lock", command, metadata(...));
newTransition("table_group", selection.resourceId(), "locked", "occupied", "table_group.temporary.occupy", command, metadata(...));
```

- [ ] **Step 6: Write reservation integration test**

In `ReservationArrivedDirectSeatingApiIntegrationTest`, add `arrivedReservationCanSeatTemporaryTableGroup`. Assert database state:

```sql
select group_type from table_groups where id = ?
```

Expected: `temporary`.

```sql
select status from table_groups where id = ?
```

Expected: `occupied`.

```sql
select count(*) from table_group_members where table_group_id = ? and deleted_at is null
```

Expected: `2`.

```sql
select count(*) from seating_resources where table_group_id = ? and status = 'active'
```

Expected: `1`.

Both selected `dining_tables.status` values must be `occupied`.

- [ ] **Step 7: Run reservation tests**

Run:

```powershell
mvn -Dtest=ReservationArrivedDirectSeatingApplicationServiceTest,ReservationArrivedDirectSeatingControllerTest,ReservationArrivedDirectSeatingApiIntegrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit the reservation integration task**

Run:

```powershell
git add src/main/java/com/rpb/reservation/reservation src/test/java/com/rpb/reservation/reservation
git commit -m "feat: seat arrived reservations with temporary table groups"
```

Expected: commit succeeds.

## Task 5: Called Queue Seating Integration

**Files:**
- Modify: `src/main/java/com/rpb/reservation/queue/application/service/SeatingFromCalledQueueApplicationService.java`
- Modify: `src/test/java/com/rpb/reservation/queue/application/SeatingFromCalledQueueApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiIntegrationTest.java`

- [ ] **Step 1: Write failing queue tests**

Add tests:

```java
@Test
void calledQueueTicketCanSeatWithTemporaryTableGroup()

@Test
void temporaryTableGroupDuplicateMembersAreRejectedForQueue()

@Test
void temporaryTableGroupMemberUnavailableIsRejectedForQueue()

@Test
void temporaryTableGroupCapacityMismatchIsRejectedForQueue()
```

The success assertion mirrors reservation but expects queue events:

```java
assertThat(result.events()).contains(
    "table_group.temporary.created",
    "table_group.temporary.locked",
    "table_group.temporary.occupied",
    "queue_ticket.seated",
    "reservation.seated",
    "seating.created",
    "table.occupied"
);
```

- [ ] **Step 2: Run the failing queue tests**

Run:

```powershell
mvn -Dtest=SeatingFromCalledQueueApplicationServiceTest test
```

Expected: FAIL because queue service does not resolve temporary members.

- [ ] **Step 3: Integrate the shared service**

Add the same constructor dependency and `resolveResource` branch as reservation, with `allowedReservationId` set to `reservation.id().value()` after the queue ticket's reservation is loaded:

```java
new CreateTemporaryTableGroupForSeatingCommand(
    scope,
    command.temporaryTableIds(),
    partySize,
    queueTicket.businessDate(),
    reservation.id().value()
)
```

Map `TemporaryTableGroupError` to `SeatingFromCalledQueueError`.

- [ ] **Step 4: Add temporary group evidence**

Add the same temporary group event and transition logs used in reservation. Queue-specific source transitions remain owned by `SeatingFromCalledQueueApplicationService`.

- [ ] **Step 5: Write queue integration test**

In `SeatingFromCalledQueueApiIntegrationTest`, add `calledQueueTicketCanSeatTemporaryTableGroup` and assert:

```java
assertThat(fixture.scalarString("select group_type from table_groups where id = ?", tableGroupId)).isEqualTo("temporary");
assertThat(fixture.scalarString("select status from table_groups where id = ?", tableGroupId)).isEqualTo("occupied");
assertThat(fixture.scalarInt("select count(*) from table_group_members where table_group_id = ? and deleted_at is null", tableGroupId)).isEqualTo(2);
assertThat(fixture.scalarString("select status from dining_tables where id = ?", firstTableId)).isEqualTo("occupied");
assertThat(fixture.scalarString("select status from dining_tables where id = ?", secondTableId)).isEqualTo("occupied");
```

- [ ] **Step 6: Run queue tests**

Run:

```powershell
mvn -Dtest=SeatingFromCalledQueueApplicationServiceTest,SeatingFromCalledQueueControllerTest,SeatingFromCalledQueueApiIntegrationTest test
```

Expected: PASS.

- [ ] **Step 7: Commit the queue integration task**

Run:

```powershell
git add src/main/java/com/rpb/reservation/queue src/test/java/com/rpb/reservation/queue
git commit -m "feat: seat called queue tickets with temporary table groups"
```

Expected: commit succeeds.

## Task 6: Walk-In Direct Seating Integration

**Files:**
- Modify: `src/main/java/com/rpb/reservation/walkin/application/service/WalkInDirectSeatingApplicationService.java`
- Modify: `src/test/java/com/rpb/reservation/walkin/application/WalkInDirectSeatingApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/walkin/integration/WalkInDirectSeatingApiIntegrationTest.java`

- [ ] **Step 1: Write failing walk-in tests**

Add tests:

```java
@Test
void walkInCanSeatWithTemporaryTableGroup()

@Test
void walkInTemporaryTableGroupDuplicateMembersAreRejected()

@Test
void walkInTemporaryTableGroupMemberUnavailableIsRejected()

@Test
void walkInTemporaryTableGroupCapacityMismatchIsRejected()

@Test
void existingWalkInAutoAssignmentStillWorksWhenNoResourceTargetIsProvided()
```

The success test must assert:

```java
assertThat(result.success()).isTrue();
assertThat(result.resourceType()).isEqualTo("table_group");
assertThat(scenario.tableGroupRepository.savedGroups.getFirst().groupType()).isEqualTo("temporary");
assertThat(scenario.diningTableRepository.tables.get(A01_ID).status()).isEqualTo(DiningTableStatus.OCCUPIED);
assertThat(scenario.diningTableRepository.tables.get(A02_ID).status()).isEqualTo(DiningTableStatus.OCCUPIED);
```

- [ ] **Step 2: Run the failing walk-in tests**

Run:

```powershell
mvn -Dtest=WalkInDirectSeatingApplicationServiceTest test
```

Expected: FAIL because walk-in service does not resolve or occupy temporary member tables.

- [ ] **Step 3: Integrate the shared service**

In `resolveResource`, branch on `!command.temporaryTableIds().isEmpty()` before single table and group selection:

```java
TemporaryTableGroupResult result = temporaryTableGroupService.createForSeating(
    new CreateTemporaryTableGroupForSeatingCommand(
        scope,
        command.temporaryTableIds(),
        partySize,
        businessDate,
        null
    )
);
require(result.success(), mapTemporaryGroupError(result.error()));
return ResourceSelection.temporaryGroup(result.group(), result.memberTables());
```

- [ ] **Step 4: Occupy member tables for group selections**

Replace the current single `occupiedTable` branch with a list:

```java
List<DiningTable> occupiedTables = occupyTables(scope, selection);
```

Implement:

```java
private List<DiningTable> occupyTables(StoreScope scope, ResourceSelection selection) {
    if (selection.table() != null) {
        return List.of(occupyTable(scope, selection.table()));
    }
    List<DiningTable> occupied = new ArrayList<>();
    for (DiningTable memberTable : selection.memberTables()) {
        occupied.add(occupyTable(scope, memberTable));
    }
    return occupied;
}
```

This fixes temporary group occupancy and preserves fixed group seating correctness.

- [ ] **Step 5: Add temporary group event evidence**

When `selection.temporaryGroup()` is true, prepend:

```java
"table_group.temporary.created",
"table_group.temporary.locked",
"table_group.temporary.occupied"
```

to the business events and transition logs before `walk_in.created`, `seating.created`, `table.locked`, and `table.occupied`.

- [ ] **Step 6: Write walk-in integration test**

In `WalkInDirectSeatingApiIntegrationTest`, add a request:

```java
new SeatWalkInDirectlyRequest(
    6,
    null,
    "Guest",
    null,
    null,
    null,
    null,
    List.of(firstTableId, secondTableId),
    null,
    null
)
```

Assert the generated `table_groups` row is `temporary` and `occupied`, the seating resource points to `table_group`, and both member tables are `occupied`.

- [ ] **Step 7: Run walk-in tests**

Run:

```powershell
mvn -Dtest=WalkInDirectSeatingApplicationServiceTest,WalkInDirectSeatingControllerTest,WalkInDirectSeatingApiIntegrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit the walk-in integration task**

Run:

```powershell
git add src/main/java/com/rpb/reservation/walkin src/test/java/com/rpb/reservation/walkin
git commit -m "feat: seat walk-ins with temporary table groups"
```

Expected: commit succeeds.

## Task 7: Cleaning Releases Temporary Groups

**Files:**
- Modify: `src/main/java/com/rpb/reservation/cleaning/application/service/CleaningApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/table/group/rule/DefaultTableGroupValidationRule.java`
- Modify: `src/test/java/com/rpb/reservation/cleaning/application/CleaningApplicationServiceTest.java`

- [ ] **Step 1: Write failing cleaning tests**

Add:

```java
@Test
void completingCleaningForTemporaryGroupReturnsMembersAvailableAndEndsGroup()

@Test
void fixedGroupCleaningDoesNotMutateGroupStatus()
```

The temporary test must assert:

```java
assertThat(result.success()).isTrue();
assertThat(scenario.tableGroupRepository.groups.get(scenario.group.id().value()).status()).isEqualTo(TableGroupStatus.ENDED);
assertThat(scenario.diningTableRepository.tables.values())
    .extracting(DiningTable::status)
    .containsOnly(DiningTableStatus.AVAILABLE);
assertThat(scenario.businessEventRepository.appended)
    .extracting(BusinessEvent::eventType)
    .contains("table_group.temporary.released", "table_group.temporary.ended");
```

- [ ] **Step 2: Run failing cleaning tests**

Run:

```powershell
mvn -Dtest=CleaningApplicationServiceTest test
```

Expected: FAIL because temporary group status is not ended and temporary release events are missing.

- [ ] **Step 3: Allow operational temporary groups in validation**

Update `DefaultTableGroupValidationRule.evaluate(group, members)`:

```java
boolean validStatus =
    ("fixed".equals(group.groupType()) && group.status() == TableGroupStatus.ACTIVE)
        || ("temporary".equals(group.groupType()) && group.status() == TableGroupStatus.OCCUPIED);
if (!validStatus || members == null || members.isEmpty()) {
    return RuleDecision.deny("invalid_table_group");
}
```

Keep `DefaultTableAvailabilityRule.evaluate(TableGroup)` strict for new seating:

```java
if (group == null || !"fixed".equals(group.groupType()) || group.status() != TableGroupStatus.ACTIVE) {
    return RuleDecision.deny("invalid_table_group");
}
```

- [ ] **Step 4: Persist temporary group end on cleaning completion**

In `CleaningApplicationService.executeComplete`, after member tables are transitioned to available:

```java
if (resource.temporaryGroup() != null) {
    tableGroupRepository.save(scope, new TableGroup(
        resource.temporaryGroup().id(),
        resource.temporaryGroup().scope(),
        resource.temporaryGroup().groupCode(),
        resource.temporaryGroup().groupType(),
        resource.temporaryGroup().capacity(),
        TableGroupStatus.ENDED
    ));
}
```

Extend `ResourceContext`:

```java
private record ResourceContext(String resourceType, UUID resourceId, List<DiningTable> tables, TableGroup temporaryGroup) {
}
```

For fixed groups pass `null`; for temporary groups pass the loaded group.

- [ ] **Step 5: Append temporary release evidence**

When `resource.temporaryGroup() != null`, include:

```java
new EventSpec("table_group.temporary.released", "table_group", resource.resourceId(), metadata(...)),
new EventSpec("table_group.temporary.ended", "table_group", resource.resourceId(), metadata(...))
```

and transitions:

```java
new TransitionSpec("table_group", resource.resourceId(), "occupied", "released", "table_group.temporary.release", metadata(...)),
new TransitionSpec("table_group", resource.resourceId(), "released", "ended", "table_group.temporary.end", metadata(...))
```

Also append per-member table transition logs for group resources:

```java
resource.tables().stream()
    .map(table -> new TransitionSpec("dining_table", table.id().value(), "cleaning", "available", "dining_table.available", metadata(...)))
```

- [ ] **Step 6: Run cleaning tests**

Run:

```powershell
mvn -Dtest=CleaningApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit the cleaning task**

Run:

```powershell
git add src/main/java/com/rpb/reservation/cleaning/application/service/CleaningApplicationService.java src/main/java/com/rpb/reservation/table/group/rule/DefaultTableGroupValidationRule.java src/main/java/com/rpb/reservation/table/rule/DefaultTableAvailabilityRule.java src/test/java/com/rpb/reservation/cleaning/application/CleaningApplicationServiceTest.java
git commit -m "feat: release temporary table groups after cleaning"
```

Expected: commit succeeds.

## Task 8: Table Resource List Exposes Group Type

**Files:**
- Modify: `docs/api/TABLE_RESOURCE_LIST_API_CONTRACT.md`
- Modify: `src/main/java/com/rpb/reservation/table/application/TableResourceItem.java`
- Modify: `src/main/java/com/rpb/reservation/table/api/TableResourceItemResponse.java`
- Modify: `src/main/java/com/rpb/reservation/table/api/TableResourceListController.java`
- Modify: `src/main/java/com/rpb/reservation/table/application/service/TableResourceListApplicationService.java`
- Modify: `src/types/tableResource.ts`
- Modify: `src/test/java/com/rpb/reservation/table/application/TableResourceListApplicationServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/table/api/TableResourceListControllerTest.java`

- [ ] **Step 1: Write failing table resource tests**

Add assertions:

```java
assertThat(groupItem.groupType()).isEqualTo("fixed");
```

and controller JSON:

```java
.andExpect(jsonPath("$.resources[?(@.resourceType == 'table_group')].groupType").value(hasItem("fixed")))
```

Add a temporary group fixture with status `occupied` and assert `groupType == "temporary"`.

- [ ] **Step 2: Run failing table resource tests**

Run:

```powershell
mvn -Dtest=TableResourceListApplicationServiceTest,TableResourceListControllerTest test
```

Expected: FAIL because `groupType` is not in the item or response.

- [ ] **Step 3: Add `groupType` to backend response**

Add `String groupType` after `resourceType` in both records:

```java
public record TableResourceItem(
    String resourceType,
    String groupType,
    UUID resourceId,
    ...
)
```

For dining tables, pass `null`. For table groups, pass `group.groupType()`.

Update `TableResourceItemResponse` and `TableResourceListController.toResponse` with the same field order.

- [ ] **Step 4: Add `groupType` to frontend types**

In `src/types/tableResource.ts`:

```ts
export type TableGroupType = 'fixed' | 'temporary'

export interface TableResourceItem {
  resourceType: 'dining_table' | 'table_group'
  groupType?: TableGroupType | null
  resourceId: string
  ...
}
```

- [ ] **Step 5: Update the table resource contract**

Document:

```json
{
  "resourceType": "table_group",
  "groupType": "temporary",
  "status": "occupied",
  "memberTableCodes": ["A01", "A02"]
}
```

- [ ] **Step 6: Run table resource tests**

Run:

```powershell
mvn -Dtest=TableResourceListApplicationServiceTest,TableResourceListControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit the table resource task**

Run:

```powershell
git add docs/api/TABLE_RESOURCE_LIST_API_CONTRACT.md src/main/java/com/rpb/reservation/table src/types/tableResource.ts src/test/java/com/rpb/reservation/table
git commit -m "feat: expose table group type in table resources"
```

Expected: commit succeeds.

## Task 9: Frontend API Types And Payloads

**Files:**
- Modify: `src/types/reservationArrivedDirectSeating.ts`
- Modify: `src/api/reservationArrivedDirectSeatingApi.ts`
- Modify: `src/types/seatingFromCalledQueue.ts`
- Modify: `src/api/seatingFromCalledQueueApi.ts`
- Modify: `src/types/walkInDirectSeating.ts`
- Modify: `src/api/walkInDirectSeatingApi.ts`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java`

- [ ] **Step 1: Write failing UI source validation test**

In `StaffUiV12TableSelectionValidationTest`, add a method:

```java
@Test
void seatingApiClientsSendTemporaryTableIds() throws Exception {
    String source = readSources(List.of(
        Path.of("src", "types", "reservationArrivedDirectSeating.ts"),
        Path.of("src", "api", "reservationArrivedDirectSeatingApi.ts"),
        Path.of("src", "types", "seatingFromCalledQueue.ts"),
        Path.of("src", "api", "seatingFromCalledQueueApi.ts"),
        Path.of("src", "types", "walkInDirectSeating.ts"),
        Path.of("src", "api", "walkInDirectSeatingApi.ts")
    ));

    assertThat(source)
        .contains("temporaryTableIds?: string[] | null")
        .contains("temporaryTableIds: request.temporaryTableIds")
        .contains("addOptionalArrayField");
}
```

- [ ] **Step 2: Run failing UI source test**

Run:

```powershell
mvn -Dtest=StaffUiV12TableSelectionValidationTest test
```

Expected: FAIL because frontend request types do not expose `temporaryTableIds`.

- [ ] **Step 3: Update TypeScript request types**

Add this field to all three request interfaces:

```ts
temporaryTableIds?: string[] | null
```

- [ ] **Step 4: Serialize temporary ids**

For reservation and walk-in API bodies:

```ts
temporaryTableIds: request.temporaryTableIds?.filter(Boolean) ?? null
```

For queue API, add:

```ts
addOptionalArrayField(body, 'temporaryTableIds', request.temporaryTableIds)
```

with:

```ts
function addOptionalArrayField(
  body: SeatCalledQueueTicketRequest,
  key: 'temporaryTableIds',
  value: string[] | null | undefined
): void {
  const values = (value ?? []).map(item => item.trim()).filter(Boolean)

  if (values.length) {
    body[key] = values
  }
}
```

- [ ] **Step 5: Run TypeScript build**

Run:

```powershell
npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit frontend API payload task**

Run:

```powershell
git add src/types/reservationArrivedDirectSeating.ts src/api/reservationArrivedDirectSeatingApi.ts src/types/seatingFromCalledQueue.ts src/api/seatingFromCalledQueueApi.ts src/types/walkInDirectSeating.ts src/api/walkInDirectSeatingApi.ts src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java
git commit -m "feat: send temporary table ids from seating clients"
```

Expected: commit succeeds.

## Task 10: TableResourcePicker Multi-Select Mode

**Files:**
- Modify: `src/components/staff-table/TableResourcePicker.vue`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java`

- [ ] **Step 1: Write failing UI validation test**

Add:

```java
@Test
void tableResourcePickerSupportsTemporaryGroupSelection() throws Exception {
    String source = Files.readString(Path.of("src", "components", "staff-table", "TableResourcePicker.vue"));

    assertThat(source)
        .contains("allowTemporaryGroup?: boolean")
        .contains("selectedTemporaryTableIds?: string[]")
        .contains("'select-temporary-tables': [tableIds: string[]]")
        .contains("selectionMode")
        .contains("temporary-group")
        .contains("toggleTemporaryTable")
        .contains("combinedCapacityText")
        .contains("组合入桌");
}
```

- [ ] **Step 2: Run failing UI validation test**

Run:

```powershell
mvn -Dtest=StaffUiV12TableSelectionValidationTest#tableResourcePickerSupportsTemporaryGroupSelection test
```

Expected: FAIL because the picker only supports single resources.

- [ ] **Step 3: Add picker props and emit**

Add props:

```ts
allowTemporaryGroup?: boolean
selectedTemporaryTableIds?: string[]
```

Add emit:

```ts
'select-temporary-tables': [tableIds: string[]]
```

Add mode:

```ts
const selectionMode = ref<'single' | 'temporary-group'>('single')
```

- [ ] **Step 4: Add temporary selection helpers**

```ts
const selectedTemporaryIds = computed(() => props.selectedTemporaryTableIds ?? [])
const selectedTemporaryResources = computed(() =>
  tableResources.value.filter(resource => selectedTemporaryIds.value.includes(resource.resourceId))
)
const combinedCapacityText = computed(() => {
  const min = selectedTemporaryResources.value.reduce((sum, resource) => sum + resource.capacityMin, 0)
  const max = selectedTemporaryResources.value.reduce((sum, resource) => sum + resource.capacityMax, 0)
  return selectedTemporaryResources.value.length ? `${min}-${max}人` : '0人'
})

function toggleTemporaryTable(resource: TableResourceItem): void {
  if (!resource.selectable || resource.resourceType !== 'dining_table') {
    return
  }
  const current = new Set(selectedTemporaryIds.value)
  if (current.has(resource.resourceId)) {
    current.delete(resource.resourceId)
  } else {
    current.add(resource.resourceId)
  }
  emit('select-temporary-tables', Array.from(current))
}
```

- [ ] **Step 5: Keep single selection behavior unchanged**

`chooseResource` must route by mode:

```ts
if (selectionMode.value === 'temporary-group') {
  toggleTemporaryTable(resource)
  return
}
```

Fixed group cards remain visible only in single mode:

```vue
<section v-if="selectionMode === 'single' && groupResources.length" ...>
```

- [ ] **Step 6: Run frontend build and UI validation**

Run:

```powershell
npm run build
mvn -Dtest=StaffUiV12TableSelectionValidationTest test
```

Expected: PASS.

- [ ] **Step 7: Commit picker task**

Run:

```powershell
git add src/components/staff-table/TableResourcePicker.vue src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java
git commit -m "feat: support temporary group selection in table picker"
```

Expected: commit succeeds.

## Task 11: Wire Temporary Groups Into Seating Screens

**Files:**
- Modify: `src/components/reservation-workbench/ReservationSeatDialog.vue`
- Modify: `src/pages/ReservationArrivedDirectSeatingPage.vue`
- Modify: `src/pages/SeatingFromCalledQueuePage.vue`
- Modify: `src/pages/WalkInDirectSeatingPage.vue`
- Modify: `src/pages/TableResourceListPage.vue`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java`

- [ ] **Step 1: Write failing UI validation test**

Add:

```java
@Test
void seatingScreensSubmitTemporaryTableIds() throws Exception {
    String source = readSources(List.of(
        Path.of("src", "components", "reservation-workbench", "ReservationSeatDialog.vue"),
        Path.of("src", "pages", "ReservationArrivedDirectSeatingPage.vue"),
        Path.of("src", "pages", "SeatingFromCalledQueuePage.vue"),
        Path.of("src", "pages", "WalkInDirectSeatingPage.vue"),
        Path.of("src", "pages", "TableResourceListPage.vue")
    ));

    assertThat(source)
        .contains("temporaryTableIds")
        .contains("@select-temporary-tables")
        .contains("hasExactlyOneResource")
        .contains("temporaryGroupingMode")
        .contains("walkInTemporaryGroupRoute")
        .contains("组合入桌");
}
```

- [ ] **Step 2: Run failing UI validation test**

Run:

```powershell
mvn -Dtest=StaffUiV12TableSelectionValidationTest#seatingScreensSubmitTemporaryTableIds test
```

Expected: FAIL because screens do not track temporary member ids.

- [ ] **Step 3: Update reservation seat dialog**

Add state:

```ts
const selectedTemporaryTableIds = ref<string[]>([])
```

Add handler:

```ts
function selectTemporaryTables(tableIds: string[]): void {
  selectedTemporaryTableIds.value = tableIds
  selectedTableId.value = ''
  selectedTableGroupId.value = ''
}
```

Update `hasExactlyOneResource`:

```ts
function hasExactlyOneResource(): boolean {
  const selectedCount = [
    !!selectedTableId.value.trim(),
    !!selectedTableGroupId.value.trim(),
    selectedTemporaryTableIds.value.length >= 2
  ].filter(Boolean).length
  return selectedCount === 1
}
```

Update request:

```ts
temporaryTableIds: selectedTemporaryTableIds.value.length ? selectedTemporaryTableIds.value : null
```

Pass picker props:

```vue
allow-temporary-group
:selected-temporary-table-ids="selectedTemporaryTableIds"
@select-temporary-tables="selectTemporaryTables"
```

- [ ] **Step 4: Update standalone reservation and queue seating pages**

Use the same state, handler, `hasExactlyOneResource`, request payload, and picker bindings as the dialog. Keep existing manual `tableId` and `tableGroupId` fields; when either manual field changes, clear `temporaryTableIds`.

- [ ] **Step 5: Update walk-in direct seating page**

Add `temporaryTableIds` to the form and route handling:

```ts
temporaryTableIds: [] as string[]
```

Read route query arrays:

```ts
function asStringArray(value: unknown): string[] {
  const values = Array.isArray(value) ? value : value ? [value] : []
  return values.filter((item): item is string => typeof item === 'string').map(item => item.trim()).filter(Boolean)
}
```

In `applyRouteSelection`:

```ts
const temporaryTableIds = asStringArray(route.query.temporaryTableIds)
if (temporaryTableIds.length >= 2) {
  selectTemporaryTables(temporaryTableIds)
}
```

In `validateForm`, reject simultaneous single resource and temporary selection:

```ts
const targetCount = [
  !!form.tableId.trim(),
  !!form.tableGroupId.trim(),
  form.temporaryTableIds.length >= 2
].filter(Boolean).length
if (targetCount > 1) {
  return createLocalError('SEATING_RESOURCE_INVALID', 'walkin.direct_seating.resource_invalid')
}
```

- [ ] **Step 6: Add table page grouping mode**

Add state:

```ts
const temporaryGroupingMode = ref(false)
const temporaryGroupTableIds = ref<string[]>([])
```

Add route builder:

```ts
function walkInTemporaryGroupRoute(): Record<string, unknown> {
  return {
    name: 'walk-in-direct-seating',
    params: { storeId: storeId.value },
    query: {
      temporaryTableIds: temporaryGroupTableIds.value,
      partySize: temporaryGroupCapacityMin.value
    }
  }
}
```

The table page action should be disabled until at least two available dining tables are selected. Existing single-resource actions continue using `walkInDirectSeatingRoute(resource)`.

- [ ] **Step 7: Run UI validation and frontend build**

Run:

```powershell
mvn -Dtest=StaffUiV12TableSelectionValidationTest test
npm run build
```

Expected: PASS.

- [ ] **Step 8: Commit seating screen task**

Run:

```powershell
git add src/components/reservation-workbench/ReservationSeatDialog.vue src/pages/ReservationArrivedDirectSeatingPage.vue src/pages/SeatingFromCalledQueuePage.vue src/pages/WalkInDirectSeatingPage.vue src/pages/TableResourceListPage.vue src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java
git commit -m "feat: wire temporary table groups into seating screens"
```

Expected: commit succeeds.

## Task 12: Final Verification, API Review Notes, And Release Note

**Files:**
- Create: `docs/release-notes/2026-06-24-temporary-table-groups-phase-1.md`
- Modify: API and frontend contract docs touched in prior tasks if verification finds drift.

- [ ] **Step 1: Run targeted backend tests**

Run:

```powershell
mvn -Dtest=TemporaryTableGroupApplicationServiceTest,ReservationArrivedDirectSeatingApplicationServiceTest,ReservationArrivedDirectSeatingControllerTest,ReservationArrivedDirectSeatingApiIntegrationTest,SeatingFromCalledQueueApplicationServiceTest,SeatingFromCalledQueueControllerTest,SeatingFromCalledQueueApiIntegrationTest,WalkInDirectSeatingApplicationServiceTest,WalkInDirectSeatingControllerTest,WalkInDirectSeatingApiIntegrationTest,CleaningApplicationServiceTest,TableResourceListApplicationServiceTest,TableResourceListControllerTest,StaffUiV12TableSelectionValidationTest test
```

Expected: PASS.

- [ ] **Step 2: Run full backend tests**

Run:

```powershell
mvn test
```

Expected: PASS.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm run build
```

Expected: PASS.

- [ ] **Step 4: Write API review notes**

Append a short "API Review Notes" section to each changed seating API contract:

```markdown
## API Review Notes For Temporary Table Groups

- Path remains `/api/v1`.
- Request body is backward compatible for existing `tableId` and `tableGroupId` callers.
- `temporaryTableIds` is optional and exclusive with existing resource target fields.
- Existing App Gate permissions are unchanged.
- Idempotency hash includes sorted `temporaryTableIds`.
- Response still returns the created seating resource as `resourceType = table_group` and the generated temporary group id.
```

- [ ] **Step 5: Write release note**

Create `docs/release-notes/2026-06-24-temporary-table-groups-phase-1.md`:

```markdown
# Release Notes

## Version / Date
2026-06-24 temporary table groups phase 1

## New
- Staff can combine two or more available dining tables during reservation seating, queue seating, or walk-in direct seating.
- Successful temporary grouping creates a `temporary` TableGroup and active member rows in the same seating transaction.
- The table picker supports a temporary group multi-select mode.
- Table resource list responses include `groupType` for table groups.

## Changed
- Seating request contracts accept `temporaryTableIds`.
- Cleaning completion ends temporary groups after member tables return to available.

## Fixed
- Walk-in table group seating now occupies member tables instead of only the group resource.

## Migration
- No Flyway migration. Existing `table_groups`, `table_group_members`, `seating_resources`, `dining_tables`, and `cleanings` tables are reused.

## Permission
- No new App Gate permission. Existing seating, table view, and cleaning permissions guard the new behavior.

## Risk
- Concurrency risk is concentrated around member table locks, active seating, active cleaning, and same-day preassignment checks.
- API compatibility risk is low for existing callers because existing fields are unchanged.

## Rollback Notes
- Roll back the code changes. Existing temporary table group rows created before rollback remain historical `table_group` resources and can be inspected through database support tooling.
```

- [ ] **Step 6: Review for forbidden marker text and contract drift**

Run:

```powershell
$terms = @('TB' + 'D', 'TO' + 'DO', 'implement' + ' later', 'fill in' + ' details', 'Similar' + ' to', 'add' + ' appropriate')
foreach ($term in $terms) {
  rg -n --fixed-strings $term docs/api docs/release-notes src/main/java src/types src/api src/components src/pages src/test/java
}
```

Expected: no matches introduced by this feature.

- [ ] **Step 7: Commit verification docs**

Run:

```powershell
git add docs/api docs/release-notes
git commit -m "docs: document temporary table group release"
```

Expected: commit succeeds.

## Final Acceptance Checklist

- [ ] Staff can create a temporary group only as part of reservation, queue, or walk-in seating.
- [ ] `temporaryTableIds` rejects fewer than two members, duplicates, unavailable members, active locks, active occupancies, active cleanings, active temporary group conflicts, and same-day preassignment conflicts.
- [ ] Successful seating creates a `temporary` `table_groups` row, member rows, one active `seating_resources` row, and occupied member tables.
- [ ] Cleaning completion returns member tables to `available` and persists the temporary group as `ended`.
- [ ] Fixed group behavior still passes existing tests.
- [ ] Table resource list exposes `groupType`.
- [ ] Reservation creation still supports only existing single table or fixed group preassignment and does not create temporary groups.
- [ ] No new permission or migration is required.
- [ ] `mvn test` passes.
- [ ] `npm run build` passes.
