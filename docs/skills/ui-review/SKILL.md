---
name: ui-review
description: Use when reviewing RPB Vue pages, staff workflows, admin screens, UI contracts, visual polish, responsive behavior, empty/loading/error states, permissions, i18n, date formatting, or amount formatting.
---

# UI Review Skill

## Purpose

Review RPB frontend work for operational clarity, visual consistency, responsive behavior, and correct integration with API and App Gate states.

Apply the RPB baseline: Vue frontend, Java backend APIs, PostgreSQL-backed data, multi-tenant SaaS, App Gate, Reservation / Queue / Walk-in / Seating / Cleaning workflows, API contract first, and TDD preferred.

## When to use

Use this skill after building or modifying a Vue page, component, admin screen, staff workflow, table, form, or API-connected UI state.

## Inputs

- Page or component name.
- UI contract, screenshot, implementation report, or changed files.
- Related API contract and sample responses.
- Permission and App Gate behavior.
- Locale, timezone, date, and currency requirements.
- Desktop and mobile verification notes when available.

## Required checks

- Layout supports the target staff or admin workflow without unnecessary marketing-style structure.
- Spacing is consistent and dense enough for operational use.
- Font size and hierarchy fit the container and workflow.
- Colors preserve readability and do not rely on a single hue family.
- Button hierarchy makes primary, secondary, destructive, and disabled states clear.
- Tables are readable, scannable, and stable with realistic data.
- Empty states explain the operational state without noisy instruction text.
- Loading states do not shift layout or hide required context.
- Error states map API failures and permission denials clearly.
- Permission-unavailable states hide or disable actions consistently.
- Mobile layout preserves workflow order and avoids overlap.
- Chinese and English text fit without clipping.
- Date and time display follows store timezone and configured format.
- Amount and currency display follows store locale and currency.
- Reservation, Queue, Walk-in, Seating, and Cleaning status names are consistent with contracts.

## Output format

```markdown
# UI Review Report
## Page / Component
## Visual Issues
## Interaction Issues
## Empty / Loading / Error States
## Responsive Check
## Suggested Fixes
```

## Stop conditions

- Stop if critical staff actions are hidden, ambiguous, or exposed without permission.
- Stop if UI behavior contradicts API contract or App Gate state.
- Stop if text overlaps, clips, or becomes unreadable on mobile or desktop.
- Stop if date, time, amount, or status formatting is inconsistent with RPB locale rules.
- Stop if a required empty, loading, error, or permission state is missing.

## Example prompt

```text
Use the RPB ui-review skill to review the store staff reservation today view after the latest Vue changes.
```
