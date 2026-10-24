package com.rpb.reservation.wallet.transactions;

/** One row per business record. Entry/collection evidence never adds duplicate business rows. */
final class TransactionProjection {
    private TransactionProjection() {}
    static final String CTE="""
        with source as (
          select b.id,'bill'::text source_type,'payment'::text type,
            case when b.status='paid' then 'completed' when b.closed and b.wallet_paid_cents+b.external_paid_cents=0 then 'cancelled' else 'pending' end status,
            b.created_at,
            case when b.status='paid' then (
              select max(at) from (
                select e.created_at at from wallet_entries e where e.tenant_id=b.tenant_id and e.source_id=b.id and e.kind='bill_debit'
                union all select c.created_at from wallet_collections c where c.tenant_id=b.tenant_id and c.bill_id=b.id and not c.is_exception
              ) evidence
            ) end posted_at,
            b.tenant_id,b.store_id,a.customer_id,b.amount_cents,0::bigint quantity,
            -b.wallet_paid_cents wallet_delta,b.principal_paid_cents principal,b.bonus_paid_cents bonus,
            0::bigint credit,b.wallet_paid_cents wallet_paid,b.external_paid_cents external_paid,b.refund_status,
            coalesce((select nullif(m.description,'') from wallet_merchant_orders m where m.tenant_id=b.tenant_id and m.bill_id=b.id),(select nullif(c.payment_description,'') from wallet_customer_identity_codes c where c.tenant_id=b.tenant_id and c.payment_bill_id=b.id))::text summary,b.created_by actor_id,null::text original_source_type,null::uuid original_id,
            b.id::text reference,'out'::text direction
          from wallet_bills b join wallet_accounts a on a.id=b.account_id and a.tenant_id=b.tenant_id where b.tenant_id=:tenant
          union all
          select t.id,'topup','topup',case when t.status='credited' then 'completed' when t.status='cancelled' then 'cancelled' else 'pending' end,
            t.created_at,case when t.status='credited' then coalesce((select e.created_at from wallet_entries e where e.tenant_id=t.tenant_id and e.source_id=t.id and e.kind='topup_credit'),t.credited_at) end,
            t.tenant_id,t.store_id,a.customer_id,t.amount_cents,0,case when t.status='credited' then t.credit_total_cents else 0 end,t.principal_cents,t.bonus_cents,t.credit_total_cents,0,case when t.status='credited' then t.amount_cents else 0 end,
            case when t.refunded_cents=0 then 'none' when t.refunded_cents=t.amount_cents then 'refunded' else 'partially_refunded' end,
            null::text,t.created_by,null,null,t.id::text,'in'
          from wallet_topups t join wallet_accounts a on a.id=t.account_id and a.tenant_id=t.tenant_id where t.tenant_id=:tenant
          union all
          select r.id,'refund','refund',r.status,r.created_at,
            case when r.status='completed' then coalesce((
              select max(at) from (
                select e.created_at at from wallet_entries e where e.tenant_id=r.tenant_id and e.source_id=r.id and e.kind in ('bill_refund','topup_refund')
                union all select p.updated_at from wallet_refund_parts p where p.tenant_id=r.tenant_id and p.refund_id=r.id and p.completed
              ) evidence
            ),r.created_at) end,
            r.tenant_id,r.store_id,a.customer_id,r.amount_cents,0,
            case when r.topup_id is not null and r.exception_collection_id is null then case when r.status='completed' then -r.wallet_cents else 0 end else r.wallet_cents end,
            r.principal_cents,r.bonus_cents,0,r.wallet_cents,
            case when r.topup_id is not null then r.amount_cents else r.amount_cents-r.wallet_cents end,
            r.status,r.reason,coalesce(r.confirmed_by,r.created_by),case when r.bill_id is not null then 'bill' else 'topup' end,
            coalesce(r.bill_id,r.topup_id),r.id::text,case when r.topup_id is not null and r.exception_collection_id is null then 'out' else 'in' end
          from wallet_refunds r join wallet_accounts a on a.id=r.account_id and a.tenant_id=r.tenant_id where r.tenant_id=:tenant
          union all
          select p.id,'points',case p.kind when 'manual_grant' then 'points_grant' when 'manual_deduct' then 'points_deduct' when 'redemption' then 'points_redemption' else 'reversal' end,
            'completed',p.created_at,p.created_at,p.tenant_id,p.store_id,a.customer_id,0,p.quantity,0,0,0,0,0,0,null,
            concat_ws(' · ',p.reason,nullif(p.note,''),d.redemption_description),p.actor_id,
            case when p.original_entry_id is not null then 'points' end,p.original_entry_id,concat_ws(' ',p.id::text,p.external_reference),
            case when p.after_balance>p.before_balance then 'in' else 'out' end
          from loyalty_entries p join loyalty_accounts a on a.id=p.account_id and a.tenant_id=p.tenant_id
          join wallet_loyalty_bindings w on w.tenant_id=p.tenant_id and w.loyalty_program_id=p.program_id and w.operating_entity_id=p.operating_entity_id
          join wallet_program_stores ws on ws.tenant_id=p.tenant_id and ws.program_id=w.wallet_program_id and ws.store_id=p.store_id
          left join loyalty_redemptions d on d.tenant_id=p.tenant_id and d.entry_id=p.id where p.tenant_id=:tenant
          union all
          select x.id,'money_reversal','reversal','completed',x.created_at,e.created_at,e.tenant_id,e.store_id,a.customer_id,
            x.bonus_cents,0,-x.bonus_cents,0,x.bonus_cents,0,0,0,null,x.reason,x.created_by,
            case when l.topup_id is not null then 'topup' end,l.topup_id,x.id::text,'out'
          from wallet_bonus_exits x join wallet_entries e on e.tenant_id=x.tenant_id and e.source_id=x.id and e.kind='bonus_revoke'
          join wallet_accounts a on a.id=x.account_id and a.tenant_id=x.tenant_id
          join wallet_funding_lots l on l.id=x.lot_id and l.tenant_id=x.tenant_id where x.tenant_id=:tenant
        ), records as (
          select x.*,coalesce(x.posted_at,x.created_at) occurred_at,s.display_name store_name,s.timezone time_zone,'SGD'::text currency,
            c.display_name customer_name,c.phone_e164 customer_phone,
            coalesce(staff.display_name,case when x.actor_id=x.customer_id then c.display_name else '历史操作人' end) actor_name
          from source x join stores s on s.id=x.store_id and s.tenant_id=x.tenant_id
          join customers c on c.id=x.customer_id and c.tenant_id=x.tenant_id
          left join auth_accounts staff on staff.id=x.actor_id and staff.tenant_id=x.tenant_id
        )
        """;
}
