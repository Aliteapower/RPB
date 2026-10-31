package com.rpb.reservation.wallet.transactions;

import com.rpb.reservation.common.scope.StoreScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

/** Uses the same completed business projection as transaction history, without pagination. */
@Repository
public class WalletBusinessReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public WalletBusinessReportRepository(JdbcTemplate jdbc){this.jdbc=new NamedParameterJdbcTemplate(jdbc);}
    public List<WalletBusinessReport.Row> query(StoreScope scope,Instant from,Instant to,String timezone,String bucket,boolean points){
        var p=new MapSqlParameterSource("tenant",scope.tenantId().value()).addValue("store",scope.storeId().value()).addValue("from",Timestamp.from(from)).addValue("to",Timestamp.from(to)).addValue("zone",timezone).addValue("bucket",bucket).addValue("points",points);
        return jdbc.query(TransactionProjection.CTE+"""
          , activity as (
            select r.occurred_at at,v.metric,''::text channel,''::text channel_name,v.amount
            from records r cross join lateral (values
              ('consumption',wallet_paid+external_paid,type='payment'),
              ('walletPayment',wallet_paid,type='payment' and wallet_paid<>0),
              ('spentPrincipal',principal,type='payment' and principal<>0),
              ('spentBonus',bonus,type='payment' and bonus<>0),
              ('externalPayment',external_paid,type='payment' and external_paid<>0),
              ('topupPrincipal',principal,type='topup'),
              ('topupBonus',bonus,type='topup' and bonus<>0),
              ('topupCredit',credit,type='topup'),
              ('consumptionRefund',amount_cents,type='refund' and original_source_type='bill'),
              ('topupRefund',amount_cents,type='refund' and original_source_type='topup'),
              ('bonusReversal',amount_cents,source_type='money_reversal'),
              ('pointsGrant',quantity,type='points_grant' and :points),
              ('pointsDeduct',quantity,type='points_deduct' and :points),
              ('pointsRedeem',quantity,type='points_redemption' and :points),
              ('pointsReversalIn',quantity,source_type='points' and type='reversal' and direction='in' and :points),
              ('pointsReversalOut',quantity,source_type='points' and type='reversal' and direction='out' and :points)
            ) v(metric,amount,included)
            where r.tenant_id=:tenant and r.store_id=:store and r.status='completed'
              and r.occurred_at>=:from and r.occurred_at<:to and v.included
            union all
            select c.created_at,case when c.topup_id is not null then 'topupReceipt' else 'consumptionReceipt' end,
              c.channel,coalesce(c.channel_name,''),c.amount_cents
            from wallet_collections c
            where c.tenant_id=:tenant and c.store_id=:store and not c.is_exception
              and c.created_at>=:from and c.created_at<:to
              and (c.topup_id is not null or c.bill_id is not null)
          )
          select to_char(date_trunc(:bucket,at at time zone :zone),'YYYY-MM-DD') bucket,
            metric,channel,channel_name,sum(amount) amount,count(*) count
          from activity group by 1,metric,channel,channel_name order by 1,metric,channel,channel_name
          """,p,(r,n)->new WalletBusinessReport.Row(r.getString("bucket"),r.getString("metric"),r.getString("channel"),r.getString("channel_name"),r.getLong("amount"),r.getLong("count")));
    }
}
