# PayNow Proof Template Platform Library

## New

- Platform admins can maintain shared PayNow receipt templates.
- Tenants can use platform templates by default and submit missing bank layouts for review.
- Uploaded/captured receipt samples can generate draft JSON rule suggestions.

## Migration

- Adds `V055__paynow_platform_proof_template_contributions.sql`.
- Adds platform permission `platform.payment_proof_template.manage`.

## Safety

- Auto-confirmation still requires unique Ref and exact amount match.
- Uploaded sample image bytes are not stored.

## Rollback Notes

- Restore backend jar and frontend bundle.
- If schema rollback is required, delete contribution rows and drop `payment_proof_template_contributions`.
