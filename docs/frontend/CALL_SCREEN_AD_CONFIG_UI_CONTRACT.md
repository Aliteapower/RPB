# Call Screen Ad Config UI Contract

## Route

Tenant admin UI is expected to manage:

```http
/api/v1/stores/{storeId}/tenant-admin/call-screen/settings
/api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets
```

The backend requires `tenant_admin` plus `tenant.admin.manage`.

## Required Controls

`active ad type selector`: lets the tenant choose between text mode and image/video media mode.

`active ad set selector`: chooses the tenant-owned ad set to activate for the current store.

`text slide editor`: edits title, subtitle, tagline, status, and versioned text slide fields.

`sort order field`: numeric field for slide ordering. Values must be greater than 0.

`enable/disable status`: controls whether an ad set or slide is active.

`media upload`: accepts supported image/video files, adds uploaded assets to the current media carousel, and shows saving/uploading/error states.

`preview panel`: renders the selected text set or media carousel in a dark call-screen preview.

## Tenant And Store Isolation

The UI must not ask the user for a tenant id. Tenant scope comes from the authenticated session. Store scope comes from the route `{storeId}`. Tenant ad sets are reusable tenant-level data, while `store_call_screen_settings` selects one active set per store.

## Media Scope

The tenant page may render file input, upload action, image/video preview, media carousel ordering, and media slide status controls. Uploaded tenant assets remain tenant-scoped and can only be served when referenced by the store's active media ad set.

Platform-owned seed template maintenance is a separate platform backend page at `/platform/call-screen/text-seed`. This tenant page edits tenant-owned copies only.
