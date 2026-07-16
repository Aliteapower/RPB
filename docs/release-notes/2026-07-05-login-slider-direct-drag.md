# Release Notes

## Version / Date
2026-07-05 login slider direct drag polish

## New
None.

## Changed
- Login slider captcha now uses the arrow puzzle piece as the direct draggable control.
- The refresh action is shown inside the captcha image area instead of sharing a separate row with a small range input.
- Keyboard calibration remains available on the slider handle through arrow keys, Home, and End.

## Fixed
- Improved mobile usability by removing the small standalone slider row below the captcha image.

## Migration
No database migration.

## Permission
No App Gate or permission change.

## Risk
Low frontend-only risk. The submitted `captchaX` payload and captcha API contract are unchanged.

## Rollback Notes
Revert `src/pages/LoginPage.vue`, `src/test/java/com/rpb/reservation/appgate/ui/AuthLoginUiValidationTest.java`, and this release note.
