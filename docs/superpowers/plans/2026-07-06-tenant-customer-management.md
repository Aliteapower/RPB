# Tenant Customer Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add tenant-admin Customer maintenance and make staff/public reservation creation create or refresh Customer phone, name, salutation, and optional email.

**Architecture:** Customer profile rules live inside the existing `customer` module. Tenant-admin APIs resolve store access but delegate Customer behavior to Customer application services; reservation and public-booking flows call a reusable Customer profile resolver instead of touching Customer persistence directly.

**Tech Stack:** Java 21, Spring Boot 3, PostgreSQL/JdbcTemplate/JPA, Vue 3, TypeScript, Vite, vue-i18n, JUnit, AssertJ.

---

## File Structure

- `src/main/java/com/rpb/reservation/customer/domain/Customer.java`: add optional `email` and email-aware profile refresh.
- `src/main/java/com/rpb/reservation/customer/persistence/mapper/DefaultCustomerMapper.java`: map `email` both ways.
- `src/main/java/com/rpb/reservation/customer/application/CustomerProfileCommand.java`: immutable Customer profile hint command.
- `src/main/java/com/rpb/reservation/customer/application/CustomerManagementCommand.java`: tenant-admin mutation command.
- `src/main/java/com/rpb/reservation/customer/application/CustomerManagementItem.java`: tenant-admin read model.
- `src/main/java/com/rpb/reservation/customer/application/CustomerManagementListResult.java`: list plus page result.
- `src/main/java/com/rpb/reservation/customer/application/CustomerProfileResolver.java`: reusable upsert/refresh service used by reservation/public booking.
- `src/main/java/com/rpb/reservation/customer/application/CustomerManagementApplicationService.java`: list/get/create/update/archive service.
- `src/main/java/com/rpb/reservation/customer/application/CustomerManagementError.java`: stable application errors.
- `src/main/java/com/rpb/reservation/customer/application/CustomerManagementException.java`: service exception.
- `src/main/java/com/rpb/reservation/customer/application/port/out/CustomerRepositoryPort.java`: add list/get/update/archive management methods.
- `src/main/java/com/rpb/reservation/customer/persistence/adapter/CustomerPersistenceAdapter.java`: implement management methods using existing `customers`.
- `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminController.java`: add Customer endpoints and mapping.
- `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminApiErrorCode.java`: add Customer error mappings.
- `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminCustomer*.java`: request/response DTOs.
- `src/main/java/com/rpb/reservation/reservation/api/CreateReservationRequest.java`: add `customerEmail`.
- `src/main/java/com/rpb/reservation/reservation/api/ReservationApiMapper.java`: map `customerEmail`.
- `src/main/java/com/rpb/reservation/reservation/application/command/CreateReservationCommand.java`: add `customerEmail`.
- `src/main/java/com/rpb/reservation/reservation/application/service/ReservationCreateApplicationService.java`: use `CustomerProfileResolver`.
- `src/main/java/com/rpb/reservation/publicbooking/api/PublicBookingController.java`: accept public customer name/nickname/email.
- `src/main/java/com/rpb/reservation/publicbooking/application/PublicBookingCreateCommand.java`: add public profile fields.
- `src/main/java/com/rpb/reservation/publicbooking/application/PublicBookingApplicationService.java`: pass profile hints to reservation creation.
- `src/api/tenantAdminApi.ts`: add Customer types and functions.
- `src/types/publicBooking.ts`: add public booking mutation fields.
- `src/api/publicBookingApi.ts`: no behavior change unless type imports need adjustment.
- `src/router/index.ts`: add tenant-admin customer route.
- `src/components/tenant-admin/TenantAdminNav.vue`: add Customer nav item.
- `src/pages/TenantAdminCustomersPage.vue`: new ERP page.
- `src/pages/PublicBookingPage.vue`: add contact fields and submit them.
- `src/i18n/locales/zh-CN.ts`, `src/i18n/locales/en-SG.ts`, `src/i18n/locales/generated-zh-CN.ts`, `src/i18n/locales/generated-en-SG.ts`: add nav/page/public booking keys.
- `src/test/java/com/rpb/reservation/customer/application/CustomerManagementApplicationServiceTest.java`: service tests.
- `src/test/java/com/rpb/reservation/auth/integration/TenantAdminApiIntegrationTest.java`: API coverage.
- `src/test/java/com/rpb/reservation/reservation/application/ReservationCreateApplicationServiceTest.java`: email/profile refresh coverage.
- `src/test/java/com/rpb/reservation/publicbooking/application/PublicBookingApplicationServiceTest.java`: public profile forwarding coverage.
- `src/test/java/com/rpb/reservation/appgate/ui/TenantAdminCustomerManagementUiValidationTest.java`: static UI/i18n validation.
- `src/test/java/com/rpb/reservation/appgate/ui/PublicBookingUiValidationTest.java`: contact-field validation.
- `docs/release-notes/2026-07-06-tenant-customer-management.md`: release note.

