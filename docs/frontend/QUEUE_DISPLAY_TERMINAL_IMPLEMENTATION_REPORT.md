# Queue Display Terminal Implementation Report

## Scope

Implemented queue display terminal at `/stores/:storeId/queue-display`.

## Changed Files

- `src/types/queueDisplay.ts`
- `src/api/queueDisplayApi.ts`
- `src/pages/QueueDisplayPage.vue`
- `src/router/index.ts`
- `src/test/java/com/rpb/reservation/appgate/ui/QueueDisplayCallScreenUiImplementationValidationTest.java`
- Existing queue UI boundary validation tests updated to allow the approved Queue Display page and API client.

## API Endpoints

- `GET /api/v1/stores/{storeId}/queue-display/state`
- `GET /api/v1/stores/{storeId}/queue-display/media/{assetId}`

The frontend reads the backend projection only. It does not call, skip, seat, cancel, or otherwise mutate queue state.

## Permission

- App Gate app key: `reservation_queue`
- Permission: `queue.display.view`

## Runtime Behavior

- Loading state: stable dark terminal screen while connecting.
- Calling state: shows current call number, safe customer display name, party-size group, waiting count, and waiting preview.
- Advertising state: rotates API-provided text or image/video media slides using `slideDurationSeconds`.
- Error state: keeps last good payload for a short grace window, then shows a restrained offline state.

## Validation

- `npm run build`: PASS
- `mvn "-Dtest=QueueDisplayCallScreenUiImplementationValidationTest,QueueCallUiImplementationValidationTest,QueueSkipUiImplementationValidationTest,QueueRejoinUiImplementationValidationTest,ReservationArrivedToQueueUiImplementationValidationTest,SeatingFromCalledQueueUiImplementationValidationTest" test`: PASS

## Known Limitations

- The terminal requires the normal authenticated browser session in Phase 1.
- Dedicated unattended device activation tokens remain a later design item.
