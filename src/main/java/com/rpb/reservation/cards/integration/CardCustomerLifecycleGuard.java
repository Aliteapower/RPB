package com.rpb.reservation.cards.integration;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customer.application.port.out.CustomerLifecycleGuard;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.common.scope.TenantScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class CardCustomerLifecycleGuard implements CustomerLifecycleGuard {
 private final JdbcTemplate jdbc;public CardCustomerLifecycleGuard(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void beforeArchive(TenantScope s,CustomerId customer){if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from customer_cards where tenant_id=? and customer_id=?) or exists(select 1 from card_purchase_orders where tenant_id=? and customer_id=?)",Boolean.class,s.tenantId().value(),customer.value(),s.tenantId().value(),customer.value())))throw new CustomerManagementException(CustomerManagementError.CUSTOMER_HAS_RETAINED_ASSETS);}
}
