package com.rpb.reservation.wallet.collectionmethods;
import java.util.*;
import com.rpb.reservation.wallet.domain.*;
public final class CollectionMethods {
 private CollectionMethods(){}
 public record Method(String code,String name,String type){}
 public record Update(Integer version,List<Method> methods,String idempotencyKey){}
 public record Settings(int version,List<Method> methods,List<UUID> affectedStoreIds){}
 public static List<Method> normalize(List<Method> input,List<String> active,Map<String,String> known){
  if(input==null||input.isEmpty()||input.size()>30)throw WalletException.invalid();
  Set<String> codes=new HashSet<>(),names=new HashSet<>();List<Method> out=new ArrayList<>();
  for(var item:input){
   if(item==null||item.type()==null||!Set.of("cash","paynow","credit_card","other").contains(item.type()))throw WalletException.invalid();
   String name=item.name()==null?"":item.name().strip();if(name.isEmpty()||name.length()>60||name.codePoints().anyMatch(Character::isISOControl)||!names.add(name.toLowerCase(Locale.ROOT)))throw WalletException.invalid();
   String code=item.code();if(code==null)code=item.type()+":"+UUID.randomUUID();
   else if(!active.contains(code)&&!known.containsKey(code))throw WalletException.invalid();
   if(!WalletMoney.channelType(code).equals(item.type())||!codes.add(code))throw WalletException.invalid();
   out.add(new Method(code,name,item.type()));
  }return List.copyOf(out);
 }
}
