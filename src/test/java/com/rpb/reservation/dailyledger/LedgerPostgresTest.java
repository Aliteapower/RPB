package com.rpb.reservation.dailyledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.appgate.domain.AppGateDenyReason;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.dailyledger.application.*;
import com.rpb.reservation.dailyledger.domain.*;
import com.rpb.reservation.dailyledger.persistence.LedgerRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Uses the project pointer database, isolated schema, and actual Flyway SQL. */
class LedgerPostgresTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final UUID TENANT=UUID.randomUUID(),STORE=UUID.randomUUID(),OTHER_TENANT=UUID.randomUUID(),OTHER_STORE=UUID.randomUUID(),ACTOR=UUID.randomUUID();
    static final StoreScope SCOPE=new StoreScope(new TenantId(TENANT),STORE);
    static final String SCHEMA="ledger_test_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate jdbc,adminJdbc;static TransactionTemplate tx;
    LedgerRepository repository;LedgerRules rules;LedgerQueryService queries;LedgerCommandService commands;AppGateService gate;LedgerAccess access;
    CurrentActor admin=new CurrentActor(TENANT,ACTOR,"tenant_admin",Set.of("tenant_admin"),Set.of("daily_ledger.view","daily_ledger.write","daily_ledger.settings.manage"),Set.of(STORE));
    CurrentActor staff=new CurrentActor(TENANT,ACTOR,"staff",Set.of("store_staff"),Set.of("daily_ledger.view","daily_ledger.write"),Set.of(STORE));
    @BeforeAll static void database() throws Exception {
        Path pointer=Path.of("target/local-postgres-current.txt");Assumptions.assumeTrue(Files.exists(pointer),"project PostgreSQL pointer unavailable");
        String port=Files.readAllLines(pointer).stream().filter(l->l.startsWith("port=")).findFirst().orElseThrow().substring(5);
        String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
        var root=new DriverManagerDataSource(url,"postgres","");adminJdbc=new JdbcTemplate(root);
        adminJdbc.execute("create schema "+SCHEMA);
        var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA+",public","postgres","");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))) {
            for(Path sql:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())jdbc.execute(Files.readString(sql));
        }
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'test','active'),(?,?,'other','active')",TENANT,"ledger-test",OTHER_TENANT,"ledger-other");
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,'main','test','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD'),(?,?,'other','other','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",STORE,TENANT,OTHER_STORE,OTHER_TENANT);
        jdbc.update("insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash) values(?,?,'ledger-test','测试员工','staff','active','test-only')",ACTOR,TENANT);
    }
    @AfterAll static void close(){if(adminJdbc!=null)adminJdbc.execute("drop schema if exists "+SCHEMA+" cascade");}
    @BeforeEach void setup(){
        gate=mock(AppGateService.class);when(gate.evaluate(any())).thenReturn(AppGateDecision.allow("daily_ledger",TENANT,STORE,"daily_ledger.view"));
        access=new LedgerAccess(gate);repository=new LedgerRepository(jdbc,JSON);rules=new LedgerRules(JSON);
        queries=new LedgerQueryService(repository,rules,access,JSON);commands=new LedgerCommandService(repository,rules,access,queries,JSON,Clock.fixed(Instant.parse("2026-10-22T01:00:00Z"),ZoneOffset.UTC));
        tx.execute(status->{repository.lockSettings(SCOPE,rules.defaults());var current=repository.settings(SCOPE).orElseThrow();repository.updateSettings(SCOPE,rules.defaults(),JSON.createObjectNode(),current.version());return null;});
    }
    @Test void oldClientCannotReplaceBrandingThroughLedgerSettings() {
        var before = repository.settings(SCOPE).orElseThrow();
        tx.execute(status -> commands.settings(SCOPE, admin, before.version(), before.config(),
            JSON.createObjectNode().put("store_name", "unexpected override").put("logo_data", "arbitrary URL")));
        assertThat(repository.settings(SCOPE).orElseThrow().branding()).isEqualTo(before.branding());
    }

    StoreScope legacyBrandingStore(String code, UUID tenant, String logo) {
        if (tenant == null) {
            tenant = UUID.randomUUID();
            jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')", tenant, code, code);
        }
        UUID store = UUID.randomUUID();
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,?,'active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,code,code);
        var scoped = new StoreScope(new TenantId(tenant),store);
        repository.lockSettings(scoped,rules.defaults());
        repository.updateSettings(scoped,rules.defaults(),JSON.createObjectNode().put("store_name",code).put("logo_data",logo),0);
        return scoped;
    }

    @Test void adoptionIsScopedAndIdempotentEvenAfterSharedLogoIsCleared() throws Exception {
        var profiles = mock(com.rpb.reservation.tenantadmin.application.TenantAdminProfileService.class);
        var profile = mock(com.rpb.reservation.tenantadmin.application.TenantAdminProfile.class);
        when(profiles.getProfile(any())).thenReturn(profile);
        var migration = new com.rpb.reservation.dailyledger.migration.LedgerBrandingMigration(jdbc,JSON,profiles);
        var scoped = tx.execute(status -> legacyBrandingStore("adopt",null,"data:image/png;base64,AQID"));
        var other = tx.execute(status -> legacyBrandingStore("untouched",null,"data:image/png;base64,BAUG"));
        assertThat(tx.<Integer>execute(status -> migration.migrate("adopt"))).isEqualTo(1);
        var file = org.mockito.ArgumentCaptor.forClass(org.springframework.web.multipart.MultipartFile.class);
        verify(profiles).uploadLogo(eq(scoped),file.capture());
        assertThat(file.getValue().getBytes()).containsExactly(1,2,3);
        assertThat(repository.settings(scoped).orElseThrow().branding().path("platform_branding_managed").asBoolean()).isTrue();
        assertThat(repository.settings(other).orElseThrow().branding().has("platform_branding_managed")).isFalse();
        clearInvocations(profiles);
        assertThat(tx.<Integer>execute(status -> migration.migrate("adopt"))).isZero();
        verify(profiles,never()).uploadLogo(any(),any());
    }

    @Test void differentGroupStoreLogosAreNotSilentlyCollapsed() {
        var profiles = mock(com.rpb.reservation.tenantadmin.application.TenantAdminProfileService.class);
        when(profiles.getProfile(any())).thenReturn(mock(com.rpb.reservation.tenantadmin.application.TenantAdminProfile.class));
        var first = tx.execute(status -> legacyBrandingStore("ambiguous",null,"data:image/png;base64,AQID"));
        var second = tx.execute(status -> legacyBrandingStore("other-store",first.tenantId().value(),"data:image/png;base64,BAUG"));
        var migration = new com.rpb.reservation.dailyledger.migration.LedgerBrandingMigration(jdbc,JSON,profiles);
        assertThatThrownBy(() -> tx.execute(status -> migration.migrate("ambiguous"))).isInstanceOf(IllegalStateException.class);
        verify(profiles,never()).uploadLogo(any(),any());
        assertThat(repository.settings(first).orElseThrow().branding().has("platform_branding_managed")).isFalse();
        assertThat(repository.settings(second).orElseThrow().branding().has("platform_branding_managed")).isFalse();
    }
    ObjectNode input(ObjectNode day,String period) {
        ObjectNode in=JSON.createObjectNode();in.set("config_snapshot",day.path("config"));in.set("withdrawal_basis",rules.withdrawalBasis(day.path("config"),day.path("records"),period));return in;
    }
    ObjectNode save(LocalDate date,String period,CurrentActor actor,int version,String key,String action,ObjectNode input){return tx.execute(status->commands.save(SCOPE,date,period,actor,version,key,action,input));}
    @Test void submittedSnapshotSurvivesAdminDraftAndOldReplayIsNotAppliedTwice(){
        LocalDate date=LocalDate.of(2040,1,1);ObjectNode day=queries.day(SCOPE,date,admin),in=input(day,"dinner");in.putObject("cash").putObject("100").put("value","10");
        var submitted=save(date,"dinner",admin,0,"submit-1","submit",in);
        assertThat(submitted.path("submitted_records").path("dinner").path("totals").path("revenue").asText()).isEqualTo("500.00");
        ObjectNode draft=input(submitted,"dinner");draft.putObject("cash").putObject("100").put("value","12");
        var updated=save(date,"dinner",admin,1,"draft-2","draft",draft);
        assertThat(updated.path("records").path("dinner").path("totals").path("revenue").asText()).isEqualTo("700.00");
        assertThat(updated.path("submitted_records").path("dinner").path("totals").path("revenue").asText()).isEqualTo("500.00");
        var replay=save(date,"dinner",admin,0,"submit-1","submit",in);
        assertThat(replay.path("records").path("dinner").path("version").asInt()).isEqualTo(2);
        assertThat(repository.audit(SCOPE,date,"dinner")).hasSize(2);
        assertThatThrownBy(()->save(date,"dinner",admin,1,"submit-1","submit",draft)).isInstanceOf(LedgerException.class).hasMessageContaining("保存编号");
        assertThatThrownBy(()->save(date,"dinner",staff,2,"staff-locked","draft",draft)).isInstanceOf(LedgerException.class).hasMessageContaining("已提交");
        assertThatThrownBy(()->save(date,"dinner",admin,1,"stale","draft",draft)).isInstanceOf(LedgerException.class).hasMessageContaining("更新");
    }
    @Test void finalShiftRejectsChangedSkimmingSourceAndConfigFreezesAtFirstSave(){
        LocalDate date=LocalDate.of(2040,1,2);var day=queries.day(SCOPE,date,admin);var closing=input(day,"dinner");
        var lunch=input(day,"lunch");lunch.putArray("withdrawals").addObject().put("id","w1").put("value","200.00");
        var updated=save(date,"lunch",staff,0,"lunch","draft",lunch);
        assertThatThrownBy(()->save(date,"dinner",admin,0,"stale-source","submit",closing)).isInstanceOf(LedgerException.class).hasMessageContaining("前班");
        var finalInput=input(updated,"dinner");finalInput.putObject("cash").putObject("100").put("value","10");
        var result=save(date,"dinner",admin,0,"dinner","submit",finalInput);
        assertThat(result.path("submitted_records").path("dinner").path("totals").path("handover").asText()).isEqualTo("700.00");
        var settings=queries.settings(SCOPE,admin);ObjectNode newConfig=settings.config().deepCopy();newConfig.put("base_amount","600.00");
        ObjectNode brand=JSON.createObjectNode().put("store_name","Test").put("store_name_en","").put("logo_data","");
        tx.execute(status->commands.settings(SCOPE,admin,settings.version(),newConfig,brand));
        assertThatThrownBy(()->tx.execute(status->commands.settings(SCOPE,admin,settings.version(),rules.defaults(),brand)))
            .isInstanceOf(LedgerException.class).hasMessageContaining("设置已更新");
        assertThat(queries.day(SCOPE,date,admin).path("config").path("base_amount").asText()).isEqualTo("500.00");
        assertThat(queries.day(SCOPE,date.plusDays(1),admin).path("config").path("base_amount").asText()).isEqualTo("600.00");
    }
    @Test void tenantStoreAndAppGateFailuresDenyBeforeDatabaseAccess(){
        assertThatThrownBy(()->queries.day(new StoreScope(new TenantId(OTHER_TENANT),OTHER_STORE),LocalDate.now(),admin)).isInstanceOf(LedgerException.class).hasMessageContaining("无权");
        for(var reason:List.of(AppGateDenyReason.APP_DISABLED,AppGateDenyReason.TENANT_APP_NOT_ENABLED,AppGateDenyReason.STORE_APP_NOT_ENABLED,AppGateDenyReason.PERMISSION_DENIED)){
            when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("daily_ledger",TENANT,STORE,"daily_ledger.view",reason));
            assertThatThrownBy(()->queries.settings(SCOPE,admin)).isInstanceOf(LedgerException.class);
        }
    }
    @Test void realPostgresRejectsCrossTenantStoreAndCatalogPricesAreCorrect(){
        assertThatThrownBy(()->repository.lockSettings(new StoreScope(new TenantId(TENANT),OTHER_STORE),rules.defaults())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select amount from platform_product_line_prices where app_key='daily_ledger' and billing_cycle='monthly'",java.math.BigDecimal.class)).isEqualByComparingTo("30.00");
        assertThat(jdbc.queryForObject("select amount from platform_product_line_prices where app_key='daily_ledger' and billing_cycle='yearly'",java.math.BigDecimal.class)).isEqualByComparingTo("360.00");
    }
    @Test void deniedForeignStoreIsAuditedWithoutViolatingTenantStoreForeignKey(){
        var auditJpa=mock(com.rpb.reservation.appgate.persistence.repository.AppGateAuditLogJpaRepository.class);
        when(auditJpa.save(any())).thenAnswer(invocation->{
            com.rpb.reservation.appgate.persistence.entity.AppGateAuditLogEntity row=invocation.getArgument(0);
            jdbc.update("insert into app_gate_audit_logs(id,tenant_id,store_id,app_key,action,after_json) values(?,?,?,?,?,?::jsonb)",row.getId(),row.getTenantId(),row.getStoreId(),row.getAppKey(),row.getAction(),row.getAfterJson());return row;
        });
        var auditService=new com.rpb.reservation.appgate.application.AppGateDenialAuditService(auditJpa);
        auditService.recordDenial(AppGateDecision.deny("daily_ledger",TENANT,OTHER_STORE,"daily_ledger.view",AppGateDenyReason.STORE_ACCESS_DENIED),admin);
        assertThat(jdbc.queryForObject("select count(*) from app_gate_audit_logs where tenant_id=? and store_id is null and after_json->>'requestedStoreId'=?",Integer.class,TENANT,OTHER_STORE.toString())).isEqualTo(1);
    }
    @Test void concurrentFirstSaveCreatesOneRecordAndOneAudit() throws Exception {
        LocalDate date=LocalDate.of(2040,1,4);var in=input(queries.day(SCOPE,date,admin),"lunch");
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for(int i=0;i<2;i++){final String key="concurrent-"+i;futures.add(executor.submit(()->{
                start.await();try{save(date,"lunch",staff,0,key,"draft",in);return "saved";}catch(LedgerException error){return error.code();}
            }));}
            start.countDown();var outcomes=new java.util.ArrayList<String>();for(var future:futures)outcomes.add(future.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("saved","VERSION_CONFLICT");
        }
        assertThat(repository.records(SCOPE,date)).hasSize(1);assertThat(repository.audit(SCOPE,date,"lunch")).hasSize(1);
    }
}
