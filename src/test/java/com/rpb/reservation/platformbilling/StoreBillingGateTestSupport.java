package com.rpb.reservation.platformbilling;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.persistence.entity.*;
import com.rpb.reservation.appgate.persistence.repository.*;
import com.rpb.reservation.common.product.ProductLineEntryRegistry;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Database-backed boundary adapter: tests the real AppGate and real commercial projection without JPA bootstrapping. */
final class StoreBillingGateTestSupport {
    static AppGateService gate(JdbcTemplate jdbc,Clock clock) {
        var apps=mock(PlatformAppJpaRepository.class);
        var tenants=mock(TenantAppEntitlementJpaRepository.class);
        var settings=mock(StoreAppSettingJpaRepository.class);
        when(apps.findByAppKey(anyString())).thenAnswer(c->jdbc.query("select * from platform_apps where app_key=?",(rs,n)->PlatformAppEntity.of(rs.getObject("id",UUID.class),rs.getString("app_key"),rs.getString("app_name"),rs.getString("status"),rs.getString("default_entry_route"),null,0,"{}"),(Object)c.getArgument(0)).stream().findFirst());
        when(apps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active")).thenAnswer(c->jdbc.query("select * from platform_apps where status='active'",(rs,n)->PlatformAppEntity.of(rs.getObject("id",UUID.class),rs.getString("app_key"),rs.getString("app_name"),rs.getString("status"),rs.getString("default_entry_route"),null,0,"{}")));
        when(tenants.findByTenantIdAndAppKey(any(),anyString())).thenAnswer(c->entitlements(jdbc,c.getArgument(0)).stream().filter(e->e.getAppKey().equals(c.getArgument(1))).findFirst());
        when(tenants.findAllByTenantId(any())).thenAnswer(c->entitlements(jdbc,c.getArgument(0)));
        when(settings.findByTenantIdAndStoreIdAndAppKey(any(),any(),anyString())).thenAnswer(c->settings(jdbc,c.getArgument(0),c.getArgument(1)).stream().filter(e->e.getAppKey().equals(c.getArgument(2))).findFirst());
        when(settings.findAllByTenantIdAndStoreId(any(),any())).thenAnswer(c->settings(jdbc,c.getArgument(0),c.getArgument(1)));
        return new AppGateService(apps,tenants,settings,new JdbcStoreAppSubscriptionRepository(jdbc),clock);
    }
    private static List<TenantAppEntitlementEntity> entitlements(JdbcTemplate jdbc,UUID tenant) {
        return jdbc.query("select * from tenant_app_entitlements where tenant_id=?",(rs,n)->TenantAppEntitlementEntity.of(rs.getObject("id",UUID.class),tenant,rs.getString("app_key"),rs.getString("status"),rs.getObject("valid_from",OffsetDateTime.class),rs.getObject("valid_until",OffsetDateTime.class),"{}",null,null),tenant);
    }
    private static List<StoreAppSettingEntity> settings(JdbcTemplate jdbc,UUID tenant,UUID store) {
        return jdbc.query("select * from store_app_settings where tenant_id=? and store_id=?",(rs,n)->StoreAppSettingEntity.of(rs.getObject("id",UUID.class),tenant,store,rs.getString("app_key"),rs.getBoolean("is_enabled"),rs.getBoolean("entry_visible"),"{}",null,null,rs.getObject("disabled_at",OffsetDateTime.class)),tenant,store);
    }
}