## Task 1: Customer Application Boundary

**Files:**
- Modify: `src/main/java/com/rpb/reservation/customer/domain/Customer.java`
- Modify: `src/main/java/com/rpb/reservation/customer/persistence/mapper/DefaultCustomerMapper.java`
- Modify: `src/main/java/com/rpb/reservation/customer/persistence/adapter/CustomerPersistenceAdapter.java`
- Modify: `src/main/java/com/rpb/reservation/customer/application/port/out/CustomerRepositoryPort.java`
- Create: `src/main/java/com/rpb/reservation/customer/application/CustomerProfileCommand.java`
- Create: `src/main/java/com/rpb/reservation/customer/application/CustomerManagementCommand.java`
- Create: `src/main/java/com/rpb/reservation/customer/application/CustomerManagementItem.java`
- Create: `src/main/java/com/rpb/reservation/customer/application/CustomerManagementListResult.java`
- Create: `src/main/java/com/rpb/reservation/customer/application/CustomerManagementError.java`
- Create: `src/main/java/com/rpb/reservation/customer/application/CustomerManagementException.java`
- Create: `src/main/java/com/rpb/reservation/customer/application/CustomerProfileResolver.java`
- Create: `src/main/java/com/rpb/reservation/customer/application/CustomerManagementApplicationService.java`
- Test: `src/test/java/com/rpb/reservation/customer/application/CustomerManagementApplicationServiceTest.java`

- [ ] **Step 1: Write failing Customer management service tests**

Add tests that assert:

```java
@Test
void createsCustomerWithoutPhoneWhenNameIsPresent() {
    FakeCustomerRepository repository = new FakeCustomerRepository();
    CustomerManagementApplicationService service = new CustomerManagementApplicationService(repository);

    CustomerManagementItem item = service.create(SCOPE, new CustomerManagementCommand("王小明", "先生", null, null));

    assertThat(item.displayName()).isEqualTo("王小明");
    assertThat(item.nickname()).isEqualTo("先生");
    assertThat(item.phoneE164()).isNull();
    assertThat(repository.saved).hasSize(1);
}

@Test
void rejectsDuplicateActivePhoneInTenant() {
    FakeCustomerRepository repository = new FakeCustomerRepository();
    repository.existingByPhone = Optional.of(activeCustomer("+6591234567"));
    CustomerManagementApplicationService service = new CustomerManagementApplicationService(repository);

    assertThatThrownBy(() -> service.create(SCOPE, new CustomerManagementCommand("王小明", "先生", "+6591234567", null)))
        .isInstanceOf(CustomerManagementException.class)
        .extracting("code")
        .isEqualTo(CustomerManagementError.CUSTOMER_PHONE_CONFLICT);
}

@Test
void profileResolverRefreshesEmailOnExistingPhoneCustomer() {
    FakeCustomerRepository repository = new FakeCustomerRepository();
    repository.existingByPhone = Optional.of(activeCustomer("+6591234567"));
    CustomerProfileResolver resolver = new CustomerProfileResolver(repository);

    Customer customer = resolver.resolve(SCOPE, new CustomerProfileCommand(null, "王小明", "先生", "+6591234567", "guest@example.com"));

    assertThat(customer.email()).isEqualTo("guest@example.com");
    assertThat(repository.saved).hasSize(1);
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
mvn "-Dtest=CustomerManagementApplicationServiceTest" test
```

