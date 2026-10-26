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
    @Autowired CustomerSecurityEmailService securityEmail;
    @Autowired CustomerActivationService activation;
    @org.springframework.boot.test.mock.mockito.MockBean com.rpb.reservation.walkin.api.CurrentActorProvider staffActors;
    UUID staffId;
    com.rpb.reservation.walkin.api.CurrentActor staff;

    @org.springframework.boot.test.mock.mockito.MockBean com.rpb.reservation.customerauth.application.port.out.CustomerEmailDeliveryPort mail;
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
        staffId=UUID.randomUUID();
        jdbc.update("insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash) values(?,?,?,'Test issuer','staff','active',?)",staffId,tenant,staffId.toString(),new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("test-secret"));
        staff=new com.rpb.reservation.walkin.api.CurrentActor(tenant,staffId,"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store));
        org.mockito.Mockito.when(staffActors.currentActor()).thenReturn(Optional.of(staff));

    }
    MvcResult login(String phone,String password,int status) throws Exception {
        return mvc.perform(post("/api/v1/public/customer-auth/password-login").header("X-RPB-Customer-Auth","1").contentType("application/json")
            .content(json.writeValueAsString(Map.of("storeId",store,"phoneE164",phone,"password",password))))
            .andExpect(status().is(status)).andReturn();
    }
    MvcResult activatedLogin() throws Exception {
        var issued=activation.issue(storeScope(),staff,customer,true,"issue-ip");
        activation.activate(storeScope(),"+6591234567",issued.code(),"my-new-password","activation-ip");
        return login("+6591234567","my-new-password",200);
    }
    Cookie cookie(MvcResult result){return result.getResponse().getCookie(CustomerAuthCookieService.COOKIE_NAME);}
    @Test void directoryCustomerMustActivateBeforePasswordLoginAndKeepsIdentity() throws Exception {
        assertThat(wallets.search(scope,"+6591234567")).extracting(c->c.id()).containsExactly(customer);
        login("+6591234567","234567",401);
        assertThat(jdbc.queryForObject("select count(*) from customer_auth_accounts where tenant_id=?",Integer.class,tenant)).isZero();
        var signed=activatedLogin();
        assertThat(json.readTree(signed.getResponse().getContentAsString()).at("/principal/customerId").asText()).isEqualTo(customer.toString());
        assertThat(json.readTree(signed.getResponse().getContentAsString()).at("/principal/passwordChangeRequired").asBoolean()).isFalse();
        assertThat(cookie(signed).isHttpOnly()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from customers where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
    }
    @Test void passwordChangeRevokesSessionsAndOldPhoneCannotBecomePassword() throws Exception {
        var first=activatedLogin();var second=login("+6591234567","my-new-password",200);
        var changed=mvc.perform(post("/api/v1/public/customer-auth/password-change").cookie(cookie(first)).header("X-RPB-Customer-Auth","1").contentType("application/json")
            .content("{\"currentPassword\":\"my-new-password\",\"newPassword\":\"next-password\"}"))
            .andExpect(status().isOk()).andReturn();
        assertThat(sessions.authenticateSession(cookie(first).getValue())).isEmpty();assertThat(sessions.authenticateSession(cookie(second).getValue())).isEmpty();
        assertThat(sessions.authenticateSession(cookie(changed).getValue())).isPresent();
        login("+6591234567","234567",401);login("+6591234567","next-password",200);
        customers.update(scope,customer,new CustomerManagementCommand("Guest",null,"+6598888888",null));
        login("+6598888888","888888",401);login("+6598888888","next-password",200);
    }
    @Test void failedLoginLimitPersistsAndUnknownPhoneCannotRegister() throws Exception {
        for(int i=0;i<10;i++)login("+6591234567","wrong",401);
        login("+6591234567","234567",429);
        assertThat(jdbc.queryForObject("select max(attempts) from customer_password_login_limits where tenant_id=?",Integer.class,tenant)).isGreaterThanOrEqualTo(11);
        assertThat(jdbc.queryForObject("select count(*) from customer_auth_accounts where tenant_id=?",Integer.class,tenant)).isZero();
    }
    @Test void passwordChangeNeedsSessionAndOldPassword() throws Exception {
        mvc.perform(post("/api/v1/public/customer-auth/password-change").header("X-RPB-Customer-Auth","1").contentType("application/json").content("{\"currentPassword\":\"234567\",\"newPassword\":\"my-new-password\"}"))
            .andExpect(status().isUnauthorized());
        var normal=activatedLogin();String before=credentials.passwordHash(scope,customer).orElseThrow();
        mvc.perform(post("/api/v1/public/customer-auth/password-change").cookie(cookie(normal)).header("X-RPB-Customer-Auth","1").contentType("application/json").content("{\"currentPassword\":\"wrong\",\"newPassword\":\"next-password\"}"))
            .andExpect(status().isUnauthorized());assertThat(credentials.passwordHash(scope,customer)).contains(before);
    }
    @Test void archivedOrDisabledCustomersCannotLoginOrReceiveCodes() throws Exception {
        activatedLogin();jdbc.update("update customer_auth_accounts set status='disabled' where tenant_id=?",tenant);
        login("+6591234567","my-new-password",401);customers.archive(scope,customer);
        assertThat(wallets.search(scope,"Shared Guest")).isEmpty();
        assertThatThrownBy(()->activation.issue(storeScope(),staff,customer,true,"issue-ip")).isInstanceOf(CustomerAuthServiceException.class);
    }
    @Test void loginReadsExistingWalletAccountWithoutMovingStoredValue() throws Exception {
        UUID entity=UUID.randomUUID(),program=UUID.randomUUID();
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'issuer','Issuer','active')",entity,tenant);
        jdbc.update("update stores set operating_entity_id=? where id=?",entity,store);
        jdbc.update("insert into wallet_programs(id,tenant_id,operating_entity_id) values(?,?,?)",program,tenant,entity);
        jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",program,tenant,store,entity);
        var account=new TransactionTemplate(transactions).execute(tx->walletAccounts.open(storeScope(),walletAccounts.requireProgram(storeScope(),true),customer));
        var signed=activatedLogin();
        mvc.perform(get("/api/v1/public/stores/"+store+"/wallet/me").cookie(cookie(signed))).andExpect(status().isOk()).andExpect(jsonPath("$.accounts[0].id").value(account.id().toString())).andExpect(jsonPath("$.accounts[0].balanceCents").value(0));
    }
    @Test void oneIpCannotBypassLoginLimitWithDifferentPhones() throws Exception {
        for(int i=0;i<50;i++)login("+659000"+String.format("%04d",i),"wrong",401);
        login("+6591234567","234567",429);
    }
    @Test void meCannotReadAnotherStoreTenant() throws Exception {
        var signed=activatedLogin();UUID other=UUID.randomUUID(),otherStore=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",other,other.toString(),"Other");
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,'other','Other','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",otherStore,other);
        mvc.perform(get("/api/v1/public/customer-auth/me").param("storeId",otherStore.toString()).cookie(cookie(signed))).andExpect(status().isUnauthorized());
    }

    void configureEmail(UUID targetStore) {
        jdbc.update("insert into store_customer_email_settings(tenant_id,store_id,enabled,from_email,smtp_host,smtp_port) values(?,?,true,'no-reply@example.com','localhost',1025)",tenant,targetStore);
    }
    StoreScope storeScope(){return new StoreScope(new TenantId(tenant),store);}
    CustomerAuthPrincipal readyCustomer() throws Exception {
        var changed=activatedLogin();
        return sessions.authenticateSession(cookie(changed).getValue()).orElseThrow();
    }
    String capturedCode() {
        var captor=org.mockito.ArgumentCaptor.forClass(CustomerEmailDeliveryMessage.class);
        org.mockito.Mockito.verify(mail,org.mockito.Mockito.atLeastOnce()).sendLoginCode(captor.capture(),org.mockito.ArgumentMatchers.any());
        return captor.getValue().code();
    }
    void bind(CustomerAuthPrincipal principal) {
        configureEmail(store);
        securityEmail.requestBinding(storeScope(),principal,"verified@example.com","binding-ip");
        securityEmail.confirmBinding(storeScope(),principal,"verified@example.com",capturedCode(),"binding-ip");
    }
    @Test void verifiedRecoveryResetsPasswordRevokesSessionsAndRejectsReplay() throws Exception {
        var principal=readyCustomer();bind(principal);
        var old=login("+6591234567","my-new-password",200);
        securityEmail.requestReset(storeScope(),"+6591234567","verified@example.com","request-ip");String code=capturedCode();
        assertThat(jdbc.queryForObject("select code_hash from customer_security_email_codes where tenant_id=? and purpose='reset_password'",String.class,tenant)).hasSize(64).isNotEqualTo(code);
        securityEmail.reset(storeScope(),"+6591234567","verified@example.com",code,"recovered-password","confirm-ip");
        assertThat(sessions.authenticateSession(cookie(old).getValue())).isEmpty();
        login("+6591234567","my-new-password",401);login("+6591234567","recovered-password",200);login("+6591234567","234567",401);
        assertThatThrownBy(()->securityEmail.reset(storeScope(),"+6591234567","verified@example.com",code,"another-password","replay-ip"))
            .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.CODE_MISMATCH);
        assertThat(jdbc.queryForObject("select password_change_required from customer_password_credentials where tenant_id=? and customer_id=?",Boolean.class,tenant,customer)).isFalse();
    }
    @Test void profileEmailAloneCannotRecoverAndAnonymousResponseDoesNotExposeAccounts() throws Exception {
        readyCustomer();configureEmail(store);
        customers.update(scope,customer,new CustomerManagementCommand("Guest",null,"+6591234567","profile-only@example.com"));
        var body=Map.of("storeId",store,"phoneE164","+6591234567","email","profile-only@example.com");
        var known=mvc.perform(post("/api/v1/public/customer-auth/password-reset-code").header("X-RPB-Customer-Auth","1").contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(content().json("{\"success\":true}")).andExpect(header().string("Cache-Control","private, no-store")).andReturn();
        var unknown=mvc.perform(post("/api/v1/public/customer-auth/password-reset-code").header("X-RPB-Customer-Auth","1").contentType("application/json").content(json.writeValueAsString(Map.of("storeId",store,"phoneE164","+6599999999","email","profile-only@example.com"))))
            .andExpect(status().isOk()).andReturn();
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        org.mockito.Mockito.verifyNoInteractions(mail);
    }
    @Test void wrongCodeAttemptsCommitAndResendingInvalidatesTheOldCode() throws Exception {
        bind(readyCustomer());securityEmail.requestReset(storeScope(),"+6591234567","verified@example.com","request-ip");String first=capturedCode();
        String wrong=first.equals("000000")?"000001":"000000";
        for(int i=0;i<5;i++)assertThatThrownBy(()->securityEmail.reset(storeScope(),"+6591234567","verified@example.com",wrong,"recovered-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        assertThat(jdbc.queryForObject("select attempts from customer_security_email_codes where tenant_id=? and purpose='reset_password' and consumed_at is null",Integer.class,tenant)).isEqualTo(5);
        assertThatThrownBy(()->securityEmail.reset(storeScope(),"+6591234567","verified@example.com",first,"recovered-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        securityEmail.requestReset(storeScope(),"+6591234567","verified@example.com","request-ip");String second=capturedCode();
        assertThat(jdbc.queryForObject("select count(*) from customer_security_email_codes where tenant_id=? and purpose='reset_password' and consumed_at is not null",Integer.class,tenant)).isEqualTo(1);
        securityEmail.reset(storeScope(),"+6591234567","verified@example.com",second,"recovered-password","confirm-ip");
    }
    @Test void expiredForeignStoreAndWrongPurposeCodesCannotReset() throws Exception {
        var principal=readyCustomer();bind(principal);
        UUID otherStore=UUID.randomUUID();jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,'second','Second','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",otherStore,tenant);
        securityEmail.requestBinding(storeScope(),principal,"verified@example.com","binding-ip");String binding=capturedCode();
        assertThatThrownBy(()->securityEmail.reset(storeScope(),"+6591234567","verified@example.com",binding,"recovered-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        securityEmail.requestReset(storeScope(),"+6591234567","verified@example.com","request-ip");String reset=capturedCode();
        assertThatThrownBy(()->securityEmail.reset(new StoreScope(new TenantId(tenant),otherStore),"+6591234567","verified@example.com",reset,"recovered-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        jdbc.update("update customer_security_email_codes set created_at=now()-interval '20 minutes',expires_at=now()-interval '1 minute' where tenant_id=? and purpose='reset_password'",tenant);
        assertThatThrownBy(()->securityEmail.reset(storeScope(),"+6591234567","verified@example.com",reset,"recovered-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
    }
    @Test void concurrentResetConsumesOneCodeOnly() throws Exception {
        bind(readyCustomer());securityEmail.requestReset(storeScope(),"+6591234567","verified@example.com","request-ip");String code=capturedCode();
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            Callable<Boolean> action=()->{start.await();try{securityEmail.reset(storeScope(),"+6591234567","verified@example.com",code,"recovered-password",UUID.randomUUID().toString());return true;}catch(CustomerAuthServiceException e){return false;}};
            var a=executor.submit(action);var b=executor.submit(action);start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }finally{executor.shutdownNow();}
    }
    @Test void securityMailEndpointsRequireSameOriginHeaderAndNormalSession() throws Exception {
        mvc.perform(post("/api/v1/public/customer-auth/password-reset-code").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/public/customer-auth/password-reset-code").header("X-RPB-Customer-Auth","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/public/customer-auth/recovery-email-code").header("X-RPB-Customer-Auth","1").contentType("application/json").content(json.writeValueAsString(Map.of("storeId",store,"email","verified@example.com"))))
            .andExpect(status().isUnauthorized());
    }
    @Test void changedPhoneDoesNotEnableTheOldBootstrapPassword() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(tx->credentials.savePassword(scope,customer,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("234567")));
        customers.update(scope,customer,new CustomerManagementCommand("Guest",null,"+6598888888",null));
        login("+6598888888","234567",401);login("+6598888888","888888",401);
    }
    @Test void recoveryCodeCannotCrossTenantOrCustomerAndForeignAccountFkIsRejected() throws Exception {
        var principal=readyCustomer();bind(principal);securityEmail.requestReset(storeScope(),"+6591234567","verified@example.com","request-ip");String code=capturedCode();
        UUID other=UUID.randomUUID(),otherStore=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",other,other.toString(),"Other tenant");
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,'other','Other','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",otherStore,other);
        var foreignCustomer=customers.create(new TenantScope(new TenantId(other)),new CustomerManagementCommand("Other",null,"+6591234567",null));
        var foreignScope=new TenantScope(new TenantId(other));
        new TransactionTemplate(transactions).executeWithoutResult(tx->{credentials.savePassword(foreignScope,foreignCustomer.id(),new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("foreign-password"));credentials.completePasswordChange(foreignScope,foreignCustomer.id());});
        passwords.login(other,"+6591234567","foreign-password","foreign-ip","test");
        jdbc.update("update customer_auth_accounts set email='verified@example.com',email_verified_at=now() where tenant_id=?",other);
        assertThatThrownBy(()->securityEmail.reset(new StoreScope(new TenantId(other),otherStore),"+6591234567","verified@example.com",code,"recovered-password","foreign-confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        assertThatThrownBy(()->jdbc.update("update customer_security_email_codes set auth_account_id=(select id from customer_auth_accounts where tenant_id=?) where tenant_id=?",other,tenant))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->securityEmail.requestBinding(storeScope(),principal,"someone-else@example.com","binding-ip")).isInstanceOf(CustomerAuthServiceException.class);
        assertThat(foreignCustomer.id()).isNotEqualTo(customer);
    }
    @Test void failedResetRateLimitSurvivesRollbackAndPersistenceFailureRollsBackPassword() throws Exception {
        bind(readyCustomer());
        for(int i=0;i<10;i++)securityEmail.requestReset(storeScope(),"+6591234567","wrong@example.com","rate-ip");
        assertThatThrownBy(()->securityEmail.requestReset(storeScope(),"+6591234567","wrong@example.com","rate-ip"))
            .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.RATE_LIMITED);
        // Use a fresh subject bucket to test atomic write failure, retaining the earlier limit evidence.
        jdbc.update("update customer_password_login_limits set window_started_at=now()-interval '16 minutes' where tenant_id=?",tenant);
        securityEmail.requestReset(storeScope(),"+6591234567","verified@example.com","request-ip");String code=capturedCode();
        String before=credentials.passwordHash(scope,customer).orElseThrow();
        jdbc.execute("create function "+SCHEMA+".deny_code_consume() returns trigger language plpgsql as $$ begin if new.consumed_at is not null and old.consumed_at is null then raise exception 'test consume persistence failure'; end if; return new; end $$");
        jdbc.execute("create trigger deny_code_consume before update on customer_security_email_codes for each row execute function "+SCHEMA+".deny_code_consume()");
        try {
            assertThatThrownBy(()->securityEmail.reset(storeScope(),"+6591234567","verified@example.com",code,"recovered-password","confirm-ip"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(credentials.passwordHash(scope,customer)).contains(before);
            assertThat(jdbc.queryForObject("select consumed_at is null from customer_security_email_codes where tenant_id=? and purpose='reset_password'",Boolean.class,tenant)).isTrue();
        } finally {jdbc.execute("drop trigger deny_code_consume on customer_security_email_codes");jdbc.execute("drop function "+SCHEMA+".deny_code_consume()");}
    }
    @Test void verifiedEmailCannotBeClaimedByAnotherCustomer() throws Exception {
        bind(readyCustomer());
        var second=customers.create(scope,new CustomerManagementCommand("Other",null,"+6591234568",null));
        var code=activation.issue(storeScope(),staff,second.id(),true,"second-issue-ip");
        activation.activate(storeScope(),"+6591234568",code.code(),"another-secret-password","second-ip");
        var principal=passwords.login(tenant,"+6591234568","another-secret-password","second-ip","test").principal();
        assertThatThrownBy(()->securityEmail.requestBinding(storeScope(),principal,"verified@example.com","second-ip"))
            .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.REQUEST_INVALID);
        assertThat(jdbc.queryForObject("select email from customer_auth_accounts where tenant_id=? and customer_id=?",String.class,tenant,second.id())).isNull();
    }
    @Test void ordinaryPasswordChangeInvalidatesAlreadyIssuedResetAndBindingCodes() throws Exception {
        var principal=readyCustomer();bind(principal);
        securityEmail.requestReset(storeScope(),"+6591234567","verified@example.com","request-ip");String code=capturedCode();
        securityEmail.requestBinding(storeScope(),principal,"verified@example.com","binding-ip");
        passwords.change(principal,"my-new-password","changed-again-password","change-ip","test");
        assertThat(jdbc.queryForObject("select count(*) from customer_security_email_codes where tenant_id=? and consumed_at is null",Integer.class,tenant)).isZero();
        assertThatThrownBy(()->securityEmail.reset(storeScope(),"+6591234567","verified@example.com",code,"recovered-password","confirm-ip"))
            .isInstanceOf(CustomerAuthServiceException.class).extracting("error").isEqualTo(CustomerAuthError.CODE_MISMATCH);
    }
    @Test void completeHttpRecoveryFlowUsesSessionIdentityAndNeverReturnsCode() throws Exception {
        var normal=activatedLogin();configureEmail(store);
        var binding=Map.of("storeId",store,"email","verified@example.com");
        mvc.perform(post("/api/v1/public/customer-auth/recovery-email-code").cookie(cookie(normal)).header("X-RPB-Customer-Auth","1").contentType("application/json").content(json.writeValueAsString(binding)))
            .andExpect(status().isOk()).andExpect(content().json("{\"success\":true}")).andExpect(jsonPath("$.devCode").doesNotExist());
        mvc.perform(post("/api/v1/public/customer-auth/recovery-email-confirm").cookie(cookie(normal)).header("X-RPB-Customer-Auth","1").contentType("application/json").content(json.writeValueAsString(Map.of("storeId",store,"email","verified@example.com","code",capturedCode()))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/public/customer-auth/password-reset-code").header("X-RPB-Customer-Auth","1").contentType("application/json").content(json.writeValueAsString(Map.of("storeId",store,"phoneE164","+6591234567","email","verified@example.com"))))
            .andExpect(status().isOk()).andExpect(content().json("{\"success\":true}"));
        mvc.perform(post("/api/v1/public/customer-auth/password-reset").header("X-RPB-Customer-Auth","1").contentType("application/json").content(json.writeValueAsString(Map.of("storeId",store,"phoneE164","+6591234567","email","verified@example.com","code",capturedCode(),"newPassword","http-recovered-password"))))
            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().doesNotExist(CustomerAuthCookieService.COOKIE_NAME));
        assertThat(sessions.authenticateSession(cookie(normal).getValue())).isEmpty();
        login("+6591234567","http-recovered-password",200);
    }
    String issueUrl(){return "/api/v1/stores/"+store+"/tenant-admin/customers/"+customer+"/activation-codes";}
    @Test void staffIssuesOnlyAfterConfirmationAndHttpActivationSetsPasswordWithoutLogin() throws Exception {
        mvc.perform(post(issueUrl()).header("X-RPB-Customer-Auth","1").contentType("application/json").content("{\"identityConfirmed\":false}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from customer_activation_codes where tenant_id=?",Integer.class,tenant)).isZero();
        var response=mvc.perform(post(issueUrl()).header("X-RPB-Customer-Auth","1").contentType("application/json").content("{\"identityConfirmed\":true}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(header().string("Referrer-Policy","no-referrer")).andReturn();
        String code=json.readTree(response.getResponse().getContentAsString()).path("code").asText();assertThat(code).matches("[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){2}");
        assertThat(jdbc.queryForObject("select code_hash from customer_activation_codes where tenant_id=?",String.class,tenant)).hasSize(64).doesNotContain(code.replace("-",""));
        assertThat(jdbc.queryForObject("select issued_by from customer_activation_codes where tenant_id=?",UUID.class,tenant)).isEqualTo(staffId);
        mvc.perform(get(issueUrl()).header("X-RPB-Customer-Auth","1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/v1/public/customer-auth/activate").header("X-RPB-Customer-Auth","1").contentType("application/json")
            .content(json.writeValueAsString(Map.of("storeId",store,"phoneE164","+6591234567","code",code,"newPassword","chosen-secret-password"))))
            .andExpect(status().isOk()).andExpect(content().json("{\"success\":true}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().doesNotExist(CustomerAuthCookieService.COOKIE_NAME));
        login("+6591234567","chosen-secret-password",200);login("+6591234567","234567",401);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where tenant_id=? and operation_code like 'customer.activation.%'",Integer.class,tenant)).isEqualTo(2);
        assertThatThrownBy(()->activation.activate(storeScope(),"+6591234567",code,"overwritten-password","ip")).isInstanceOf(CustomerAuthServiceException.class);
        assertThatThrownBy(()->activation.issue(storeScope(),staff,customer,true,"ip")).isInstanceOf(CustomerAuthServiceException.class);
    }
    @Test void activationIssueRequiresStaffScopePermissionAndSameOriginHeader() throws Exception {
        mvc.perform(post(issueUrl()).contentType("application/json").content("{\"identityConfirmed\":true}")).andExpect(status().isForbidden());
        mvc.perform(post(issueUrl()).header("X-RPB-Customer-Auth","1").header("Sec-Fetch-Site","same-site").contentType("application/json").content("{\"identityConfirmed\":true}")).andExpect(status().isForbidden());
        org.mockito.Mockito.when(staffActors.currentActor()).thenReturn(Optional.empty());
        mvc.perform(post(issueUrl()).header("X-RPB-Customer-Auth","1").contentType("application/json").content("{\"identityConfirmed\":true}")).andExpect(status().isUnauthorized());
        for(var invalid:List.of(
            new com.rpb.reservation.walkin.api.CurrentActor(tenant,staffId,"staff",Set.of("store_staff"),Set.of("tenant.admin.manage"),Set.of(store)),
            new com.rpb.reservation.walkin.api.CurrentActor(tenant,staffId,"staff",Set.of("tenant_admin"),Set.of(),Set.of(store)),
            new com.rpb.reservation.walkin.api.CurrentActor(tenant,staffId,"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of()),
            new com.rpb.reservation.walkin.api.CurrentActor(UUID.randomUUID(),staffId,"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store)))){
            org.mockito.Mockito.when(staffActors.currentActor()).thenReturn(Optional.of(invalid));
            mvc.perform(post(issueUrl()).header("X-RPB-Customer-Auth","1").contentType("application/json").content("{\"identityConfirmed\":true}")).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select count(*) from customer_activation_codes where tenant_id=?",Integer.class,tenant)).isZero();
    }
    @Test void verifiedEmailUsesRecoveryInsteadOfStaffActivation(){
        jdbc.update("insert into customer_auth_accounts(id,tenant_id,customer_id,email,email_verified_at,status) values(?,?,?,'verified@example.com',now(),'active')",UUID.randomUUID(),tenant,customer);
        assertThatThrownBy(()->activation.issue(storeScope(),staff,customer,true,"ip")).isInstanceOf(CustomerAuthServiceException.class);
    }
    @Test void activationWrongAttemptsPersistAndReissueRevokesPreviousCode(){
        var first=activation.issue(storeScope(),staff,customer,true,"ip");
        String wrong=(first.code().startsWith("A")?"B":"A")+first.code().substring(1);
        for(int i=0;i<5;i++){String attempt=i==0?"A".repeat(33):wrong;assertThatThrownBy(()->activation.activate(storeScope(),"+6591234567",attempt,"chosen-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);}
        assertThat(jdbc.queryForObject("select attempts from customer_activation_codes where tenant_id=?",Integer.class,tenant)).isEqualTo(5);
        assertThatThrownBy(()->activation.activate(storeScope(),"+6591234567",first.code(),"chosen-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        var next=activation.issue(storeScope(),staff,customer,true,"ip");
        assertThatThrownBy(()->activation.activate(storeScope(),"+6591234567",first.code(),"chosen-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        activation.activate(storeScope(),"+6591234567",next.code(),"chosen-password","confirm-ip");
        assertThat(credentials.passwordChangeRequired(scope,customer)).isFalse();
    }
    @Test void activationExpiredCrossStoreCrossCustomerAndCrossTenantCodesAreRejected(){
        var issued=activation.issue(storeScope(),staff,customer,true,"ip");UUID secondStore=UUID.randomUUID();
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,'second','Second','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",secondStore,tenant);
        customers.create(scope,new CustomerManagementCommand("Second",null,"+6591234568",null));
        assertThatThrownBy(()->activation.activate(new StoreScope(new TenantId(tenant),secondStore),"+6591234567",issued.code(),"chosen-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        assertThatThrownBy(()->activation.activate(storeScope(),"+6591234568",issued.code(),"chosen-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        assertThatThrownBy(()->activation.activate(new StoreScope(new TenantId(UUID.randomUUID()),store),"+6591234567",issued.code(),"chosen-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        jdbc.update("update customer_activation_codes set created_at=now()-interval '20 minutes',expires_at=now()-interval '1 minute' where tenant_id=?",tenant);
        assertThatThrownBy(()->activation.activate(storeScope(),"+6591234567",issued.code(),"chosen-password","confirm-ip")).isInstanceOf(CustomerAuthServiceException.class);
        assertThat(credentials.passwordHash(scope,customer)).isEmpty();
    }
    @Test void concurrentActivationConsumesOneCodeAndCreatesOneAccount() throws Exception {
        var code=activation.issue(storeScope(),staff,customer,true,"ip").code();var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{Callable<Boolean> action=()->{start.await();try{activation.activate(storeScope(),"+6591234567",code,"chosen-password",UUID.randomUUID().toString());return true;}catch(CustomerAuthServiceException e){return false;}};
            var a=executor.submit(action);var b=executor.submit(action);start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("select count(*) from customer_auth_accounts where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
        }finally{executor.shutdownNow();}
    }
    @Test void activationAuditFailureRollsBackCredentialsAccountAndCodeConsumption(){
        var issued=activation.issue(storeScope(),staff,customer,true,"ip");
        jdbc.execute("create function "+SCHEMA+".deny_activation_audit() returns trigger language plpgsql as $$ begin if new.operation_code='customer.activation.completed' then raise exception 'test audit failure'; end if; return new; end $$");
        jdbc.execute("create trigger deny_activation_audit before insert on audit_logs for each row execute function "+SCHEMA+".deny_activation_audit()");
        try{assertThatThrownBy(()->activation.activate(storeScope(),"+6591234567",issued.code(),"chosen-password","confirm-ip")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(credentials.passwordHash(scope,customer)).isEmpty();assertThat(jdbc.queryForObject("select count(*) from customer_auth_accounts where tenant_id=?",Integer.class,tenant)).isZero();
            assertThat(jdbc.queryForObject("select consumed_at is null from customer_activation_codes where tenant_id=?",Boolean.class,tenant)).isTrue();
        }finally{jdbc.execute("drop trigger deny_activation_audit on audit_logs");jdbc.execute("drop function "+SCHEMA+".deny_activation_audit()");}
    }
}
