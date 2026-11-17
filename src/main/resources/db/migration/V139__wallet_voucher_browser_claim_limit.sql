-- Keep IP as audit data and preserve every existing claim. Switch uniqueness
-- to the browser receipt so phones on the same company network can all claim.
alter table wallet_voucher_claims
    drop constraint wallet_voucher_claims_pkey,
    drop constraint wallet_voucher_claims_campaign_id_receipt_hash_key,
    add primary key (campaign_id, receipt_hash);
