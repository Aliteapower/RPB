# Reservation Meal Period API Contract

## Platform Seed

`GET /api/v1/platform/reservation/meal-period-seed`

Returns the platform default meal periods.

`PATCH /api/v1/platform/reservation/meal-period-seed`

Requires role `platform_admin` and permission `platform.reservation_meal_period.manage`.

Request:

```json
{
  "periods": [
    {
      "id": "optional-existing-id",
      "periodKey": "lunch",
      "displayName": "午餐",
      "startTime": "11:00",
      "endTime": "15:00",
      "crossesNextDay": false,
      "slotIntervalMinutes": 30,
      "status": "active",
      "sortOrder": 10,
      "version": 0
    }
  ]
}
```

## Tenant Store Meal Periods

`GET /api/v1/stores/{storeId}/tenant-admin/reservation-meal-periods`

Returns the store setting, editable store periods, platform seed periods, and effective periods.

`PATCH /api/v1/stores/{storeId}/tenant-admin/reservation-meal-periods`

Requires tenant admin scope for the store.

Request:

```json
{
  "usePlatformSeed": false,
  "periods": [
    {
      "id": "optional-existing-id",
      "periodKey": "dinner",
      "displayName": "晚餐",
      "startTime": "17:00",
      "endTime": "00:30",
      "crossesNextDay": true,
      "slotIntervalMinutes": 30,
      "status": "active",
      "sortOrder": 20,
      "version": 0
    }
  ]
}
```

When `usePlatformSeed=true`, the submitted store period list is ignored and the effective periods come from platform seed.

## Reservation Time Slots

`GET /api/v1/stores/{storeId}/reservations/time-slots?businessDate=2026-06-29`

Requires App Gate `reservation_queue` and permission `reservation.create`.

Response:

```json
{
  "success": true,
  "businessDate": "2026-06-29",
  "slots": [
    {
      "periodKey": "lunch",
      "periodName": "午餐",
      "time": "11:00",
      "label": "11:00",
      "startAt": "2026-06-29T03:00:00Z",
      "nextDay": false,
      "selectable": true
    },
    {
      "periodKey": "dinner",
      "periodName": "晚餐",
      "time": "00:30",
      "label": "次日 00:30",
      "startAt": "2026-06-29T16:30:00Z",
      "nextDay": true,
      "selectable": true
    }
  ]
}
```

## Reservation Create Compatibility

`POST /api/v1/stores/{storeId}/reservations` accepts optional `businessDate`. If present, cross-day slot creation stores the selected service date in `reservations.business_date`; if absent, the service keeps the previous behavior and derives business date from `reservedStartAt` in store timezone.
