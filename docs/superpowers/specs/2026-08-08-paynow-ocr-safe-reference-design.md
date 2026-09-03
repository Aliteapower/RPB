# PayNow OCR-Safe Payment Reference Design

## Context

Payment Proof Review is used by store staff on a mobile device while a customer shows a PayNow bank receipt on the customer's phone. Upload recognition now works, but the everyday workflow depends on fast camera scanning of another screen. That makes the generated payment reference itself part of the OCR accuracy surface.

The current reference shape is readable by people but not ideal for screen-to-screen OCR:

```text
QP-202608-0013-2KZ6
PIT-202608-0021-ABCD
```

Observed OCR failure modes:

- Separators are read as `-`, `.`, spaces, or omitted.
- Check characters can confuse `0/O`, `1/I/L`, `2/Z`, `5/S`, and `8/B`.
- Browser/photo captures may add page chrome, tilt, moire, and extra text around the receipt.

## Goals

- Make newly generated references easier for OCR to read during live scanning.
- Preserve human usefulness: staff can still see prefix, month, and sequence.
- Keep old pending and historical references valid.
- Avoid database migrations and avoid changing the PayNow QR payload contract.
- Keep matching strict enough that random bank transaction IDs are not accepted.

## Non-Goals

- Do not rewrite existing payment references.
- Do not remove the existing Proof Review scan or upload flows.
- Do not depend on a QR code inside the bank receipt; bank receipts only show text.
- Do not add new tenant settings in this phase.

## Recommended Format

Newly generated references should use a compact, separator-free shape:

```text
QP2026080013ACDE
PIT2026080021KMRV
```

Segments:

- Prefix: existing product/channel prefix, uppercase, 2 to 8 characters.
- Period: `yyyyMM`.
- Sequence: fixed-width numeric sequence, initially 4 digits for current Quick Pay display numbers.
- Check segment: 4 OCR-safe uppercase letters.

The canonical display value should not include `-` separators for newly generated references. Existing references with separators remain accepted.

## OCR-Safe Check Alphabet

Use letters only for the check segment:

```text
ACDEFGHJKMNPQRTVWXY
```

Excluded characters:

- `O`, because it is confused with `0`.
- `I` and `L`, because they are confused with `1`.
- `B`, because it is confused with `8`.
- `S`, because it is confused with `5`.
- `Z`, because it is confused with `2`.
- Digits, because numeric check characters increase OCR ambiguity.

This alphabet gives 19 symbols. A 4-character check segment gives 130,321 combinations, enough for validation and collision resistance in this workflow.

## Generation Rules

- Continue using the existing prefix validation: `[A-Z0-9]{2,8}`.
- Keep period as `yyyyMM`.
- Keep sequence validation as positive and bounded by the existing generator.
- Build the checksum input from a stable canonical base:

```text
<PREFIX><YYYYMM><SEQUENCE>
```

For a 4-digit sequence:

```text
QP2026080013
```

- Convert the CRC32 value into 4 characters using the OCR-safe alphabet.
- Return:

```text
<PREFIX><YYYYMM><SEQUENCE><CHECK4>
```

## Parsing And Matching Rules

The parser must support both generations:

- New compact format:

```text
QP2026080013ACDE
PIT2026080021KMRV
```

- Old separated format:

```text
QP-202608-0013-2KZ6
QP-202608.0013.2KZ6
QP 202608 0013 2KZ6
PIT-202608-0021
```

Normalization should:

- Uppercase text.
- Normalize Unicode hyphens to `-`.
- Treat `-`, `.`, and whitespace as optional separators only inside valid reference shapes.
- Keep old checksum characters valid for old generated references.
- Prefer exact candidate matching from active pending records after extracting a plausible system reference.

The parser should not apply broad OCR character correction globally. Character corrections such as `O -> 0` or `I -> 1` can create false positives. If added later, they should be position-aware and candidate-aware only.

## Compatibility

- New payment intents use the compact OCR-safe format after rollout.
- Existing pending intents keep their stored reference and remain matchable.
- Existing admin search and proof review compare normalized references, so old and new values can coexist.
- No migration is required because `payment_reference` is already a string field.
- PayNow QR payload continues placing the reference in the EMV additional-data reference field.

## User Experience

- Quick Pay and Present screens simply display the compact reference as the PayNow message/reference.
- Payment Proof Review continues showing extracted Ref and expected amount.
- No staff setting is needed for this phase.
- The shorter compact shape should be easier to fit on bank receipt lines and easier to scan from a customer's phone.

## Testing

Add or update tests for:

- Generator creates compact OCR-safe references without excluded check characters.
- Generator remains deterministic for the same prefix, period, and sequence.
- Parser extracts compact references from Chinese OCBC-style receipt text.
- Parser extracts compact references when OCR inserts spaces or dot/hyphen separators.
- Parser still extracts old separated references with and without checksum.
- Parser rejects long bank transaction IDs without a system reference shape.
- Proof Review service matches new compact references against pending candidates by Ref and amount.
- UI acceptance checks continue covering Proof Review scan/upload entry points.

## Rollout

1. Implement generator and parser tests first.
2. Update `PaymentReferenceGenerator`.
3. Update `PaymentReferencePattern`.
4. Run focused backend and UI acceptance tests.
5. Build frontend and backend artifacts.
6. Deploy with the same backup procedure used for the previous Proof Review releases.
7. Smoke `/login`, QuickPay, Present, Proof Review, Payment Records, and current frontend assets.

## Rollback

Rollback is the previous backend jar and frontend directory backup. No data rollback is required:

- References generated before rollback remain stored as strings.
- Proof Review parser should remain backward compatible before deploying this change.
- If rollback happens after compact references have been generated, an older backend may not parse compact references unless the parser compatibility patch has already been included in that older version. For that reason, the implementation should keep compact reference support in the same deployable unit as generation.

## Open Decision

Default sequence width should remain 4 digits for current Quick Pay display numbers, with generator validation preserving the existing upper bound for future use. If product usage requires more than 9,999 references per month per prefix, the generator can move to 6 digits in a later explicit change.
