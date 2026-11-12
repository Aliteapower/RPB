package com.rpb.reservation.loyalty.application;
import com.rpb.reservation.loyalty.application.PointsViews.*;
import com.rpb.reservation.loyalty.domain.*;
import com.rpb.reservation.loyalty.persistence.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class PointsService {
 private final PointsRepository accounts;private final PointsCommandRepository commands;
 public PointsService(PointsRepository accounts,PointsCommandRepository commands){this.accounts=accounts;this.commands=commands;}
 @Transactional public UUID createProgram(UUID tenant,UUID issuer){
  if(tenant==null||issuer==null)throw PointsException.invalid();return accounts.createProgram(tenant,issuer);
 }
 public UUID originalEntryStore(Binding binding,UUID entryId){return accounts.entry(binding,entryId).storeId();}
 public boolean retainsCustomer(com.rpb.reservation.common.scope.TenantScope scope,UUID customer){return accounts.retainsCustomer(scope,customer);}
 public Account get(Binding b,UUID customer){return accounts.account(b,customer,false,false);}
 public List<Entry> entries(Binding b,UUID customer,int offset){return accounts.entries(b,customer,offset);}
 public UUID customerForEntry(Binding b,UUID entry){return accounts.customerForEntry(b,entry);}
 @Transactional public Result mutate(Binding b,UUID actor,UUID customer,String kind,Mutation m){
  if(m==null||m.quantity()==null||!Set.of("manual_grant","manual_deduct","redemption").contains(kind))throw PointsException.invalid();
  PointsRules.required(m.reason(),500);PointsRules.optional(m.note(),2000);PointsRules.optional(m.externalReference(),200);
  if(kind.equals("redemption"))PointsRules.required(m.redemptionDescription(),1000);
  return commands.run(b,actor,kind,m.idempotencyKey(),Map.of("customer",customer,"command",m),()->{
   var a=accounts.account(b,customer,true,true);
   if(!kind.equals("manual_grant"))PointsRules.version(a.version(),m.expectedVersion());
   int balance=PointsRules.balance(a.balance(),m.quantity(),kind.equals("manual_grant"));
   var after=accounts.update(a,balance);
   var entry=accounts.append(b,actor,a,after,kind,m.quantity(),m.reason().trim(),m.note(),m.externalReference(),null);
   var redemption=kind.equals("redemption")?accounts.redeem(b,entry,m.redemptionDescription().trim(),m.externalReference()):null;
   return new Result(after,redemption==null?entry:accounts.entry(b,entry.id()),redemption);
  });
 }
 @Transactional public Result reverse(Binding b,UUID actor,UUID entryId,Reverse r){
  if(r==null)throw PointsException.invalid();PointsRules.required(r.reason(),500);
  return commands.run(b,actor,"reversal",r.idempotencyKey(),Map.of("entryId",entryId,"command",r),()->{
   UUID customer=accounts.customerForEntry(b,entryId);var a=accounts.account(b,customer,false,true);var original=accounts.entry(b,entryId);
   if(original.kind().equals("reversal"))throw PointsException.invalid();
   if(accounts.reversed(b.tenantId(),entryId))throw PointsException.conflict("POINTS_ENTRY_ALREADY_REVERSED","原积分记录已经全额冲正");
   PointsRules.version(a.version(),r.expectedVersion());
   int balance=PointsRules.balance(a.balance(),original.quantity(),!original.kind().equals("manual_grant"));
   var after=accounts.update(a,balance);var entry=accounts.append(b,actor,a,after,"reversal",original.quantity(),r.reason().trim(),null,null,entryId);
   return new Result(after,entry,null);
  });
 }
}