Expected: compile failure because the new Customer management classes do not exist.

- [ ] **Step 3: Implement Customer domain and application classes**

Implement:

```java
public record CustomerProfileCommand(
    UUID customerId,
    String displayName,
    String nickname,
    String phoneE164,
    String email
) {}
```

`Customer` must expose `email` and `refreshProfile(E164Phone updatedPhone, String updatedDisplayName, String updatedNickname, String updatedEmail)`.

`CustomerProfileResolver.resolve(TenantScope, CustomerProfileCommand)` must:

```text
customerId present -> findById or CUSTOMER_NOT_FOUND
else phone present -> findByPhone
else no match -> create new Customer
refresh only nonblank displayName, nickname, email, and present phone
```

`CustomerManagementApplicationService` must normalize optional fields, validate E.164/email, check duplicate phone on create/update, and archive with repository support.

- [ ] **Step 4: Implement persistence methods**

Add repository port methods:

```java
List<CustomerManagementItem> listActive(TenantScope scope, String keyword, int limit, int offset);
int countActive(TenantScope scope, String keyword);
Optional<CustomerManagementItem> findManagementItem(TenantScope scope, CustomerId customerId);
Customer updateProfile(TenantScope scope, Customer customer);
void archive(TenantScope scope, CustomerId customerId);
```

Use `JdbcTemplate` or existing JPA repository support inside `CustomerPersistenceAdapter`. Queries must filter `tenant_id = ?`, `deleted_at is null`, and `status = 'active'`.

- [ ] **Step 5: Run task tests**

Run:

```powershell
mvn "-Dtest=CustomerManagementApplicationServiceTest" test
```

Expected: tests pass.

## Task 2: Tenant Admin Customer API

