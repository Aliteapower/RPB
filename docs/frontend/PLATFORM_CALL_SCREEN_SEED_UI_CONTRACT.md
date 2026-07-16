# Platform Call Screen Seed UI Contract

## Route

```text
/platform/call-screen/text-seed
```

Route name:

```text
platform-call-screen-text-seed
```

The route requires platform-admin navigation guard access.

## Required Controls

`text seed editor`: edits the platform default text seed template name, status, title, subtitle, tagline, sort order, and slide status.

`add text slide`: adds a new text slide row to the submitted text seed payload.

`version display`: shows seed and slide versions so stale-write conflicts are understandable.

`preview panel`: renders the first active slide in the dark terminal visual style.

`media seed editor`: edits the platform-owned image/video seed template, including upload, media slide ordering, status, title, and alt text.

## Scope Rules

The page must not ask for tenant id or store id. Platform seed templates are platform-owned. Tenant admins edit tenant-owned copies from the tenant admin call-screen page.

Saving the platform seed affects the platform template only. Existing tenant copies remain unchanged.

## Empty / Loading / Error States

The page must provide stable loading, success, and error banners. `FORBIDDEN`, `REQUEST_INVALID`, `SEED_NOT_FOUND`, and `VERSION_CONFLICT` should map to clear Chinese copy.

## Media Scope

The page exposes platform media upload and image/video seed editing through the real platform media seed APIs. Platform assets remain platform-owned and are not shared as tenant-owned files.
