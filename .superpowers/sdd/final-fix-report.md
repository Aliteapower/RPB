# Final Fix Report

## Scope

Fix the idempotency snapshot decode contract so a valid JSON literal `null` never escapes as a Java `null` result.

## RED Evidence

Command:

```powershell
mvn -q "-Dtest=JacksonIdempotencySnapshotCodecTest" test
```

Result: failed as expected. Surefire recorded `tests=3`, `failures=1`, and `errors=0`.

The new `rejectsJsonNullWithDecodeFailure` assertion failed with:

```text
Expecting code to raise a throwable.
```

## GREEN Evidence

Command:

```powershell
mvn -q "-Dtest=JacksonIdempotencySnapshotCodecTest" test
```

Result: passed. Surefire recorded `tests=3`, `failures=0`, `errors=0`, and `skipped=0`.

`decode("null")` now throws `IdempotencySnapshotException` with the stable message `idempotency_snapshot_decode_failed`.

## Foundation Regression Gate

Command:

```powershell
mvn -q "-Dtest=JacksonIdempotencySnapshotCodecTest,ReservationArrivedDirectSeatingSnapshotCodecTest,SeatingFromCalledQueueSnapshotCodecTest,WalkInDirectSeatingSnapshotCodecTest,StoreBusinessTimeTest" test
```

Result: passed with `tests=11`, `failures=0`, `errors=0`, and `skipped=0` across the five classes. The count is 11 because this fix adds one regression assertion to the previous 10-test gate.

## Compile and Diff

```powershell
mvn -q -DskipTests compile
git diff --check
```

Both commands succeeded. `git diff --check` reported only the repository's existing LF-to-CRLF warnings; no whitespace errors were reported.

## Files

- `src/main/java/com/rpb/reservation/idempotency/application/codec/JacksonIdempotencySnapshotCodec.java`
- `src/test/java/com/rpb/reservation/idempotency/application/codec/JacksonIdempotencySnapshotCodecTest.java`
- `.superpowers/sdd/final-fix-report.md`

## Commit

`fix: reject null idempotency snapshots`

## Concerns

None. The change preserves all existing decode failure handling and only rejects a null deserialization result.
