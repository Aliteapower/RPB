package com.rpb.reservation.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.StoreAppSubscription;
import com.rpb.reservation.appgate.persistence.entity.*;
import com.rpb.reservation.appgate.persistence.repository.*;
import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.auth.application.*;
import com.rpb.reservation.auth.authorization.StaffPermissionCatalog;
import com.rpb.reservation.auth.persistence.AuthRepository;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.application.*;
import com.rpb.reservation.tenantadmin.persistence.StaffPermissionsRepository;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminStaffRepository;
import com.rpb.reservation.tenantadmin.api.*;
import com.rpb.reservation.auth.api.AuthController;
import com.rpb.reservation.auth.security.AuthCookieService;
import com.rpb.reservation.auth.security.AuthSessionFilter;
import com.rpb.reservation.common.web.HostPrefixContextResolver;
import com.rpb.reservation.walkin.api.SecurityContextCurrentActorProvider;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.WalletAccess;
import com.rpb.reservation.wallet.application.WalletDirectory;
import com.rpb.reservation.wallet.domain.WalletException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL, session reload, transactions and authorization in an owned disposable schema. */
class StaffPermissionsPostgresTest {
    static final String SCHEMA="staff_permissions_test_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate root,jdbc;
    static DataSourceTransactionManager transactions;
    UUID tenant,store,sibling,staffId,adminId;
    StoreScope scope;
    CurrentActor admin;
    AuthRepository auth;
    AuthApplicationService sessions;
    AppGateService gate;
    StaffPermissionsRepository repository;
    StaffPermissionsService service;
    AuditLogRepositoryPort audit;
    final String token=UUID.randomUUID().toString();

