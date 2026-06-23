# Staff Home New UI Design Contract V1.1 - Lightweight Workbench

## 1. Purpose

Finalize the next Staff Home visual direction as a lightweight mobile staff workbench while preserving the
approved Staff Home entry-hub boundary.

Target page:

```text
src/pages/StoreStaffHomePage.vue
```

Target route:

```text
/stores/{storeId}/staff
```

This document is a design contract only. It does not implement UI, Vue Router changes, App Gate metadata
changes, backend APIs, database migrations, runtime configuration, dependency changes, seed data, or
production data.

## 2. Supersedes

This contract refines and supersedes the visual direction in:

```text
docs/frontend/STAFF_HOME_NEW_UI_DESIGN_CONTRACT.md
```

The baseline entry and permission contract remains:

```text
docs/frontend/STAFF_HOME_UI_BASELINE_CONTRACT.md
```

V1.1 keeps the V1 safety boundary, but changes the desired visual treatment from "grouped cards inside
section cards" to a lighter workbench layout with fewer nested boxes.

## 3. Design Decision

Final approved direction:

```text
Staff Home New UI V1.1 - Lightweight Workbench
```

The page should feel like a phone-first restaurant staff tool, not a marketing page, analytics dashboard,
table map, queue workbench, or prototype demo.

The phrase `Lightweight Workbench` names the visual direction only. It does not approve a Queue Workbench,
Table Workbench, mutation console, or new business workflow.

The key design change is:

```text
Use lightweight page sections with direct action buttons.
Avoid card-inside-card layout.
```

## 4. Reference Materials

Read and considered:

- `docs/frontend/STAFF_HOME_UI_BASELINE_CONTRACT.md`
- `docs/frontend/STAFF_HOME_NEW_UI_DESIGN_CONTRACT.md`
- `src/pages/StoreStaffHomePage.vue`
- `src/router/index.ts`
- `src/api/meAppsApi.ts`
- `src/types/meApps.ts`
- `src/stores/storeContext.ts`
- Attached HTML prototype
- User-provided mobile screenshots for home, reservation, queue, and table views

## 5. Baseline Entries

Full-permission Staff Home must still show all 10 approved entries:

1. 散客直接入座
2. 清台处理
3. 创建预约
4. 今日预约
5. 预约到店
6. 预约排队
7. 排队列表
8. 排队叫号
9. 排队入座
10. 预约入座

Narrow-permission views may show fewer entries. Missing entries under narrow permissions are expected and
must not be treated as regression.

## 6. Final V1.1 Information Architecture

Use this page structure:

```text
顶部栏
- 食刻 · 管理 / 门店员工工作台
- 当前时间
- 当前门店
- 应用可用状态

流程提示条
- 散客入座 -> 占用 -> 清台 -> 可用

接待
- 散客直接入座
- 预约到店

预约管理
- 创建预约
- 今日预约
- 预约排队
- 预约入座

排队管理
- 排队列表
- 排队叫号
- 排队入座

桌台流转
- 清台处理
```

This grouping is presentation only. It must not change route targets, route names, permissions, App Gate
metadata, API contracts, or business workflows.

## 7. Visual Direction

V1.1 should absorb the useful parts of the screenshots:

- mobile-width centered shell;
- white top bar;
- light gray app background;
- warm orange primary action color;
- compact workflow hint strip;
- clear section headings;
- large thumb-friendly action buttons;
- simple icon or symbol support when available;
- high-frequency actions visually stronger;
- bottom safe-area awareness.

V1.1 should avoid the current visual issue:

```text
Outer card -> section card -> inner action card
```

Preferred structure:

```text
Page background
  Top bar
  Workflow/status strip
  Section title
  Direct action grid
  Section title
  Direct action grid
```

Use cards only for the action buttons themselves or for a single lightweight status strip. Do not wrap each
section in a large card unless the section contains real data, filters, or a list.

