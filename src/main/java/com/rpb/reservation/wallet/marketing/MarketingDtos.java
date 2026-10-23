package com.rpb.reservation.wallet.marketing;

import java.time.Instant;
import com.fasterxml.jackson.annotation.*;
import java.util.*;
import com.rpb.reservation.wallet.domain.WalletException;

/** Immutable API contracts; monetary input is decimal SGD text. */
public final class MarketingDtos {
 private MarketingDtos() {}
 public record OfferInput(String amount,String bonus) {}
 public record SettingsUpdate(int expectedVersion,@JsonAlias("baseOffers") List<OfferInput> offers,String deductionPolicy,@JsonAlias("selfServiceEnabled") boolean selfTopupEnabled,boolean campaignsEnabled,boolean pointsEnabled,String idempotencyKey) {}
 public record CampaignDraft(String name,@JsonAlias("startsAt") Instant startAt,@JsonAlias("endsAt") Instant endAt,int priority,List<OfferInput> offers,String terms,int expectedVersion,String idempotencyKey) {}
 public record Publish(int draftVersion,int expectedVersion,String idempotencyKey) {}
 public record Lifecycle(int expectedVersion,String reason,String idempotencyKey) {}
 public record Offer(UUID id,int version,long principalCents,long bonusCents,long creditTotalCents) {}
 public record Offers(UUID campaignId,Integer campaignVersion,String campaignName,String deductionPolicy,int policyVersion,List<String> channels,List<Offer> offers,boolean selfServiceEnabled) {}
 public record Settings(int version,String deductionPolicy,int policyVersion,boolean selfServiceEnabled,boolean campaignsEnabled,boolean pointsEnabled,List<UUID> affectedStoreIds,List<Offer> baseOffers) {}
 public record Campaign(UUID id,String name,String status,int version,Integer currentPublishedRevision,List<Revision> revisions,Instant createdAt) {
  @JsonProperty(access=JsonProperty.Access.READ_ONLY) public int draftVersion(){return revisions.isEmpty()?0:revisions.getFirst().revision();}
  @JsonProperty(access=JsonProperty.Access.READ_ONLY) public Instant startsAt(){return revisions.isEmpty()?null:revisions.getFirst().startAt();}
  @JsonProperty(access=JsonProperty.Access.READ_ONLY) public Instant endsAt(){return revisions.isEmpty()?null:revisions.getFirst().endAt();}
  @JsonProperty(access=JsonProperty.Access.READ_ONLY) public String terms(){return revisions.isEmpty()?"":revisions.getFirst().terms();}
  @JsonProperty(access=JsonProperty.Access.READ_ONLY) public int priority(){return revisions.isEmpty()?0:revisions.getFirst().priority();}
  @JsonProperty(access=JsonProperty.Access.READ_ONLY) public List<Offer> offers(){return revisions.isEmpty()?List.of():revisions.getFirst().offers();}
 }
 public record Revision(int revision,Instant startAt,Instant endAt,int priority,String terms,List<Offer> offers) {}
 public record QuoteRequest(UUID accountId,UUID offerId,int offerVersion,String channel) {}
 public record CustomerQuote(UUID offerId,int offerVersion,String channel) {}
 public record Apply(UUID quoteId,String idempotencyKey) {}
 public record CustomerAction(String note,String reason,String idempotencyKey) {}
 public record Quote(UUID quoteId,UUID offerId,int offerVersion,UUID campaignId,Integer campaignVersion,long principalCents,long bonusCents,long creditTotalCents,String channel,String deductionPolicy,int policyVersion,Instant expiresAt) {}
 public static WalletException changed() {return new WalletException("WALLET_OFFER_CHANGED",409,"充值档位已变更，请重新选择");}
 public static WalletException stale() {return new WalletException("WALLET_CAMPAIGN_CONFLICT",409,"活动或配置版本已变更，或同优先级活动时间重叠");}
}
