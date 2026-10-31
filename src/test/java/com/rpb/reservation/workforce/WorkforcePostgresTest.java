package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.auth.application.WorkforceAccountProvisioner;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.persistence.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import static com.rpb.reservation.workforce.application.WorkforceDtos.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkforcePostgresTest {
    static final String SCHEMA="workforce_test_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate jdbc,root;
    static DataSourceTransactionManager tx;
    UUID tenant,company,otherCompany,store,sibling,otherStore,adminId;
    CurrentActor admin;
    WorkforceDirectory directory;
    WorkforceEmployeeRepository repository;
    WorkforceAccess access;
    WorkforceEmployeeService employees;
    WorkforceCompanyService companies;
    WorkforceAdminService admins;
    AuditLogRepositoryPort logs;
    AppGateService gate;
    Clock clock=Clock.fixed(Instant.parse("2026-10-28T04:00:00Z"),ZoneOffset.UTC);
    @BeforeAll static void database()throws Exception{
        var pointer=new HashMap<String,String>();
        for(var line:Files.readAllLines(Path.of("target/local-postgres-current.txt"))){var p=line.split("=",2);if(p.length==2)pointer.put(p[0],p[1]);}
        assertThat(Path.of(pointer.get("runtimeWorktree")).toAbsolutePath()).isEqualTo(Path.of("").toAbsolutePath());
        String url="jdbc:postgresql://127.0.0.1:"+Integer.parseInt(pointer.get("port"))+"/postgres?stringtype=unspecified";
        root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+SCHEMA);
        var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA,"postgres","");jdbc=new JdbcTemplate(ds);tx=new DataSourceTransactionManager(ds);
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var p:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())jdbc.execute(Files.readString(p));}
    }
    @AfterAll static void cleanup(){if(root!=null&&SCHEMA.matches("workforce_test_[a-f0-9]{32}"))root.execute("drop schema if exists "+SCHEMA+" cascade");}
    @BeforeEach void setup(){
        tenant=UUID.randomUUID();company=UUID.randomUUID();otherCompany=UUID.randomUUID();store=UUID.randomUUID();sibling=UUID.randomUUID();otherStore=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Test','active')",tenant,"t-"+tenant);
        for(UUID c:List.of(company,otherCompany))jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,?,'Company','active')",c,tenant,"c-"+c);
        for(UUID s:List.of(store,sibling,otherStore))jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,?,'Store','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",s,tenant,s.equals(otherStore)?otherCompany:company,"s-"+s);
        adminId=account("tenant_admin",Set.of(store,sibling,otherStore),Set.of("workforce.view","workforce.admin.manage","tenant.admin.manage"));
        admin=new CurrentActor(tenant,adminId,"staff",Set.of("tenant_admin"),Set.of("workforce.view","workforce.admin.manage","tenant.admin.manage"),Set.of(store,sibling,otherStore));
        directory=new WorkforceDirectory(jdbc);repository=new WorkforceEmployeeRepository(jdbc);gate=mock(AppGateService.class);
        jdbc.update("insert into workforce_positions(tenant_id,name,version) values(?,'Service',1)",tenant);
        when(gate.evaluate(any())).thenAnswer(c->{AppGateAccessRequest r=c.getArgument(0);return AppGateDecision.allow(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission());});
        access=new WorkforceAccess(gate,directory);logs=mock(AuditLogRepositoryPort.class);
        var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());
        var provisioning=new WorkforceAccountProvisioner(jdbc,new BCryptPasswordEncoder());
        employees=proxy(new WorkforceEmployeeService(access,directory,repository,provisioning,audit,clock,new com.rpb.reservation.workforce.positions.EmployeePositionSelection(new com.rpb.reservation.workforce.positions.PositionRepository(jdbc))));
        companies=proxy(new WorkforceCompanyService(access,new WorkforceCompanyRepository(jdbc),audit));
        admins=proxy(new WorkforceAdminService(access,directory,new WorkforceAdminRepository(jdbc),provisioning,audit));
    }
    @SuppressWarnings("unchecked") <T>T proxy(T value){var p=new ProxyFactory(value);p.setProxyTargetClass(true);p.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));return (T)p.getProxy();}
    UUID account(String role,Set<UUID> stores,Set<String> permissions){
        UUID id=UUID.randomUUID();jdbc.update("insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash) values(?,?,?,'Person','staff','active','old-hash')",id,tenant,"u-"+id);
        jdbc.update("insert into auth_account_roles(account_id,role_code) values(?,?)",id,role);
        for(var s:stores)jdbc.update("insert into auth_account_store_access(account_id,tenant_id,store_id) values(?,?,?)",id,tenant,s);
        for(var p:permissions)jdbc.update("insert into auth_account_permissions(account_id,permission_code) values(?,?)",id,p);return id;
    }
    EmployeeWrite command(long version,List<UUID> stores,UUID employer,UUID account,String username){
        return new EmployeeWrite(version,"E001","Alice","+6591234567","alice@example.com","Service",LocalDate.of(2026,10,1),null,"active",employer,
            version==0?LocalDate.of(2026,10,1):LocalDate.of(2026,10,28),store,stores,account,username,username==null?null:"123456");
    }
    @Test void newAccountHasOnlyWorkforcePermissionAndCreateRetryDoesNotDuplicate(){
        UUID id=UUID.randomUUID();var c=command(0,List.of(store,sibling),company,null,"clock-"+UUID.randomUUID().toString().substring(0,8));
        var e=employees.save(admin,store,id,c);
        assertThat(jdbc.queryForList("select permission_code from auth_account_permissions where account_id=? and deleted_at is null",String.class,e.accountId())).containsExactly("workforce.view");
        assertThat(jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,e.accountId())).doesNotContain("123456");
        assertThat(employees.save(admin,store,id,c).accountId()).isEqualTo(e.accountId());
        verify(logs,times(1)).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
    }
    @Test void bindingPreservesExistingPasswordAndOtherProductPermissions(){
        UUID a=account("store_staff",Set.of(store),Set.of("wallet.view"));
        employees.save(admin,store,UUID.randomUUID(),command(0,List.of(store),company,a,null));
        assertThat(jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,a)).isEqualTo("old-hash");
        assertThat(jdbc.queryForList("select permission_code from auth_account_permissions where account_id=?",String.class,a)).containsExactlyInAnyOrder("wallet.view","workforce.view");
    }
    @Test void unchangedAccountAccessAndDisabledLoginDoNotBlockEmployeeMaintenance(){
        UUID account=account("store_staff",Set.of(store,sibling),Set.of("wallet.view")),id=UUID.randomUUID();
        var e=employees.save(admin,store,id,command(0,List.of(store),company,account,null));
        UUID manager=account("store_manager",Set.of(store),Set.of("workforce.view"));admins.save(admin,store,manager,new AdministratorWrite(0L,true,null));
        var actor=new CurrentActor(tenant,manager,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store));
        jdbc.update("update auth_accounts set status='disabled' where id=?",account);
        var update=new EmployeeWrite(e.version(),e.employeeNo(),e.name(),"+6591111111",e.email(),e.jobTitle(),e.hireDate(),LocalDate.of(2026,10,28),"left",e.companyId(),e.companyEffectiveDate(),e.mainStoreId(),e.storeIds(),e.accountId(),null,null);
        assertThat(employees.save(actor,store,id,update).status()).isEqualTo("left");
    }
    @Test void explicitDelegationDoesNotExposeOtherStoresOrPrivateDetails(){
        UUID id=UUID.randomUUID();employees.save(admin,store,id,command(0,List.of(store,sibling),company,null,null));
        UUID manager=account("store_manager",Set.of(store),Set.of("workforce.view","tenant.admin.manage"));
        admins.save(admin,store,manager,new AdministratorWrite(0L,true,null));
        var actor=new CurrentActor(tenant,manager,"staff",Set.of("tenant_admin","store_manager"),Set.of("workforce.view","tenant.admin.manage"),Set.of(store));
        var item=employees.list(actor,store,50,0).items().getFirst();
        assertThat(item.editable()).isFalse();assertThat(item.phone()).isEmpty();assertThat(item.companyId()).isNull();assertThat(item.storeIds()).containsExactly(store);
        assertThatThrownBy(()->employees.save(actor,store,id,command(1,List.of(store),company,null,null))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->admins.save(actor,store,manager,new AdministratorWrite(1L,true,company))).isInstanceOf(WorkforceException.class);
    }
    @Test void companyTransferAndStoreRemovalKeepHistory(){
        UUID id=UUID.randomUUID();employees.save(admin,store,id,command(0,List.of(store,sibling),company,null,null));
        employees.save(admin,store,id,command(1,List.of(store),otherCompany,null,null));
        assertThat(jdbc.queryForObject("select count(*) from workforce_company_history where employee_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from workforce_work_stores where employee_id=? and ended_on is not null",Integer.class,id)).isEqualTo(1);
        assertThatThrownBy(()->employees.save(admin,store,id,command(1,List.of(store),otherCompany,null,null))).isInstanceOf(WorkforceException.class);
    }
    @Test void auditFailureRollsBackEmployeeAndNewAccount(){
        when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit failed"));
        UUID id=UUID.randomUUID();String user="rollback-"+UUID.randomUUID().toString().substring(0,8);
        assertThatThrownBy(()->employees.save(admin,store,id,command(0,List.of(store),company,null,user))).isInstanceOf(IllegalStateException.class);
        assertThat(repository.find(tenant,id)).isEmpty();assertThat(jdbc.queryForObject("select count(*) from auth_accounts where tenant_id=? and username=?",Integer.class,tenant,user)).isZero();
    }
    @Test void companySettingsValidateScopeAndVersion(){
        var saved=companies.save(admin,store,company,new CompanyWrite(0L,"202600001A",7,true));assertThat(saved.version()).isEqualTo(1);
        assertThatThrownBy(()->companies.save(admin,store,company,new CompanyWrite(0L,"202600001A",7,true))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->companies.save(admin,store,company,new CompanyWrite(1L,"",31,true))).isInstanceOf(WorkforceException.class);
        var limited=new CurrentActor(tenant,adminId,"staff",admin.roles(),admin.permissions(),Set.of(store));
        assertThatThrownBy(()->companies.save(limited,store,company,new CompanyWrite(1L,"",7,true))).isInstanceOf(WorkforceException.class);
    }
    @Test void disabledCompanyPreventsNewEmployeesAndDoesNotDeleteExistingOnes(){
        UUID id=UUID.randomUUID();employees.save(admin,store,id,command(0,List.of(store),company,null,null));
        companies.save(admin,store,company,new CompanyWrite(0L,"",7,false));
        assertThatThrownBy(()->employees.save(admin,store,UUID.randomUUID(),command(0,List.of(store),company,null,null))).isInstanceOf(WorkforceException.class);
        assertThat(employees.get(admin,store,id).name()).isEqualTo("Alice");
    }
    @Test void simultaneousCreationWithSameIdIsIdempotent()throws Exception{
        UUID id=UUID.randomUUID();var command=command(0,List.of(store),company,null,null);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var a=pool.submit(()->{start.await();return employees.save(admin,store,id,command);});
            var b=pool.submit(()->{start.await();return employees.save(admin,store,id,command);});
            start.countDown();assertThat(a.get(20,TimeUnit.SECONDS).id()).isEqualTo(b.get(20,TimeUnit.SECONDS).id());
            assertThat(jdbc.queryForObject("select count(*) from workforce_employees where id=?",Integer.class,id)).isEqualTo(1);
            verify(logs,times(1)).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
        }finally{pool.shutdownNow();}
    }
    @Test void foreignTenantAndStoreCannotBeReferenced(){
        UUID foreign=UUID.randomUUID(),foreignStore=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Foreign','active')",foreign,"t-"+foreign);
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'Foreign','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",foreignStore,foreign,"s-"+foreignStore);
        assertThatThrownBy(()->employees.save(admin,store,UUID.randomUUID(),command(0,List.of(store,foreignStore),company,null,null))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->jdbc.update("insert into workforce_administrators(tenant_id,store_id,account_id,version) values(?,?,?,1)",foreign,foreignStore,adminId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void unknownTenantProductHasNoAutomaticEntitlement(){
        assertThat(jdbc.queryForObject("select count(*) from tenant_app_entitlements where app_key='workforce' and tenant_id=?",Integer.class,tenant)).isZero();
    }
}
