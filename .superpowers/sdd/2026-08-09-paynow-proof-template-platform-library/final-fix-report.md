# Final Fix Report

## Scope

Verified all eight final whole-branch review findings against `d9bce35c` and applied minimal fixes within the PayNow proof review/template backend, API contract, Vue consumers, tests, and release notes. No database migration or permission scope was changed by this final-fix round.

## Finding Verification And Fixes

1. **Exact auto-confirm amount**: confirmed. `PaymentProofReviewService.amountMatches` accepted an absolute difference of `0.01`. Replaced it with scale-insensitive exact `BigDecimal.compareTo` equality. Added a regression proving OCR `1.01` for expected `1.00` remains `needs_review` and does not confirm the intent.
2. **Rule validation**: confirmed. Template and contribution normalization only parsed JSON, while scan-time `Pattern.compile` could throw. Added object/array/string shape validation and eager compilation of every `referencePatterns` and `amountPatterns` entry. Validation runs on template create/update, contribution submission, and contribution acceptance, including persisted legacy contribution data.
3. **Caller-visible optimistic locking**: confirmed. Existing-template acceptance read the latest target version and sent it back to persistence, and platform PATCH defaulted a missing version to `0`. Added nullable `targetTemplateVersion` and nullable contribution `version` to the review command, required the caller's target version for existing-template acceptance, passed that version to the repository update, rejected missing required versions, and made platform PATCH reject missing `version`.
4. **Reject note**: confirmed. Rejection previously trimmed but did not require a note. The service now rejects blank notes, and the platform UI guards and disables rejection until a nonblank note exists.
5. **Contribution mutation response**: confirmed. Backend mutations return a flat `ContributionResponse`; TypeScript modeled a `{ success, contribution }` wrapper. The frontend response type now aliases the flat `PaymentProofTemplateContribution` payload, with source acceptance validation preventing the wrapper from returning.
6. **Platform OCR evidence**: confirmed. The suggestion response OCR object was discarded. The platform editor now retains and renders extracted Ref, amount, confidence, and raw OCR text without persisting image bytes or mutating payment state.
7. **Tenant contribution outcomes**: confirmed. Tenant rows only exposed the submitted state. The latest contribution now displays submitted/accepted/rejected/withdrawn status and any `reviewNote`.
8. **API and rollback docs**: confirmed. Expanded the contract with authorization/scope, template/rule/contribution schemas, flat mutation responses, optimistic version requirements, transitions, validation, and errors. Rollback notes now explicitly remove `platform.payment_proof_template.manage` grants.

## TDD Review

| Scenario | Test | Result |
|---|---|---|
| OCR amount differs by one cent | `PaymentProofReviewServiceTest.doesNotAutoConfirmWhenExtractedAmountDiffersByOneCent` | Passed after failing as `auto_confirmed` before the fix |
| Malformed regex on create/submit/accept | `PaymentProofTemplateContributionServiceTest` malformed-rule cases | Passed after failing before validation |
| Missing/stale target version | `PaymentProofTemplateContributionServiceTest` target-version cases | Passed after failing before caller-visible locking |
| Missing platform PATCH version | `PlatformPaymentProofTemplateControllerTest.platformTemplatePatchRequiresVersion` | Passed after the versionless request reached the service before the fix |
| Blank reject note | `PaymentProofTemplateContributionServiceTest.rejectContributionRequiresNonBlankReviewNote` | Passed after failing before validation |
| Frontend contracts/evidence/outcomes | `PayNowPaymentUiAcceptanceValidationTest` | Passed |

Permission and tenant-isolation coverage remains in the existing platform/tenant controller and contribution service tests. No idempotent payment command behavior, App Gate registration, migration schema, or local runtime security behavior changed in this round.

## API Review

- Existing `/api/v1` paths and platform/tenant permission enforcement remain unchanged.
- Review requests now expose both contribution and target-template optimistic versions; missing values map to `REQUEST_INVALID`, stale values to `VERSION_CONFLICT`.
- Contribution mutation responses are consistently flat across backend, TypeScript, API helpers, and contract documentation.
- Rule suggestions remain read-only and image bytes remain temporary filesystem input only.

## Code Review

- Scope and layering are preserved: controllers call `PaymentProofTemplateService`; repository interfaces and SQL remain unchanged.
- Exact amount safety, transaction rollback behavior, platform permission checks, and tenant source isolation remain intact.
- No P0/P1 findings remain from this final-fix review.

## Validation

- Platform/template/contribution/UI suite: 27 tests, 0 failures, 0 errors.
- Proof review/template/OCR/controller suite: 27 tests, 0 failures, 0 errors.
- Payment migration suite: 8 tests, 0 failures, 0 errors.
- `npm run build`: passed (`vue-tsc --noEmit && vite build`).
- Worktree-local PostgreSQL pointer: `target/local-postgres-current.txt`, port `62867`, accepting connections during migration validation.
- `git diff --check`: passed with no whitespace errors.

## Remaining Risk

None identified beyond the existing repository-wide Maven deprecation warnings and Mockito dynamic-agent warning, neither introduced by this change.

## Scoped Re-review Follow-up

### Persisted malformed regex isolation

The scoped re-review finding was confirmed. Although write-time validation rejects new malformed patterns, `extractByPatterns` and `extractAmountByPatterns` still compiled every persisted pattern without exception isolation. A malformed active legacy row could therefore throw `PatternSyntaxException` and abort proof-template enhancement.

Both scan-time extraction loops now catch `PatternSyntaxException` per persisted pattern and continue to the next pattern. If no valid template pattern extracts a value, the existing OCR/system-reference fallback remains available. Strict write-time compilation is unchanged.

Regression `PaymentProofTemplateServiceTest.enhancementSkipsMalformedPersistedPatternsAndContinuesWithValidPatterns` uses an active persisted template with malformed first reference/amount patterns and valid second patterns. It failed with `PatternSyntaxException` before the fix and now extracts the reference and amount successfully.

Focused validation:

- `mvn "-Dtest=PaymentProofTemplateServiceTest,PaymentProofReviewServiceTest" test`: passed, 15 tests with 0 failures and 0 errors.
- `git diff --check`: passed with no whitespace errors.
