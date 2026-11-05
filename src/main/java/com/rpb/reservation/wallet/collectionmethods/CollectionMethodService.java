package com.rpb.reservation.wallet.collectionmethods;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.domain.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.collectionmethods.CollectionMethods.*;
@Service
@Transactional
public class CollectionMethodService {
 private final WalletAccess access;private final WalletAccountRepository accounts;private final WalletDirectory directory;private final WalletCommandRepository commands;private final CollectionMethodRepository methods;
 public CollectionMethodService(WalletAccess access,WalletAccountRepository accounts,WalletDirectory directory,WalletCommandRepository commands,CollectionMethodRepository methods){this.access=access;this.accounts=accounts;this.directory=directory;this.commands=commands;this.methods=methods;}
 private void authorize(StoreScope s,CurrentActor a){access.staff(s,a,"wallet.settings.manage",true);if(a==null||!a.roles().contains("tenant_admin"))throw new WalletException("FORBIDDEN",403,"仅商户管理员可维护收款科目");}
 public Settings get(StoreScope s,CurrentActor a){authorize(s,a);var p=accounts.requireProgram(s,false);var labels=methods.labels(s,p.id());return new Settings(p.version(),p.channels().stream().map(c->new Method(c,labels.getOrDefault(c,""),WalletMoney.channelType(c))).toList(),accounts.programStores(s.tenantId().value(),p.id()));}
 public Settings save(StoreScope s,CurrentActor a,Update c){authorize(s,a);if(c==null)throw WalletException.invalid();return commands.run(s,a.actorId(),"collection-methods.configure",c.idempotencyKey(),c,Settings.class,()->{
  var p=accounts.requireProgram(s,true);var labels=methods.labels(s,p.id());var normalized=CollectionMethods.normalize(c.methods(),p.channels(),labels);
  var issuer=directory.store(s).operatingEntityId();var updated=accounts.configure(s,issuer,normalized.stream().map(Method::code).toList(),c.version());
  normalized.forEach(m->labels.put(m.code(),m.name()));methods.save(s,updated.id(),labels);var result=get(s,a);commands.audit(s,a.actorId(),"collection-methods.configure",updated.id(),result);return result;
 });}
}
