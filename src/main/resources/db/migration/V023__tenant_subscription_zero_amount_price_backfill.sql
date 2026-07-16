with zero_amount_subscriptions as (
    select
        subscription.id,
        subscription.tenant_id,
        price.amount as unit_amount,
        price.currency,
        case subscription.billing_cycle
            when 'monthly' then greatest(
                1,
                (
                    extract(year from age(subscription.current_period_end, subscription.current_period_start))::integer * 12
                    + extract(month from age(subscription.current_period_end, subscription.current_period_start))::integer
                )
            )
            when 'yearly' then greatest(
                1,
                extract(year from age(subscription.current_period_end, subscription.current_period_start))::integer
            )
            else 0
        end as duration_count
    from tenant_product_subscriptions subscription
    join platform_product_line_prices price
      on price.app_key = subscription.app_key
     and price.billing_cycle = subscription.billing_cycle
     and price.status = 'active'
    where subscription.billing_cycle in ('monthly', 'yearly')
      and subscription.current_period_start is not null
      and subscription.current_period_end is not null
      and subscription.current_period_end > subscription.current_period_start
      and subscription.amount = 0
      and price.amount > 0
)
update tenant_product_subscriptions subscription
set amount = zero_amount.unit_amount * zero_amount.duration_count,
    currency = zero_amount.currency,
    updated_at = now(),
    version = version + 1
from zero_amount_subscriptions zero_amount
where subscription.id = zero_amount.id
  and subscription.tenant_id = zero_amount.tenant_id
  and zero_amount.duration_count > 0;