    @BeforeAll static void database() throws Exception {
        var pointer=new HashMap<String,String>();
        for(String line:Files.readAllLines(Path.of("target/local-postgres-current.txt"))) {
            String[] pair=line.split("=",2); if(pair.length==2)pointer.put(pair[0],pair[1]);
        }
        assertThat(pointer.get("runtimeWorktree").replace('\\','/')).isEqualToIgnoringCase(Path.of("").toAbsolutePath().toString().replace('\\','/'));
        int port=Integer.parseInt(pointer.get("port"));
        String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
        root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));
        root.execute("create schema "+SCHEMA);
        var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA,"postgres","");
        jdbc=new JdbcTemplate(ds); transactions=new DataSourceTransactionManager(ds);
        try(var paths=Files.list(Path.of("src/main/resources/db/migration"))) {
            for(var file:paths.filter(path->path.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())
                jdbc.execute(Files.readString(file));
        }
    }
    @AfterAll static void cleanup() {
        if(root!=null&&SCHEMA.matches("staff_permissions_test_[a-f0-9]{32}")) root.execute("drop schema if exists "+SCHEMA+" cascade");
    }
    @BeforeEach void setup() {
        tenant=UUID.randomUUID(); store=UUID.randomUUID(); sibling=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Test','active')",tenant,"test-"+tenant);
        for(UUID id:List.of(store,sibling)) jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'Test','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",id,tenant,"s-"+id);
        staffId=account("store_staff",Set.of("wallet.view","wallet.topup.create","custom.unmanaged"));
        adminId=account("tenant_admin",StaffPermissionCatalog.permissions());
        scope=new StoreScope(new TenantId(tenant),store);
        var grants=new HashSet<>(StaffPermissionCatalog.permissions()); grants.add("tenant.admin.manage");
        admin=new CurrentActor(tenant,adminId,"staff",Set.of("tenant_admin"),grants,Set.of(store,sibling));
        auth=new AuthRepository(jdbc); sessions=new AuthApplicationService(auth,new BCryptPasswordEncoder());
        var staff=auth.findActiveAccountByUsername("u-"+staffId).orElseThrow();
        auth.createSession(UUID.randomUUID(),staff,AuthApplicationService.hashSessionToken(token),Instant.now().plusSeconds(3600),"test","test");
        gate=mock(AppGateService.class);
        when(gate.isStoreProductProvisioned(anyString(),eq(tenant),any())).thenReturn(true);
        when(gate.evaluateStoreActivation(anyString(),eq(tenant),any())).thenAnswer(call->com.rpb.reservation.appgate.domain.AppGateDecision.allow(call.getArgument(0),tenant,call.getArgument(2),null));
        when(gate.evaluateCommercial(anyString(),eq(tenant),any())).thenAnswer(call->com.rpb.reservation.appgate.domain.AppGateDecision.allow(call.getArgument(0),tenant,call.getArgument(2),null));
        audit=mock(AuditLogRepositoryPort.class);
        when(audit.append(any(StoreScope.class),any(AuditLog.class))).thenAnswer(call->{
            StoreScope s=call.getArgument(0); AuditLog log=call.getArgument(1);
            jdbc.update("insert into audit_logs(id,tenant_id,store_id,operation_code,target_type,target_id,actor_type,actor_id,source,metadata) values(?,?,?,?,?,?,?,?,?,?::jsonb)",log.id(),s.tenantId().value(),s.storeId().value(),log.operationCode(),log.targetType(),log.targetId(),log.actorType(),log.actorId(),log.source(),log.metadata());
            return log;
        });
        repository=new StaffPermissionsRepository(jdbc);
        service=proxy(new StaffPermissionsService(repository,gate,new StaffPermissionsAuditService(audit,new ObjectMapper())));
    }
    StaffPermissionsService proxy(StaffPermissionsService target) {
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        return (StaffPermissionsService)proxy.getProxy();
    }
    UUID account(String role,Set<String> permissions) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash,default_store_id) values(?,?,?,'Test','staff','active','unused',?)",id,tenant,"u-"+id,store);
        jdbc.update("insert into auth_account_roles(account_id,role_code) values(?,?)",id,role);
        for(UUID s:List.of(store,sibling))jdbc.update("insert into auth_account_store_access(account_id,tenant_id,store_id) values(?,?,?)",id,tenant,s);
        for(String p:permissions)jdbc.update("insert into auth_account_permissions(account_id,permission_code) values(?,?)",id,p);
        return id;
    }
    CurrentActor employee(UUID s) { return sessions.currentUser(token).toCurrentActor(s); }
    int audits() { return jdbc.queryForObject("select count(*) from audit_logs where target_id=?",Integer.class,staffId); }

    @Test void migrationAndStoreOverridesPreserveSiblingAndUnmanagedPermissions() {
        assertThat(service.get(scope,admin,staffId).inherited()).isTrue();
        assertThat(employee(store).hasPermission("wallet.topup.create")).isTrue();
        var saved=service.put(scope,admin,staffId,0L,List.of("wine_storage.receive"));
        assertThat(saved.permissions()).contains("wine_storage.view","wine_storage.receive");
        assertThat(employee(store).permissions()).contains("wine_storage.receive","custom.unmanaged").doesNotContain("wallet.topup.create");
        assertThat(employee(sibling).permissions()).contains("wallet.topup.create").doesNotContain("wine_storage.receive");
        assertThat(audits()).isEqualTo(1);
        var metadata=jdbc.queryForObject("select metadata::text from audit_logs where target_id=?",String.class,staffId);
        assertThat(metadata).contains("wine_storage.receive").doesNotContain("unused");
        assertThat(jdbc.queryForObject("select actor_id from audit_logs where target_id=?",UUID.class,staffId)).isEqualTo(adminId);
    }
    @Test void explicitEmptyRevokesSameSessionAndActualWalletAccessRejectsIt() {
        var access=new WalletAccess(gate,mock(WalletDirectory.class));
        access.staff(scope,employee(store),"wallet.view",false);
        var saved=service.put(scope,admin,staffId,0L,List.of());
        assertThat(saved.inherited()).isFalse();
        assertThat(employee(store).permissions()).containsExactly("custom.unmanaged");
        assertThatThrownBy(()->access.staff(scope,employee(store),"wallet.view",false)).isInstanceOf(WalletException.class);
        service.put(scope,admin,staffId,0L,List.of());
        assertThat(audits()).isEqualTo(1);
    }
    @Test void auditFailureRollsBackAuthorizationAndVersion() {
        when(audit.append(any(StoreScope.class),any(AuditLog.class))).thenThrow(new IllegalStateException("audit unavailable"));
        assertThatThrownBy(()->service.put(scope,admin,staffId,0L,List.of("wine_storage.view"))).isInstanceOf(RuntimeException.class);
        assertThat(service.get(scope,admin,staffId).inherited()).isTrue();
        assertThat(employee(store).hasPermission("wallet.topup.create")).isTrue();
        assertThat(audits()).isZero();
    }
    @Test void databaseRejectsCrossTenantAccountAndStorePair() {
        UUID other=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Other','active')",other,"test-"+other);
        assertThatThrownBy(()->jdbc.update("insert into auth_staff_store_permissions(account_id,tenant_id,store_id,permissions,version) values(?,?,?,ARRAY[]::text[],1)",staffId,other,store)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void concurrentDifferentSavesDoNotSilentlyRestoreStalePermissions() throws Exception {
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var tasks=new ArrayList<Future<Boolean>>();
            for(String p:List.of("wine_storage.view","daily_ledger.view")) tasks.add(executor.submit(()->{
                start.await(); try { service.put(scope,admin,staffId,0L,List.of(p)); return true; }
                catch(com.rpb.reservation.tenantadmin.api.TenantAdminApiException e) { assertThat(e.code().name()).isEqualTo("PERMISSION_VERSION_CONFLICT"); return false; }
            }));
            start.countDown(); int successes=0; for(var result:tasks)if(result.get(20,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1); assertThat(audits()).isEqualTo(1);
            assertThat(service.get(scope,admin,staffId).version()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void configuredPermissionStillCannotBypassClosedOrExpiredProduct() {
        service.put(scope,admin,staffId,0L,List.of("wallet.collection.confirm","wallet.charge.create"));
        var apps=mock(PlatformAppJpaRepository.class);var entitlements=mock(TenantAppEntitlementJpaRepository.class);
        var settings=mock(StoreAppSettingJpaRepository.class);var subscriptions=mock(StoreAppSubscriptionRepository.class);
        when(apps.findByAppKey("wallet")).thenReturn(Optional.of(PlatformAppEntity.of(UUID.randomUUID(),"wallet","Wallet","active","/stores/:storeId/wallet",null,30,"{}")));
        when(entitlements.findByTenantIdAndAppKey(tenant,"wallet")).thenReturn(Optional.of(TenantAppEntitlementEntity.of(UUID.randomUUID(),tenant,"wallet","enabled",null,null,"{}",null,null)));
        when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,"wallet")).thenReturn(Optional.of(StoreAppSettingEntity.of(UUID.randomUUID(),tenant,store,"wallet",false,true,"{}",null,null,null)));
        var actual=new AppGateService(apps,entitlements,settings,subscriptions,Clock.systemUTC());
        var request=new AppGateAccessRequest("wallet",tenant,store,"wallet.charge.create",employee(store));
        assertThat(actual.evaluate(request).denyReason().name()).isEqualTo("STORE_APP_NOT_ENABLED");
        when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,"wallet")).thenReturn(Optional.of(StoreAppSettingEntity.of(UUID.randomUUID(),tenant,store,"wallet",true,true,"{}",null,null,null)));
        when(subscriptions.findByTenantAndStore(tenant,store)).thenReturn(Map.of("wallet",new StoreAppSubscription("active","active",OffsetDateTime.now().minusDays(1))));
        assertThat(actual.evaluate(request).denyReason().name()).isEqualTo("STORE_APP_EXPIRED");
    }
    @Test void administratorHttpSaveUpdatesExistingEmployeeSessionAndAuthMeMap() throws Exception {
        jdbc.update("insert into auth_account_permissions(account_id,permission_code) values(?,'tenant.admin.manage')",adminId);
        var adminAccount=auth.findActiveAccountByUsername("u-"+adminId).orElseThrow();
        String adminToken="admin-"+token;
        auth.createSession(UUID.randomUUID(),adminAccount,AuthApplicationService.hashSessionToken(adminToken),Instant.now().plusSeconds(3600),"test","test");
        var cookies=new AuthCookieService();var filter=new AuthSessionFilter(cookies,sessions);
        Filter context=(req,res,chain)->{SecurityContextHolder.clearContext();try{filter.doFilter(req,res,chain);}finally{SecurityContextHolder.clearContext();}};
        var mvc=MockMvcBuilders.standaloneSetup(
            new StaffPermissionsController(new TenantAdminScopeResolver(new SecurityContextCurrentActorProvider()),service),
            new AuthController(sessions,cookies,new HostPrefixContextResolver(""))).addFilters(context).build();
        String path="/api/v1/stores/"+store+"/tenant-admin/staff/"+staffId+"/permissions";
        var adminCookie=new Cookie(AuthCookieService.COOKIE_NAME,adminToken);
        var staffCookie=new Cookie(AuthCookieService.COOKIE_NAME,token);
        mvc.perform(get(path).cookie(staffCookie)).andExpect(status().isForbidden());
        mvc.perform(get(path).cookie(adminCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.inherited").value(true));
        mvc.perform(put(path).cookie(adminCookie).header("X-RPB-Staff-Permissions","1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":0,\"permissions\":[\"wine_storage.receive\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        var first=mvc.perform(get("/api/v1/auth/me").cookie(staffCookie)).andExpect(status().isOk()).andReturn();
        var body=new ObjectMapper().readTree(first.getResponse().getContentAsString());
        assertThat(body.path("user").path("storePermissions").path(store.toString()).toString()).contains("wine_storage.receive").doesNotContain("wallet.topup.create");
        mvc.perform(put(path).cookie(adminCookie).header("X-RPB-Staff-Permissions","1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":1,\"permissions\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.permissions").isEmpty());
        assertThat(employee(store).hasPermission("wine_storage.view")).isFalse();
        assertThat(employee(sibling).hasPermission("wallet.topup.create")).isTrue();
        assertThat(audits()).isEqualTo(2);
    }
    @Test void staffManagementHttpCannotAddOrRemoveStoresOutsideAdministratorScope() throws Exception {
        var staffService=new TenantAdminStaffService(new TenantAdminStaffRepository(jdbc),new BCryptPasswordEncoder(),
            new TenantAdminStaffAuditService(audit,new ObjectMapper()));
        var factory=new ProxyFactory(staffService);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        var restricted=new CurrentActor(tenant,adminId,"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store));
        var controller=new TenantAdminController((TenantAdminStaffService)factory.getProxy(),null,null,null,null,null,null,
            new TenantAdminScopeResolver(()->Optional.of(restricted)),null);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        String path="/api/v1/stores/"+store+"/tenant-admin/staff";
        var mapper=new ObjectMapper();
        String forged="forged-"+UUID.randomUUID();
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
            "employeeNo",forged,"name","Test","password","abc123","storeIds",List.of(store,sibling),"defaultStoreId",store))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(jdbc.queryForObject("select count(*) from auth_accounts where username=?",Integer.class,forged)).isZero();
        String allowed="allowed-"+UUID.randomUUID();
        var created=mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
            "employeeNo",allowed,"name","Test","password","abc123","storeIds",List.of(store),"defaultStoreId",store))))
            .andExpect(status().isCreated()).andReturn();
        String createdId=mapper.readTree(created.getResponse().getContentAsString()).path("staff").path("id").asText();
        mvc.perform(patch(path+"/"+createdId).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("storeIds",List.of(store,sibling),"defaultStoreId",store))))
            .andExpect(status().isForbidden());
        mvc.perform(patch(path+"/"+staffId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.staff.name").value("Updated"));
        assertThat(jdbc.queryForList("select store_id from auth_account_store_access where account_id=? and deleted_at is null",UUID.class,staffId)).containsExactlyInAnyOrder(store,sibling);
        mvc.perform(patch(path+"/"+staffId).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("storeIds",List.of(store),"defaultStoreId",store))))
            .andExpect(status().isForbidden());
        mvc.perform(patch(path+"/"+staffId).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("defaultStoreId",sibling))))
            .andExpect(status().isForbidden());
        // An employee's already established default at another store is preserved by ordinary profile edits.
        jdbc.update("update auth_accounts set default_store_id=? where id=?",sibling,staffId);
        mvc.perform(patch(path+"/"+staffId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Profile only\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.staff.defaultStoreId").value(sibling.toString()));
        assertThat(jdbc.queryForList("select store_id from auth_account_store_access where account_id=? and deleted_at is null",UUID.class,staffId)).containsExactlyInAnyOrder(store,sibling);
    }
}