**Files:**
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminController.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminApiErrorCode.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminServiceErrorCode.java`
- Create: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminCustomerMutationRequest.java`
- Create: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminCustomerItemResponse.java`
- Create: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminCustomerResponse.java`
- Create: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminCustomerListResponse.java`
- Test: `src/test/java/com/rpb/reservation/auth/integration/TenantAdminApiIntegrationTest.java`

- [ ] **Step 1: Write failing API integration tests**

Add tests that call:

```text
GET /api/v1/stores/{storeId}/tenant-admin/customers
POST /api/v1/stores/{storeId}/tenant-admin/customers
PATCH /api/v1/stores/{storeId}/tenant-admin/customers/{customerId}
POST /api/v1/stores/{storeId}/tenant-admin/customers/{customerId}/archive
```

Assert tenant admin can create a no-phone Customer, duplicate phone returns conflict, store scope mismatch returns forbidden, and archive removes the Customer from active list.

- [ ] **Step 2: Run API test and verify failure**

Run:

```powershell
mvn "-Dtest=TenantAdminApiIntegrationTest" test
```

Expected: 404 for Customer endpoints.

- [ ] **Step 3: Implement API DTOs and controller mappings**

Constructor-inject `CustomerManagementApplicationService` into `TenantAdminController`.

Add mappings:

```java
@GetMapping("/customers")
@PostMapping("/customers")
@GetMapping("/customers/{customerId}")
@PatchMapping("/customers/{customerId}")
@PostMapping("/customers/{customerId}/archive")
```

Each mapping must call `requireTenantAdminScope(storeId)` and pass only `scope.tenantScope()` to Customer application services.

- [ ] **Step 4: Map errors**

Map Customer errors to tenant admin API errors:

```text
REQUEST_INVALID -> 400
CUSTOMER_NOT_FOUND -> 404
CUSTOMER_PHONE_CONFLICT -> 409
PERSISTENCE_ERROR -> 500
```

- [ ] **Step 5: Run API tests**

Run:

```powershell
mvn "-Dtest=TenantAdminApiIntegrationTest" test
```

Expected: tenant-admin existing tests plus Customer endpoint tests pass.

## Task 3: Reservation And Public Booking Profile Upsert

**Files:**
- Modify: `src/main/java/com/rpb/reservation/reservation/api/CreateReservationRequest.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/api/ReservationApiMapper.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/command/CreateReservationCommand.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationCreateApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/publicbooking/api/PublicBookingController.java`
- Modify: `src/main/java/com/rpb/reservation/publicbooking/application/PublicBookingCreateCommand.java`
- Modify: `src/main/java/com/rpb/reservation/publicbooking/application/PublicBookingApplicationService.java`
- Test: `src/test/java/com/rpb/reservation/reservation/application/ReservationCreateApplicationServiceTest.java`
- Test: `src/test/java/com/rpb/reservation/publicbooking/application/PublicBookingApplicationServiceTest.java`

- [ ] **Step 1: Write failing reservation tests**

Add assertions:

```java
@Test
void createsCustomerWithEmailWhenProvided() {
    Scenario scenario = Scenario.ready();

    ReservationCreateResult result = scenario.service().createReservation(
        scenario.commandWithPhoneCustomerAndEmail("+6591234567", "guest@example.com")
    );

    assertThat(result.success()).isTrue();
    assertThat(scenario.customerRepository.saved.getFirst().email()).isEqualTo("guest@example.com");
}
```

- [ ] **Step 2: Write failing public booking test**

Use a fake reservation service or spy command capture to assert public booking maps `customerName`, `customerNickname`, and `customerEmail` into `CreateReservationCommand`.

- [ ] **Step 3: Run tests and verify failure**

Run:

```powershell
mvn "-Dtest=ReservationCreateApplicationServiceTest,PublicBookingApplicationServiceTest" test
```

Expected: compile failure for missing `customerEmail` fields.

- [ ] **Step 4: Implement command/API fields and resolver usage**

Add `customerEmail` to reservation request and command. Replace reservation service's private Customer creation/refresh logic with `CustomerProfileResolver.resolve(...)`.

Public booking request adds:

```java
String customerName,
String customerNickname,
String customerEmail
```

Public booking passes these through to `CreateReservationCommand`, using authenticated principal id/display name when login is required and submitted nonblank fields as refresh hints.

- [ ] **Step 5: Run focused tests**

Run:

```powershell
mvn "-Dtest=ReservationCreateApplicationServiceTest,PublicBookingApplicationServiceTest" test
```

Expected: tests pass.

## Task 4: Frontend ERP Page And I18n

**Files:**
- Modify: `src/api/tenantAdminApi.ts`
- Modify: `src/types/publicBooking.ts`
- Modify: `src/router/index.ts`
- Modify: `src/components/tenant-admin/TenantAdminNav.vue`
- Create: `src/pages/TenantAdminCustomersPage.vue`
- Modify: `src/pages/PublicBookingPage.vue`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`
- Modify: `src/i18n/locales/generated-zh-CN.ts`
- Modify: `src/i18n/locales/generated-en-SG.ts`
- Test: `src/test/java/com/rpb/reservation/appgate/ui/TenantAdminCustomerManagementUiValidationTest.java`
- Test: `src/test/java/com/rpb/reservation/appgate/ui/PublicBookingUiValidationTest.java`

- [ ] **Step 1: Write failing static UI tests**

Assert:

