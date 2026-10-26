# Shared customer registration and PDPA registration records

Scope: platform customer module, reused by tenant administration and wallet presentation. No product-specific customer data source. Existing tenant_admin + tenant.admin.manage and authorized store checks remain mandatory.

## Contract

Base `/api/v1/stores/{storeId}/tenant-admin`.

- GET `/customer-privacy-notice`: authenticated administrator; returns `{version, textZh, textEn, labelZh, labelEn}`. Server-owned notice version `customer-profile-v2`. Purposes: customer identification/contact, requested services and associated transaction records. The current registration notice includes consent to marketing messages or authorization for wallet payments, per the confirmed business rule. This does not bypass transaction-specific wallet authorization, ownership, balance, or idempotency checks.
- POST `/customers`: existing profile fields plus required `privacyConsent: {accepted:true, noticeVersion:'customer-profile-v2', entrySource:'wallet'|'tenant_admin'}`. Missing, false, stale version or invalid source returns existing 400 REQUEST_INVALID without creating a customer or consent row. Server gets tenant, store, actor and time from the authenticated scope and server clock. Successful creation and consent registration commit atomically; duplicate phone remains 409 CUSTOMER_PHONE_CONFLICT and does not create another record.
- PATCH `/customers/{id}`: existing profile maintenance contract; no new consent is inferred or rewritten by editing a profile. Privacy checkbox appears only for creation.
- GET `/customers/{id}/privacy-consents`: administrator in the same tenant and authorized store; existing active management record required, returns `{records:[{id,customerId,storeId,accepted,recordedBy,recordedByCustomerId,recordedByName,recordedAt,entrySource,confirmationMethod,noticeVersion,noticeTextZh,noticeTextEn}]}` ordered recordedAt DESC,id DESC. Merchant records use `merchant_attestation` with the authenticated staff actor; self-registration records use `customer_registration_submission` with the newly created customer ID, and do not claim verified phone ownership. No record means no recorded confirmation, never auto-accepted. Archiving retains stored evidence; this endpoint follows existing customer lookup rules and is not an archived-customer audit search.

The checkbox is initially checked as requested, but is saved only upon explicit merchant form submission. Cancellation prevents save. Label describes merchant registration of notice/consent; it does not claim that the customer clicked the checkbox. The record alone does not prove compliance or authorize unrelated use. Existing customers are not backfilled.

Frontend form controls use server notice metadata. The public customer module owns operations, fields and record presentation; wallet owns its visual wrapper and navigation. The tenant admin table and wallet cards use the same operations and API.

## Customer self-registration

- Public GET `/api/v1/public/customer-auth/registration-notice?storeId=…` resolves an active store and returns server-owned notice metadata with a customer-facing confirmation label.
- Public POST `/api/v1/public/customer-auth/register`: `{storeId,displayName?,nickname?,phoneE164,email?,privacyConsent:{accepted:true,noticeVersion}}`, returns 201 `{success:true,customerId}`. Tenant/store are resolved server-side from the active store; customer ID and source are server-generated. No merchant session is required.
- Existing phone: 409 `phone_already_registered`, never attach to, overwrite or reset that profile. Invalid/missing/unchecked/stale notice: 400 `request_invalid`; cross-site submission: 403; existing rate-limit counters: 429 `rate_limited`; persistence failure: 500 `persistence_error`. Normal phone uniqueness also protects concurrent creations.
- Reuse password-auth rate limit storage with a separate registration subject; count attempts in an independent transaction so rejected registration cannot roll back throttling. Successful registration and its confirmation row commit together.
- No SMS integration or automatic phone ownership verification. Registration creates only the profile and its consent evidence, never a usable password, auth session, wallet or points account. The phone-suffix password is disabled. Customers without a chosen password or verified recovery email visit the store for in-person verification, receive a one-time code from authorized customer-management staff, and set their own password. See [in-person activation](2026-10-26-customer-in-person-activation.md). Wallet refreshes the authenticated workspace only after successful sign-in.
- The checkbox defaults to checked as requested; unchecking prevents registration. The self-submission record is distinct from merchant registration; both use the same server-owned purposes and current consent wording. Transaction-specific wallet checks still apply.
- Wallet page `/wallet/{storeId}/register`; generic platform page `/customers/register?storeId=…`. Merchant management displays a shareable registration URL/QR; customer login also links to registration. The common form has no wallet-provider dependency and can be embedded by other product lines.

## Migration and compatibility

V067 adds application-append-only `customer_privacy_consents`, tenant/customer and tenant/store composite foreign keys, server timestamp, actual merchant actor or self-registering customer, operator name and notice snapshots, source/method consistency constraints, and an index on tenant/customer/time/id. Self-submission requires recorded_by_customer_id=customer_id and no merchant actor; merchant registration requires an actual staff actor and no self-registering actor. No customer or wallet balances are altered and no historical consent is fabricated. One registration record per customer; future withdrawal/reconsent requires a separate explicit operation, not profile editing. Existing reservation/identity resolution behavior is preserved and is not silently labelled as a recorded PDPA confirmation.

Preexisting open create forms without privacyConsent are rejected until refreshed; do not silently default missing API acceptance to true. Existing list/edit/archive APIs and other product lines' customer identity resolution remain unchanged. Deployment requires backend V067 before new frontend. Rollback the frontend/backend together if needed, retain the additive table and its records; never delete evidence or roll back customer business data.

## Validation

Cover missing/false/version/source validation before any writes, atomic persistence failure rollback, duplicate phone, unauthorized/anonymous/cross-tenant access, readback of actor/time/text/source, independent wallet/points balances, UI default checked and unchecked disabled, and no historical backfill. Use the local PostgreSQL pointer exclusively and isolated schemas.

Reference for purpose/consent distinctions: [Singapore PDPA, Part 4](https://sso.agc.gov.sg/Act/PDPA2012?ProvIds=P14-P21-). The application's notice text is an operational service notice; it is not a certification of legal compliance.

## 2026-10-26 consistency update

Current notice is `customer-profile-v2`; its Chinese/English text retains the explicitly requested inclusion of marketing and wallet payment authorization. Existing `customer-profile-v1` records and their stored body snapshots remain unchanged. Stale forms must reload. Registration itself does not create a payment, bypass a per-transaction check, or certify legal compliance. The later confirmed in-person activation rule supersedes phone-suffix initial login; see [password security contract](2026-10-26-customer-password-security.md).
