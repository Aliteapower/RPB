# RPB Skills Index

This directory contains reusable RPB skills for Codex-assisted engineering work. Use the relevant skill before implementation or review, then keep the output format from that skill in the final report or handoff document.

## Skill Index

- Architecture Review: use `docs/architecture/ARCHITECTURE.md`, governance docs, and `docs/skills/reservation-system/SKILL.md` when module boundaries or the reservation workflow are affected.
- [Code Review](code-review/SKILL.md)
- [API Review](api-review/SKILL.md)
- [UI Review](ui-review/SKILL.md)
- [TDD Review](tdd-review/SKILL.md)
- [Release Note](release-note/SKILL.md)
- [Troubleshooting](troubleshooting/SKILL.md)
- [Database Review](database-review/SKILL.md)
- [PR Review](pr-review/SKILL.md)
- [Issue to PR](issue-to-pr/SKILL.md)
- [Changelog](changelog/SKILL.md)
- [Production Readiness](production-readiness/SKILL.md)
- [Reservation System](reservation-system/SKILL.md): domain governance for Reservation / Queue / Walk-in / Seating / Cleaning.

## Recommended Usage Flow

```text
Issue to PR
↓
Architecture Review
↓
API Review / Database Review
↓
TDD Review
↓
Code Review
↓
UI Review
↓
Release Note
↓
PR Review
↓
Production Readiness
```

For bug fixing or failed validation, use Troubleshooting first, then return to TDD Review, Code Review, Release Note, PR Review, and Production Readiness as needed.
