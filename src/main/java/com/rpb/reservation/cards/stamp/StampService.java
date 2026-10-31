package com.rpb.reservation.cards.stamp;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.cards.command.CardCommands;
import com.rpb.reservation.cards.plan.CardPlanRepository;
import com.rpb.reservation.cards.entitlement.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class StampService {
 private final CardAccess access;private final CardPlanRepository plans;private final CardHoldingRepository r;private final CardCommands commands;private final Clock clock;
 public StampService(CardAccess access,CardPlanRepository plans,CardHoldingRepository r,CardCommands commands,Clock clock){this.access=access;this.plans=plans;this.r=r;this.commands=commands;this.clock=clock;}
 @Transactional public Holding grant(StoreScope s,CurrentActor a,UUID id,String source,String key){access.staff(s,a,"wallet.cards.stamp",true);return commands.run(s,a.actorId(),"stamp",key,Map.of("card",id,"source",source==null?"":source),Holding.class,()->apply(s,id,source,a.actorId(),null));}
 /** Called in the already-authorized merchant-intent transaction. */
 public Holding apply(StoreScope s,UUID id,String source,UUID actor,UUID confirmed){if(source==null||source.isBlank()||source.length()>200)throw CardException.invalid();var h=r.get(s,id,true,clock.instant());var now=clock.instant();requireActive(h,now);if(!h.kind().equals("stamp"))throw CardException.invalid();if(r.sourceExists(s,h.planId(),source))throw CardException.conflict("此消费或签到来源已经集次");var p=h.snapshot();if(!p.repeatable()&&r.liveReward(s,h.id()))throw CardException.conflict("本卡已完成集次");int used=r.used(s,h.id(),CardRules.day(now,h.timezone()));if(h.dailyLimit()!=null&&used>=h.dailyLimit())throw new CardException("CARD_DAILY_LIMIT",409,"今日集次额度已用完");
  int progress=h.progress()+1,round=h.round();if(progress>=p.stampTarget()){round++;progress=0;var reward=r.issue(s,h.customerId(),p,"reward",1,null,h.timezone(),now,CardRules.expiry(now,h.timezone(),p.rewardDays()));r.reward(s,h.id(),round,reward.id());r.entry(s,reward,"grant",1,0,1,CardRules.day(now,h.timezone()),0,0,"reward:"+h.id()+":"+round,null,actor,confirmed,"集满奖励",now);}
  var day=CardRules.day(now,h.timezone());r.usage(s,h.id(),day,1);r.update(s,h,null,progress,round);r.entry(s,h,"stamp",1,h.progress(),progress,day,used,used+1,source,null,actor,confirmed,"集次",now);return r.get(s,id,false,now);
 }
 @Transactional public Holding reverse(StoreScope s,CurrentActor a,UUID entryId,Reverse c){access.staff(s,a,"wallet.cards.reverse",false);if(c==null||c.reason()==null||c.reason().isBlank())throw CardException.invalid();return commands.run(s,a.actorId(),"stamp.reverse",c.idempotencyKey(),Map.of("id",entryId,"body",c),Holding.class,()->{var e=r.entry(s,entryId);if(!e.kind().equals("stamp")||r.reversed(s,entryId))throw CardException.conflict("不能撤回该集次");var h=r.get(s,e.cardId(),true,clock.instant());if(!r.latestStamp(s,h.id()).equals(entryId)||h.progress()!=(e.afterTotal()==null?0:e.afterTotal()))throw CardException.conflict("请按集次倒序撤回");int round=h.round(),progress;
   if(e.rewardRound()!=null){var ids=r.rewards(s,h.id(),e.rewardRound());if(ids.size()!=1)throw CardException.conflict("奖励状态异常");var reward=r.get(s,ids.getFirst(),true,clock.instant());if(!reward.status().equals("active")||reward.remaining()==null||reward.remaining()!=1)throw CardException.conflict("奖励已使用，需人工处理");r.update(s,reward,0,reward.progress(),reward.round());r.status(s,reward.id(),"revoked");r.entry(s,reward,"reward_revoke",1,1,0,CardRules.day(clock.instant(),reward.timezone()),0,0,"revoke:"+entryId,null,a.actorId(),null,c.reason(),clock.instant());progress=h.snapshot().stampTarget()-1;
   }else progress=e.beforeTotal();
   r.usage(s,h.id(),e.businessDay(),-1);r.update(s,h,null,progress,round);r.entry(s,h,"stamp_reverse",1,h.progress(),progress,e.businessDay(),e.afterDaily(),e.beforeDaily(),"reverse:"+entryId,entryId,a.actorId(),null,c.reason(),clock.instant());return r.get(s,h.id(),false,clock.instant());});}
 public static void requireActive(Holding h,Instant now){if(!h.status().equals("active")||now.isBefore(h.startsAt())||!now.isBefore(h.expiresAt()))throw new CardException("CARD_EXPIRED",409,"卡尚未生效、已到期或已停用");}
}
