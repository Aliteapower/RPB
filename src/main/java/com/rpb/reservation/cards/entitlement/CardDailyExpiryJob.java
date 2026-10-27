package com.rpb.reservation.cards.entitlement;

import java.time.Clock;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Each store settles independently; card row locks make multiple instances safe. */
@Configuration
@EnableScheduling
public class CardDailyExpiryJob {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CardDailyExpiryJob.class);
 private final CardDailyExpiryRepository expiry;private final Clock clock;
 public CardDailyExpiryJob(CardDailyExpiryRepository expiry,Clock clock){this.expiry=expiry;this.clock=clock;}
 @Scheduled(initialDelay=60000,fixedDelay=60000)
 public void settle(){var now=clock.instant();for(var scope:expiry.dueScopes(now)){try{expiry.settleScope(scope,null,now);}catch(RuntimeException error){log.error("Card daily expiry failed for tenant {} store {}",scope.tenantId().value(),scope.storeId().value(),error);}}}
}