```java
assertThat(router).contains("TenantAdminCustomersPage").contains("tenant-admin-customers");
assertThat(nav).contains("nav.tenant.customers").contains("/admin/customers");
assertThat(page).contains("generated.tenant-admin-customers.001");
assertThat(publicBookingPage).contains("customerName").contains("customerNickname").contains("customerEmail");
```

- [ ] **Step 2: Run UI validation tests and verify failure**

Run:

```powershell
mvn "-Dtest=TenantAdminCustomerManagementUiValidationTest,PublicBookingUiValidationTest" test
```

Expected: Customer page/router/nav assertions fail.

- [ ] **Step 3: Implement frontend API and page**

Add TypeScript interfaces:

```ts
export interface TenantAdminCustomer {
  id: string
  customerCode: string
  displayName: string | null
  nickname: string | null
  phoneE164: string | null
  email: string | null
  status: 'active' | 'archived' | 'merged'
  createdAt: string
  updatedAt: string
}

export interface TenantAdminCustomerMutation {
  displayName?: string | null
  nickname?: string | null
  phoneE164?: string | null
  email?: string | null
}
```

Add `listCustomers`, `createCustomer`, `updateCustomer`, and `archiveCustomer`.

Implement `TenantAdminCustomersPage.vue` with ERP table/search/form/archive confirmation, using `ErpQueryToolbar`, `ErpPagination`, `TenantAdminNav`, and `useGeneratedText`.

- [ ] **Step 4: Add route/nav/i18n and public booking fields**

Router:

```ts
const TenantAdminCustomersPage = () => import('../pages/TenantAdminCustomersPage.vue')
```

Route:

```ts
{
  path: '/stores/:storeId/admin/customers',
  name: 'tenant-admin-customers',
  component: TenantAdminCustomersPage,
  meta: { requiresTenantAdmin: true }
}
```

Public booking contact step must bind `bookingForm.customerName`, `bookingForm.customerNickname`, and `bookingForm.customerEmail`, then include them in `createPublicBooking(...)`.

- [ ] **Step 5: Run frontend validation**

Run:

```powershell
mvn "-Dtest=TenantAdminCustomerManagementUiValidationTest,PublicBookingUiValidationTest" test
npm run build
```

Expected: Java static UI tests pass and TypeScript build succeeds.

## Task 5: Review, Release Note, And Full Validation

**Files:**
- Create: `docs/release-notes/2026-07-06-tenant-customer-management.md`
- Modify: files touched by earlier tasks only if review finds issues.

- [ ] **Step 1: Run focused backend and frontend tests**

Run:

```powershell
mvn "-Dtest=CustomerManagementApplicationServiceTest,TenantAdminApiIntegrationTest,ReservationCreateApplicationServiceTest,PublicBookingApplicationServiceTest,TenantAdminCustomerManagementUiValidationTest,PublicBookingUiValidationTest" test
npm run build
```

Expected: all focused tests and build pass.

- [ ] **Step 2: Write release note**

Create release note with:

```markdown
# Tenant Customer Management

- Added tenant-admin Customer maintenance for list/search, create, edit, and archive.
- Staff reservation and public booking creation now create or refresh Customer name, salutation, phone, and optional email.
- Customer phone remains optional; E.164 validation and tenant-scoped phone uniqueness are preserved.
- Added zh-CN and en-SG UI copy for the new Customer maintenance workflow.
```

- [ ] **Step 3: Run code review checks**

Use `docs/skills/code-review/SKILL.md` and verify:

```text
Customer remains tenant-scoped.
Reservation/PublicBooking use Customer application service, not Customer persistence.
API DTOs do not leak persistence entities.
No hardcoded UI copy appears outside i18n locale files.
No unrelated dirty files are reverted.
```

- [ ] **Step 4: Commit implementation**

Stage only files touched for this feature:

```powershell
git status --short
git add -- <feature files>
git commit -m "feat: add tenant customer management"
```

Expected: commit succeeds without staging unrelated pre-existing changes.

