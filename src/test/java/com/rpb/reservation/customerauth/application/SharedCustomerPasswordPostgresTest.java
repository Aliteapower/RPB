package com.rpb.reservation.customerauth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customerauth.api.CustomerAuthCookieService;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.wallet.integration.SharedWalletDirectory;
import com.rpb.reservation.wallet.persistence.WalletAccountRepository;
import com.rpb.reservation.customerauth.application.port.out.CustomerPasswordRepositoryPort;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses only the owning workspace pointer PostgreSQL, with a fresh schema. */
@SpringBootTest(properties={"spring.flyway.enabled=false","rpb.local-auth.enabled=false","spring.http.client.factory=simple"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SharedCustomerPasswordPostgresTest {
    static final String SCHEMA="shared_customer_test_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate root;
    static final String URL=start();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CustomerManagementApplicationService customers;
    @Autowired CustomerDirectoryQuery directory;
    @Autowired SharedWalletDirectory wallets;
    @Autowired CustomerPasswordAuthService passwords;
    @Autowired CustomerAuthApplicationService sessions;
    @Autowired CustomerPasswordRepositoryPort credentials;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WalletAccountRepository walletAccounts;
    UUID tenant,store,customer;
    TenantScope scope;
    static String start() {
        try {
            String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream()
                .filter(l->l.startsWith("port=")).findFirst().orElseThrow().substring(5);
            String base="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
            root=new JdbcTemplate(new DriverManagerDataSource(base,"postgres",""));root.execute("create schema "+SCHEMA);
            String url=base+"&currentSchema="+SCHEMA;
            var db=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));
            try(var files=Files.list(Path.of("src/main/resources/db/migration"))) {
                for(var file:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())
                    db.execute(Files.readString(file));
            }
            return url;
        } catch(Exception e) { if(root!=null) root.execute("drop schema if exists "+SCHEMA+" cascade");throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->URL);r.add("spring.datasource.username",()->"postgres");r.add("spring.datasource.password",()->"");
        r.add("spring.datasource.hikari.schema",()->SCHEMA);
    }
    @AfterAll static void close(){root.execute("drop schema if exists "+SCHEMA+" cascade");}
    @BeforeEach void fixture() {
        tenant=UUID.randomUUID();store=UUID.randomUUID();scope=new TenantScope(new TenantId(tenant));
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"Shared customer test");
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,'main','Store','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant);
        customer=customers.create(scope,new CustomerManagementCommand("Shared Guest",null,"+6591234567",null)).id();
    }
    MvcResult login(String phone,String password,int status) throws Exception {
        return mvc.perform(post("/api/v1/public/customer-auth/password-login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("storeId",store,"phoneE164",phone,"password",password))))
            .andExpect(status().is(status)).andReturn();
    }
    Cookie cookie(MvcResult result){return result.getResponse().getCookie(CustomerAuthCookieService.COOKIE_NAME);}
    @Test void existingDirectoryCustomerWithoutLoginIsAvailableToWalletAndPasswordLoginKeepsSameId() throws Exception {
        assertThat(wallets.search(scope,"+6591234567")).extracting(c->c.id()).containsExactly(customer);
        assertThat(wallets.customer(scope,customer).phone()).isEqualTo("+6591234567");
        assertThat(jdbc.queryForObject("select count(*) from customer_auth_accounts where tenant_id=?",Integer.class,tenant)).isZero();
        var result=login("+6591234567","234567",200);
        assertThat(json.readTree(result.getResponse().getContentAsString()).at("/principal/customerId").asText()).isEqualTo(customer.toString());
        assertThat(json.readTree(result.getResponse().getContentAsString()).at("/principal/phoneE164").asText()).isEqualTo("+6591234567");
        assertThat(cookie(result).isHttpOnly()).isTrue();
        login("+6591234567","234567",200);
        assertThat(jdbc.queryForObject("select count(*) from customers where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from customer_auth_accounts where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select password_hash from customer_password_credentials where tenant_id=?",String.class,tenant)).startsWith("$2");
    }
    @Test void passwordChangeRevokesOldSessionsAndDefaultPasswordCannotReturnEvenAfterPhoneChange() throws Exception {
        var first=login("+6591234567","234567",200);var second=login("+6591234567","234567",200);
        var changed=mvc.perform(post("/api/v1/public/customer-auth/password-change").cookie(cookie(first)).contentType("application/json")
            .content("{\"currentPassword\":\"234567\",\"newPassword\":\"my-new-password\"}"))
            .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/v1/public/customer-auth/me").cookie(cookie(first))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/customer-auth/me").cookie(cookie(second))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/customer-auth/me").cookie(cookie(changed))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where tenant_id=? and target_id=? and operation_code='customer.password.set'",Integer.class,tenant,customer)).isEqualTo(2);
        login("+6591234567","234567",401);login("+6591234567","my-new-password",200);
        customers.update(scope,customer,new CustomerManagementCommand("Guest",null,"+6598888888",null));
        login("+6591234567","my-new-password",401);login("+6598888888","888888",401);login("+6598888888","my-new-password",200);
    }
    @Test void failuresPersistAndUnknownPhoneCannotRegisterAndOtherTenantCannotRead() throws Exception {
        login("+6590000000","000000",401);
        assertThat(jdbc.queryForObject("select count(*) from customers where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
        for(int i=0;i<10;i++) login("+6591234567","wrong",401);
        login("+6591234567","234567",429);
        assertThat(jdbc.queryForObject("select max(attempts) from customer_password_login_limits where tenant_id=?",Integer.class,tenant)).isGreaterThanOrEqualTo(11);
        var foreign=new TenantScope(new TenantId(UUID.randomUUID()));
        assertThat(directory.active(foreign,customer)).isEmpty();assertThat(wallets.search(foreign,"Shared Guest")).isEmpty();
    }
    @Test void passwordChangeWithoutSessionFailsAndWrongOldPasswordDoesNotChangeHash() throws Exception {
        mvc.perform(post("/api/v1/public/customer-auth/password-change").contentType("application/json")
            .content("{\"currentPassword\":\"234567\",\"newPassword\":\"my-new-password\"}"))
            .andExpect(status().isUnauthorized());
        var signed=login("+6591234567","234567",200);
        String before=credentials.passwordHash(scope,customer).orElseThrow();
        mvc.perform(post("/api/v1/public/customer-auth/password-change").cookie(cookie(signed)).contentType("application/json")
            .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"my-new-password\"}"))
            .andExpect(status().isUnauthorized());
        assertThat(credentials.passwordHash(scope,customer)).contains(before);
    }
    @Test void archivedOrDisabledCustomersCannotLoginAndAreNotWalletCandidates() throws Exception {
        login("+6591234567","234567",200);
        jdbc.update("update customer_auth_accounts set status='disabled' where tenant_id=?",tenant);
        login("+6591234567","234567",401);
        customers.archive(scope,customer);
        assertThat(wallets.search(scope,"+6591234567")).isEmpty();login("+6591234567","234567",401);
    }
    @Test void concurrentInitialLoginsCreateOneCredentialAndAccount() throws Exception {
        var executor=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            Callable<UUID> work=()->{start.await();return passwords.login(tenant,"+6591234567","234567",UUID.randomUUID().toString(),"test").principal().customerId();};
            var a=executor.submit(work);var b=executor.submit(work);start.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(customer);assertThat(b.get(15,TimeUnit.SECONDS)).isEqualTo(customer);
            assertThat(jdbc.queryForObject("select count(*) from customer_auth_accounts where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from customer_password_credentials where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
        } finally {executor.shutdownNow();}
    }

    @Test void samePhoneInDifferentTenantsHasIndependentCustomerAndPassword() {
        UUID other=UUID.randomUUID();var otherScope=new TenantScope(new TenantId(other));
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",other,other.toString(),"Other tenant");
        var otherCustomer=customers.create(otherScope,new CustomerManagementCommand("Other Guest",null,"+6591234567",null));
        var a=passwords.login(tenant,"+6591234567","234567","ip","test");
        passwords.change(a.principal(),"234567","tenant-one-password","ip","test");
        var b=passwords.login(other,"+6591234567","234567","ip","test");
        assertThat(b.principal().customerId()).isEqualTo(otherCustomer.id()).isNotEqualTo(customer);
        assertThat(passwords.login(tenant,"+6591234567","tenant-one-password","ip","test").principal().customerId()).isEqualTo(customer);
    }

    @Test void loginReadsOriginalWalletAccountAndNeverCreatesOrMovesStoredValue() throws Exception {
        UUID entity=UUID.randomUUID(),program=UUID.randomUUID();
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'issuer','Issuer','active')",entity,tenant);
        jdbc.update("update stores set operating_entity_id=? where id=?",entity,store);
        jdbc.update("insert into wallet_programs(id,tenant_id,operating_entity_id) values(?,?,?)",program,tenant,entity);
        jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",program,tenant,store,entity);
        var storeScope=new StoreScope(new TenantId(tenant),store);
        var account=new TransactionTemplate(transactions).execute(s->walletAccounts.open(storeScope,walletAccounts.requireProgram(storeScope,true),customer));
        var signed=login("+6591234567","234567",200);
        mvc.perform(get("/api/v1/public/stores/"+store+"/wallet/me").cookie(cookie(signed)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accounts[0].id").value(account.id().toString()))
            .andExpect(jsonPath("$.accounts[0].customerId").value(customer.toString()))
            .andExpect(jsonPath("$.accounts[0].balanceCents").value(0));
        assertThat(jdbc.queryForObject("select count(*) from wallet_accounts where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
    }

    @Test void oneIpCannotBypassLoginLimitByChangingPhoneNumbers() throws Exception {
        for(int i=0;i<50;i++) login("+659000"+String.format("%04d",i),"wrong",401);
        login("+6591234567","234567",429);
        assertThat(jdbc.queryForObject("select count(*) from customer_auth_accounts where tenant_id=?",Integer.class,tenant)).isZero();
    }

    @Test void meCannotDisplayCustomerFromAnotherStoreTenant() throws Exception {
        var signed=login("+6591234567","234567",200);
        mvc.perform(get("/api/v1/public/customer-auth/me").param("storeId",store.toString()).cookie(cookie(signed)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.principal.phoneE164").value("+6591234567"));
        var other=UUID.randomUUID();var otherStore=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",other,other.toString(),"Other tenant");
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,'other','Other','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",otherStore,other);
        mvc.perform(get("/api/v1/public/customer-auth/me").param("storeId",otherStore.toString()).cookie(cookie(signed)))
            .andExpect(status().isUnauthorized());
    }
}
