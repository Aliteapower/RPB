alter table wallet_voucher_claims
    add column redemption_number integer,
    add column redemption_issue_total integer;

with historical as (
    select x.campaign_id, x.ip, c.total,
           row_number() over (partition by x.campaign_id order by x.redeemed_at, x.code_hash)::integer as number
    from wallet_voucher_claims x
    join wallet_voucher_campaigns c on c.id=x.campaign_id and c.tenant_id=x.tenant_id
    where x.redeemed_at is not null
)
update wallet_voucher_claims x
set redemption_number=h.number, redemption_issue_total=h.total
from historical h
where x.campaign_id=h.campaign_id and x.ip=h.ip;

alter table wallet_voucher_claims add constraint wallet_voucher_redemption_number_check check (
    (redeemed_at is null and redemption_number is null and redemption_issue_total is null)
    or (redeemed_at is not null and redemption_number is not null and redemption_issue_total is not null
        and redemption_number between 1 and redemption_issue_total)
);

create unique index wallet_voucher_redemption_number_unique
    on wallet_voucher_claims(campaign_id, redemption_number)
    where redemption_number is not null;