## 8. Recommended Layout Rules

### 8.1 Top Bar

The top bar should be compact and sticky or visually anchored at the top.

Recommended content:

- left: brand/workbench title, for example `食刻 · 管理` or `门店员工工作台`;
- center/right: current time;
- right: current store indicator or compact app status pill.

The current time is visual context only. It must not be used as a business timestamp, reservation clock, or
state-transition source. If no store-timezone source is available, implementation should use a simple local
display and avoid implying business precision.

Do not include a `大屏` button in V1.1 because Queue Display is not approved for Staff Home.

### 8.2 Workflow Strip

Use a thin warm-orange strip for the high-level flow:

```text
散客入座 -> 占用 -> 清台 -> 可用
```

This is informational only. It must not create table status actions or table map behavior.

### 8.3 App Status

Show loading, unavailable, empty, and available states from the existing `/api/me/apps` flow.

Allowed states:

- loading: checking available apps;
- unavailable: request failed or app unavailable;
- empty: no visible `reservation_queue` app;
- available: entry groups render by permission.

Avoid debug-style `权限模拟` controls in production Staff Home.

### 8.4 Action Sections

Sections are not large cards. They should be unframed page regions with a title and direct actions.

Recommended section labels:

- `接待`
- `预约管理`
- `排队管理`
- `桌台流转`

The section labels may use small orange icons or text markers, but should not depend on external icon CDNs.

### 8.5 Action Buttons

Use a 2-column grid for common actions on mobile where labels fit. Single full-width action is acceptable
for `清台处理`.

Recommended priority treatment:

- strong primary: `散客直接入座`, `预约到店`;
- strong operational: `排队列表`, `排队叫号`;
- normal: `创建预约`, `今日预约`, `预约排队`, `预约入座`, `排队入座`;
- support: `清台处理`.

Action buttons may have:

- icon area;
- label;
- compact one-line helper only if it fits without visual clutter.

Avoid long explanatory copy inside buttons. Staff Home is for repeated operation, not instruction reading.

## 9. Color And State Direction

Use colors as visual hints, not fake runtime data.

Recommended mapping:

| Meaning | Color direction |
| --- | --- |
| Primary / high-frequency | Orange |
| Reservation / schedule | Blue or indigo |
| Queue waiting / calling | Orange or amber |
| Seating / completed | Green |
| Cleaning / attention | Green or warm red-orange, used carefully |
| Disabled / unavailable | Slate gray |

Do not display numeric status counts unless a real approved API provides them.

## 10. Quick Status Decision

V1.1 must not fake status metrics.

Do not show values such as:

```text
今日预约 12
排队中 5
已叫号 2
清台中 1
```

unless an approved backend API provides those values.

Allowed V1.1 alternatives:

- omit the quick status area;
- use the workflow strip only;
- use static route-entry buttons without counts;
- reserve a future placeholder in the design report, not in the runtime UI.

## 11. Bottom Navigation Decision

Bottom navigation is not part of Staff Home V1.1 implementation.

Reason:

- it implies multiple tab pages inside Staff Home;
- it duplicates existing route navigation;
- `桌台` would imply Table Map/Table Status scope;
- it increases route and permission ambiguity.

Future bottom navigation may be considered only after separate contracts approve reservation, queue, and
table workbench pages.

## 12. Prototype And Screenshot Usage Boundary

Allowed inspiration:

- mobile shell sizing;
- top bar density;
- warm orange workflow strip;
- sectioned action layout;
- large touch targets;
- high-frequency action emphasis;
- status color language.

Forbidden to copy or implement:

- Font Awesome CDN;
- standalone HTML/CSS/JS wholesale;
- `document.querySelector`, `innerHTML`, or local DOM handlers;
- mock queue, reservation, or table data;
- local JavaScript business state;
- permission simulation controls;
- Queue Display / 大屏 button;
- table grid or table cards;
- bottom tab navigation;
- ActionSheet;
- live reservation calendar;
- queue workbench actions that are not existing routes;
- direct business mutations on Staff Home.

