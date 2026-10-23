package com.rpb.reservation.wallet.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static com.rpb.reservation.wallet.marketing.MarketingDtos.*;

/** Actual runtime-pointer PostgreSQL in an owned random schema; no shared business rows. */
class WalletMarketingPostgresTest {
 static final String SCHEMA="wallet_marketing_test_"+UUID.randomUUID().toString().replace("-","");
 static JdbcTemplate jdbc,root;static TransactionTemplate tx;
 ObjectMapper json=new ObjectMapper().findAndRegisterModules();UUID tenant,issuer,store,customer,actorId,program;StoreScope scope;CurrentActor actor;CustomerAuthPrincipal principal;
 WalletAccountRepository accounts;WalletTopupRepository topups;WalletAccess access;WalletCommandRepository commands;MarketingRepository repository;WalletMarketingService marketing;WalletMarketingConfigurationService config;WalletCustomerTopupService self;WalletCollectionPort port;AppGateService gate;
 @BeforeAll static void database()throws Exception{String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(l->l.startsWith("port=")).findFirst().orElseThrow().substring(5);String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+SCHEMA);var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA+",public","postgres","");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var f:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())jdbc.execute(Files.readString(f));}}
 @AfterAll static void close(){if(root!=null)root.execute("drop schema if exists "+SCHEMA+" cascade");}
 @BeforeEach void setup(){tenant=UUID.randomUUID();issuer=UUID.randomUUID();store=UUID.randomUUID();customer=UUID.randomUUID();actorId=UUID.randomUUID();jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"marketing");jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'issuer','issuer','active')",issuer,tenant);jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'main','main','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,issuer);jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'customer','regular','customer','+6591234567','active')",customer,tenant);scope=new StoreScope(new TenantId(tenant),store);actor=new CurrentActor(tenant,actorId,"tenant_admin",Set.of("tenant_admin"),Set.of("wallet.marketing.view","wallet.marketing.manage","wallet.topup.create"),Set.of(store));principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"test@example.com","customer");gate=mock(AppGateService.class);when(gate.evaluate(any())).thenReturn(AppGateDecision.allow("wallet",tenant,store,"wallet.topup.create"));when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.allow("wallet",tenant,store,null));port=mock(WalletCollectionPort.class);when(port.paynowConfigured(any())).thenReturn(true);accounts=new WalletAccountRepository(jdbc,json);topups=new WalletTopupRepository(jdbc);commands=new WalletCommandRepository(jdbc,json);access=new WalletAccess(gate,mock(WalletDirectory.class));repository=new MarketingRepository(jdbc,json);marketing=new WalletMarketingService(repository,accounts,access,port);config=new WalletMarketingConfigurationService(repository,accounts,access,commands);self=new WalletCustomerTopupService(marketing,repository,access,accounts,topups,commands,port);program=tx.execute(s->accounts.configure(scope,issuer,List.of("cash","paynow"),null)).id();}
 @Test void paynowRemainsVisibleButQuotesAndNewOrdersRequireValidCollectionProfile(){
  enable();when(port.paynowConfigured(any())).thenReturn(false);
  var live=tx.execute(s->marketing.customerOffers(scope,principal));assertThat(live.channels()).contains("cash","paynow");var o=live.offers().getFirst();
  assertThatThrownBy(()->tx.execute(s->marketing.customerQuote(scope,principal,new CustomerQuote(o.id(),o.version(),"paynow")))).isInstanceOf(WalletException.class);
  when(port.paynowConfigured(any())).thenReturn(true);
  var q=tx.execute(s->marketing.customerQuote(scope,principal,new CustomerQuote(o.id(),o.version(),"paynow")));assertThat(q.channel()).isEqualTo("paynow");
  when(port.paynowConfigured(any())).thenReturn(false);
  assertThatThrownBy(()->tx.execute(s->self.apply(scope,principal,new Apply(q.quoteId(),key()),"paynow-test"))).isInstanceOf(WalletException.class);
  assertThat(jdbc.queryForObject("select count(*) from wallet_topups where tenant_id=?",Integer.class,tenant)).isZero();
  when(port.paynowConfigured(any())).thenReturn(true);
  var topup=tx.execute(s->self.apply(scope,principal,new Apply(q.quoteId(),key()),"paynow-test"));assertThat(topup.channel()).isEqualTo("paynow");assertThat(topup.status()).isEqualTo("pending_collection");
  when(port.paynowQr(any(),eq(topup.amountCents()),eq(topup.id()))).thenReturn("paynow-principal-qr");assertThat(self.paynow(scope,principal,topup.id()).get("payload")).isEqualTo("paynow-principal-qr");
  tx.execute(s->accounts.configure(scope,issuer,List.of("cash"),accounts.requireProgram(scope,false).version()));assertThat(tx.execute(s->marketing.offers(scope,actor)).channels()).containsExactly("cash");
 }
 String key(){return UUID.randomUUID().toString();}
 Settings enable(){var initial=tx.execute(s->config.settings(scope,actor));return tx.execute(s->config.save(scope,actor,new SettingsUpdate(initial.version(),List.of(new OfferInput("100","0"),new OfferInput("300","0")),"proportional",true,true,true,key())));}
 Campaign draft(String name,int priority){return tx.execute(s->config.create(scope,actor,new CampaignDraft(name,Instant.now().minusSeconds(60),Instant.now().plusSeconds(3600),priority,List.of(new OfferInput("100","10")),"terms",0,key())));}
 Campaign publish(Campaign c){return tx.execute(s->config.publish(scope,actor,c.id(),new Publish(c.draftVersion(),c.version(),key())));}
 Quote quote(){var offers=tx.execute(s->marketing.customerOffers(scope,principal));var o=offers.offers().getFirst();return tx.execute(s->marketing.customerQuote(scope,principal,new CustomerQuote(o.id(),o.version(),"cash")));}
 @Test void initializesEditableBaseAndDraftOnlyAndCanReplaySettings(){var initial=tx.execute(s->config.settings(scope,actor));assertThat(initial.baseOffers()).extracting(Offer::principalCents).containsExactly(10000L,30000L,50000L,100000L);assertThat(initial.baseOffers()).allMatch(o->o.bonusCents()==0);assertThat(tx.<List<Campaign>>execute(s->config.campaigns(scope,actor,"all",50,null))).singleElement().extracting(Campaign::status).isEqualTo("draft");assertThat(tx.execute(s->marketing.offers(scope,actor)).campaignId()).isNull();var command=new SettingsUpdate(initial.version(),List.of(new OfferInput("20","0")),"principal_first",true,true,true,key());var saved=tx.execute(s->config.save(scope,actor,command));assertThat(tx.<Settings>execute(s->config.save(scope,actor,command))).isEqualTo(saved);assertThat(tx.<Boolean>execute(s->marketing.pointsEnabled(scope))).isTrue();}
 @Test void selectingHighestPriorityAndEqualPriorityOverlapRejected(){enable();var first=publish(draft("normal",1));assertThat(tx.execute(s->marketing.offers(scope,actor)).campaignId()).isEqualTo(first.id());var conflict=draft("conflict",1);assertThatThrownBy(()->publish(conflict)).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_CAMPAIGN_CONFLICT");var higher=publish(draft("higher",2));assertThat(tx.execute(s->marketing.offers(scope,actor)).campaignId()).isEqualTo(higher.id());}
 @Test void concurrentPublishingIsSerializedUnderPlanLock()throws Exception{enable();var a=draft("a",1);var b=draft("b",1);var pool=Executors.newFixedThreadPool(2);try{var start=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();for(var c:List.of(a,b))futures.add(pool.submit(()->{start.await();try{publish(c);return true;}catch(WalletException e){return false;}}));start.countDown();assertThat(futures.get(0).get()).isNotEqualTo(futures.get(1).get());}finally{pool.shutdownNow();}}
 @Test void configurationRequiresAllPlanStoresAndViewDoesNotGrantManage(){enable();UUID other=UUID.randomUUID();jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'other','other','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",other,tenant,issuer);jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",program,tenant,other,issuer);assertThatThrownBy(()->draft("forbidden",0)).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).status()).isEqualTo(403);var cashier=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("wallet.topup.create"),Set.of(store));assertThat(tx.execute(s->marketing.offers(scope,cashier)).offers()).isNotEmpty();assertThatThrownBy(()->config.campaigns(scope,cashier,"all",50,null)).isInstanceOf(WalletException.class);}
 @Test void quoteExpiresAndDisabledCampaignInvalidatesNewOrdersButNotFrozenOnes(){enable();var campaign=publish(draft("campaign",0));var expired=quote();jdbc.update("update wallet_topup_quotes set expires_at=now()-interval '1 second' where id=?",expired.quoteId());assertThatThrownBy(()->tx.execute(s->self.apply(scope,principal,new Apply(expired.quoteId(),key()),"ip"))).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_TOPUP_QUOTE_EXPIRED");var q=quote();var applied=tx.execute(s->self.apply(scope,principal,new Apply(q.quoteId(),key()),"ip"));assertThat(jdbc.queryForObject("select bonus_cents from wallet_topups where id=?",Long.class,applied.id())).isEqualTo(1000);tx.execute(s->config.lifecycle(scope,actor,campaign.id(),new Lifecycle(campaign.version(),"stop",key()),false));assertThat(jdbc.queryForObject("select bonus_cents from wallet_topups where id=?",Long.class,applied.id())).isEqualTo(1000);assertThat(accounts.customerAccounts(scope,customer).getFirst().balanceCents()).isZero();assertThat(jdbc.queryForObject("select count(*) from wallet_entries where tenant_id=?",Integer.class,tenant)).isZero();}
 @Test void customerApplyReplayPendingConflictDeclarationCancelAndOwnership(){enable();var q=quote();var c=new Apply(q.quoteId(),key());var first=tx.execute(s->self.apply(scope,principal,c,"ip"));assertThat(tx.<WalletViews.Topup>execute(s->self.apply(scope,principal,c,"ip"))).isEqualTo(first);assertThat(tx.<WalletViews.Topup>execute(s->self.apply(scope,principal,new Apply(q.quoteId(),key()),"ip"))).isEqualTo(first);var another=quote();assertThatThrownBy(()->tx.execute(s->self.apply(scope,principal,new Apply(another.quoteId(),key()),"ip"))).isInstanceOf(WalletCustomerTopupService.PendingTopupException.class);var action=new CustomerAction("paid",null,key());tx.execute(s->self.declare(scope,principal,first.id(),action));var declared=jdbc.queryForObject("select customer_declared_paid_at from wallet_topups where id=?",java.sql.Timestamp.class,first.id());tx.execute(s->self.declare(scope,principal,first.id(),new CustomerAction("again",null,key())));assertThat(jdbc.queryForObject("select customer_declared_paid_at from wallet_topups where id=?",java.sql.Timestamp.class,first.id())).isEqualTo(declared);assertThat(accounts.customerAccounts(scope,customer).getFirst().balanceCents()).isZero();assertThat(tx.execute(s->self.cancel(scope,principal,first.id(),new CustomerAction(null,"cancel",key()))).status()).isEqualTo("cancelled");var foreign=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"x@example.com","x");assertThatThrownBy(()->self.get(scope,foreign,first.id())).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).status()).isEqualTo(404);}
 @Test void simultaneousDifferentApplyKeysCreateOnlyOnePendingRequest()throws Exception{enable();var q1=quote();var q2=quote();var pool=Executors.newFixedThreadPool(2);try{var start=new CountDownLatch(1);List<Future<Boolean>> results=new ArrayList<>();for(var q:List.of(q1,q2))results.add(pool.submit(()->{start.await();try{tx.execute(s->self.apply(scope,principal,new Apply(q.quoteId(),key()),"ip"));return true;}catch(WalletException e){return false;}}));start.countDown();assertThat(results.get(0).get()).isNotEqualTo(results.get(1).get());assertThat(jdbc.queryForObject("select count(*) from wallet_topups where tenant_id=? and origin='customer'",Integer.class,tenant)).isEqualTo(1);}finally{pool.shutdownNow();}}
 @Test void commercialDenialPreventsQuotesAndApplicationsButHistoryRemainsReadable(){enable();var q=quote();var applied=tx.execute(s->self.apply(scope,principal,new Apply(q.quoteId(),key()),"ip"));when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.STORE_APP_EXPIRED));assertThatThrownBy(()->marketing.customerOffers(scope,principal)).isInstanceOf(WalletException.class);assertThatThrownBy(()->self.apply(scope,principal,new Apply(q.quoteId(),key()),"ip")).isInstanceOf(WalletException.class);assertThat(self.get(scope,principal,applied.id()).id()).isEqualTo(applied.id());}
 @Test void strictDtoRejectsClientAmountsQuantityArraysAndForeignIdentity()throws Exception{
  var reader=new MarketingRequestReader(json);
  for(String payload:List.of("{\"offerId\":null,\"offerVersion\":1,\"channel\":\"cash\",\"quantity\":2}","{\"offers\":[]}","{\"accountId\":\""+UUID.randomUUID()+"\"}","{\"bonusCents\":100}","{\"customerId\":\""+customer+"\"}")){
   var node=json.readTree(payload);assertThatThrownBy(()->reader.read(node,CustomerQuote.class)).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).status()).isEqualTo(400);
  }
  record InvalidInput(String payload,Class<?> type){}
  for(var input:List.of(
   new InvalidInput("{\"baseOffers\":[{\"amount\":100,\"bonus\":\"0\"}]}",SettingsUpdate.class),
   new InvalidInput("{\"offers\":[{\"amount\":\"100\",\"bonus\":10}]}",CampaignDraft.class),
   new InvalidInput("{\"offers\":[{\"amount\":null}]}",CampaignDraft.class),
   new InvalidInput("{\"expectedVersion\":\"1\"}",SettingsUpdate.class),
   new InvalidInput("{\"expectedVersion\":1.5}",Lifecycle.class),
   new InvalidInput("{\"draftVersion\":\"2\"}",Publish.class),
   new InvalidInput("{\"offerVersion\":1.0}",CustomerQuote.class),
   new InvalidInput("{\"offerVersion\":\"1\"}",CustomerQuote.class),
   new InvalidInput("{\"priority\":\"5\"}",CampaignDraft.class),
   new InvalidInput("{\"priority\":5.1}",CampaignDraft.class),
   new InvalidInput("{\"priority\":2147483648}",CampaignDraft.class),
   new InvalidInput("{\"selfServiceEnabled\":\"true\"}",SettingsUpdate.class),
   new InvalidInput("{\"selfTopupEnabled\":1}",SettingsUpdate.class),
   new InvalidInput("{\"campaignsEnabled\":\"false\"}",SettingsUpdate.class),
   new InvalidInput("{\"pointsEnabled\":null}",SettingsUpdate.class))){
   var node=json.readTree(input.payload());assertThatThrownBy(()->reader.read(node,input.type())).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).status()).isEqualTo(400);
  }
  SettingsUpdate valid=reader.read(json.readTree("{\"expectedVersion\":0,\"baseOffers\":[{\"amount\":\"100.00\",\"bonus\":\"0.00\"}],\"deductionPolicy\":\"proportional\",\"selfServiceEnabled\":true,\"campaignsEnabled\":true,\"pointsEnabled\":false,\"idempotencyKey\":\"key\"}"),SettingsUpdate.class);
  assertThat(valid.offers().getFirst().amount()).isEqualTo("100.00");assertThat(valid.offers().getFirst().bonus()).isEqualTo("0.00");assertThat(valid.selfTopupEnabled()).isTrue();
  SettingsUpdate optional=reader.read(json.readTree("{\"expectedVersion\":0,\"baseOffers\":[{\"amount\":\"100.00\"},{\"amount\":\"300.00\",\"bonus\":null}],\"deductionPolicy\":\"proportional\",\"selfServiceEnabled\":true,\"campaignsEnabled\":true,\"pointsEnabled\":false,\"idempotencyKey\":\"optional-bonus\"}"),SettingsUpdate.class);
  assertThat(optional.offers()).allMatch(o->o.bonus()==null);assertThat(MarketingRepository.bonus(optional.offers().getFirst().bonus())).isZero();
  CampaignDraft validCampaignDraft=reader.read(json.readTree("{\"name\":\"活动草稿\",\"startsAt\":\"2026-11-01T00:00:00Z\",\"endsAt\":\"2026-12-01T00:00:00Z\",\"priority\":0,\"offers\":[{\"amount\":\"100.00\",\"bonus\":\"10.00\"}],\"terms\":\"说明\",\"expectedVersion\":0,\"idempotencyKey\":\"new-campaign\"}"),CampaignDraft.class);
  assertThat(validCampaignDraft.expectedVersion()).isZero();assertThat(validCampaignDraft.priority()).isZero();assertThat(validCampaignDraft.startAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));assertThat(validCampaignDraft.offers().getFirst().bonus()).isEqualTo("10.00");
 }


 @Test void staffOrdinaryAmountRequiresNoCampaignAndQuotesBindEmployeeAccountAndStore(){enable();var account=tx.execute(s->accounts.open(scope,accounts.requireProgram(scope,true),customer));var ordinary=tx.execute(s->marketing.prepare(scope,actor,new WalletCommands.TopupCreate(account.id(),"12","cash",key())));assertThat(ordinary.bonusCents()).isZero();publish(draft("active",0));assertThatThrownBy(()->tx.execute(s->marketing.prepare(scope,actor,new WalletCommands.TopupCreate(account.id(),"12","cash",key())))).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_TOPUP_OFFER_REQUIRED");var offers=tx.execute(s->marketing.offers(scope,actor));var o=offers.offers().getFirst();var q=tx.execute(s->marketing.quote(scope,actor,new QuoteRequest(account.id(),o.id(),o.version(),"cash")));var another=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("wallet.topup.create"),Set.of(store));assertThatThrownBy(()->tx.execute(s->marketing.prepare(scope,another,new WalletCommands.TopupCreate(account.id(),null,null,key(),q.quoteId())))).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).status()).isEqualTo(404);var p=tx.execute(s->marketing.prepare(scope,actor,new WalletCommands.TopupCreate(account.id(),null,null,key(),q.quoteId())));assertThat(p.principalCents()).isEqualTo(10000);assertThat(p.bonusCents()).isEqualTo(1000);}
 @Test void settingsChangeInvalidatesQuoteAndTenantScopeNeverRecoversForeignOrder(){enable();var q=quote();var settings=tx.execute(s->config.settings(scope,actor));tx.execute(s->config.save(scope,actor,new SettingsUpdate(settings.version(),List.of(new OfferInput("100","0")),"principal_first",true,true,true,key())));assertThatThrownBy(()->tx.execute(s->self.apply(scope,principal,new Apply(q.quoteId(),key()),"ip"))).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_OFFER_CHANGED");var current=quote();var applied=tx.execute(s->self.apply(scope,principal,new Apply(current.quoteId(),key()),"ip"));var other=new StoreScope(new TenantId(UUID.randomUUID()),store);assertThatThrownBy(()->self.get(other,principal,applied.id())).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).status()).isEqualTo(403);}
 @Test void quoteRateLimitAndDatabaseBaseBonusChecksAreEnforced(){enable();org.springframework.test.util.ReflectionTestUtils.setField(marketing,"quoteLimit",0);assertThatThrownBy(this::quote).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_REQUEST_RATE_LIMITED");var base=tx.execute(s->marketing.offers(scope,actor)).offers().getFirst();assertThatThrownBy(()->jdbc.update("update wallet_topup_offers set bonus_cents=1 where id=?",base.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);}

 @Test void cglibRepositoryProxySupportsCampaignQuotesAndCustomerCommandsAndKeepsRateRequiresNew(){
  var factory=new org.springframework.aop.framework.ProxyFactory(repository);factory.setProxyTargetClass(true);
  factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(tx.getTransactionManager(),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
  repository=(MarketingRepository)factory.getProxy();
  assertThat(org.springframework.aop.support.AopUtils.isCglibProxy(repository)).isTrue();
  assertThat(repository.jdbc()).isSameAs(jdbc);assertThat(repository.json()).isSameAs(json);
  marketing=new WalletMarketingService(repository,accounts,access,port);config=new WalletMarketingConfigurationService(repository,accounts,access,commands);self=new WalletCustomerTopupService(marketing,repository,access,accounts,topups,commands,port);
  enable();var campaign=publish(draft("proxy campaign",0));
  List<Campaign> campaigns=tx.execute(s->config.campaigns(scope,actor,"published",50,null));assertThat(campaigns).extracting(Campaign::id).contains(campaign.id());
  var q=quote();assertThat(q.bonusCents()).isEqualTo(1000);
  WalletViews.Topup applied=tx.execute(s->self.apply(scope,principal,new Apply(q.quoteId(),key()),"proxy-ip"));
  assertThat(applied.bonusCents()).isEqualTo(1000);assertThat(self.get(scope,principal,applied.id()).id()).isEqualTo(applied.id());
  tx.execute(s->self.declare(scope,principal,applied.id(),new CustomerAction("paid",null,key())));
  WalletViews.Topup cancelled=tx.execute(s->self.cancel(scope,principal,applied.id(),new CustomerAction(null,"cancel",key())));assertThat(cancelled.status()).isEqualTo("cancelled");
  assertThatThrownBy(()->tx.execute(s->{repository.rate(tenant,program,"proxy-rollback","requires-new",100);throw new IllegalStateException("outer rollback");})).isInstanceOf(IllegalStateException.class);
  assertThat(jdbc.queryForObject("select counter from wallet_marketing_rate_limits where tenant_id=? and program_id=? and subject='proxy-rollback' and operation='requires-new'",Integer.class,tenant,program)).isEqualTo(1);
 }
}
