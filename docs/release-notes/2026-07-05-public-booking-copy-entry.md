# Public Booking Entry Copy Fallback

## Version / Date
2026-07-05

## Changed
- Tenant public booking settings now labels the copy action as `复制公网预约入口` so the action is explicit.

## Fixed
- Copying the public booking entry now falls back to a textarea copy flow when the browser blocks `navigator.clipboard.writeText`, which can happen on the current HTTP public deployment.
- Successful copy feedback now says `公网预约入口已复制`; failure feedback includes the URL for manual copying.

## Migration
- No database migration.
- No API contract change.

## Permission
- No App Gate or role permission change.

## Risk
- Low frontend-only risk. The change is limited to the tenant admin public booking copy button.

## Rollback Notes
- Roll back by restoring the previous `/opt/rpb/frontend` static bundle backup.