## 13. Business Boundary

Staff Home remains an entry hub.

Staff Home may:

- show current store context;
- show current clock;
- show app loading/unavailable/empty state;
- show permission-filtered existing entries;
- route staff to existing approved pages;
- display high-level workflow hints.

Staff Home must not:

- perform queue call, queue seat, queue skip, queue rejoin, no-show, cancellation, cleaning, reservation, or
  table mutations directly;
- create or mutate table status;
- create a Queue Display;
- create a Queue Workbench;
- create a Table Map;
- create a Reservation Calendar;
- infer authorization from role name;
- bypass backend App Gate.

## 14. Implementation Boundary For Next Slice

Recommended implementation files:

```text
src/pages/StoreStaffHomePage.vue
src/test/java/com/rpb/reservation/appgate/ui/*StaffHome*Test.java
docs/frontend/STAFF_HOME_NEW_UI_V1_1_LIGHTWEIGHT_WORKBENCH_IMPLEMENTATION_REPORT.md
```

Do not modify:

- router;
- App Gate permission metadata;
- backend API;
- migrations;
- runtime config;
- dependency files;
- Maven Wrapper;
- Queue Display;
- Table Map;
- Reservation Calendar;
- Queue Rejoin UI;
- Queue Skip UI.

## 15. Required Test Expectations For Next Slice

The next implementation slice should verify:

- full-permission view preserves all 10 baseline entries;
- grouping order follows `接待`, `预约管理`, `排队管理`, `桌台流转`;
- `散客直接入座` and `预约到店` are in `接待`;
- `创建预约`, `今日预约`, `预约排队`, `预约入座` are in `预约管理`;
- `排队列表`, `排队叫号`, `排队入座` are in `排队管理`;
- `清台处理` is in `桌台流转`;
- missing permissions hide individual entries and empty sections;
- `/api/me/apps` remains the visibility source;
- route target names remain unchanged;
- no `queue.skip` or `queue.rejoin` Staff Home entry is added;
- no `Queue Display`, `Table Map`, `Reservation Calendar`, `ActionSheet`, `table-grid`, `screen-overlay`,
  fake data, DOM mutation, or local business state appears in Staff Home.

## 16. Acceptance Criteria

V1.1 implementation is acceptable only if:

- the page no longer reads as card-inside-card;
- staff can scan high-frequency actions quickly on mobile;
- all existing routes and permissions are preserved;
- no fake status metrics are shown;
- no unapproved business workflow is added;
- loading, unavailable, and empty states remain readable;
- Chinese labels do not clip or overlap;
- frontend build passes;
- focused Staff Home validation tests pass.

## 17. Open Questions

None for the V1.1 design contract.

Future implementation may choose exact icon sources from existing project dependencies only. If no suitable
icon dependency exists, use text, CSS, or lightweight inline symbols without adding dependencies.

## 18. Next Step Recommendation

Next approved slice:

```text
Staff Home New UI V1.1 Lightweight Workbench Implementation
```

Recommended sequence:

1. Update focused Staff Home validation tests for V1.1 grouping and forbidden artifacts.
2. Update `src/pages/StoreStaffHomePage.vue` layout and styles only.
3. Run frontend build and focused Staff Home tests.
4. Write V1.1 implementation report.

## 19. Boundary Statement

Staff Home New UI V1.1 design contract finalized: Yes
Staff Home UI changed: No
Router changed: No
Permission metadata changed: No
Backend API changed: No
Queue Display implemented: No
Workbench implemented: No
Table Map implemented: No
Reservation Calendar implemented: No
No-show implemented: No
Cancellation implemented: No
Migration changed: No
Production database touched: No
GitHub remote added: No
GitHub push performed: No
Maven Wrapper added: No
