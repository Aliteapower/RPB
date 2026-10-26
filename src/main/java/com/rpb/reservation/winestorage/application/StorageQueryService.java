package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.catalog.application.StorageProductCatalog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.persistence.StorageRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

@Service
@Transactional(readOnly=true)
public class StorageQueryService {
 private final StorageAccess access;private final StorageRepository r;private final StorageProductCatalog products;private final Clock clock;private final com.rpb.reservation.platform.application.SharedStoreIdentityQuery stores;
 public StorageQueryService(StorageAccess access,StorageRepository r,StorageProductCatalog products,Clock clock,com.rpb.reservation.platform.application.SharedStoreIdentityQuery stores){this.stores=stores;this.access=access;this.r=r;this.products=products;this.clock=clock;}
 public Home customer(StoreScope s,CustomerAuthPrincipal p){access.customer(s,p,false);return home(s,p.customerId(),Set.of(),null);}
 public Home staff(StoreScope s,CurrentActor a){access.staff(s,a,"wine_storage.view",false);return home(s,null,a.permissions(),a.storeIds());}
 private Home home(StoreScope s,UUID customer,Set<String> permissions,Set<UUID> allowedStores){var latest=new LinkedHashMap<UUID,Rule>();r.rules(s).forEach(rule->latest.putIfAbsent(rule.categoryId(),rule));var settings=r.settings(s);return new Home(settings.operationMode(),settings.version(),access.timezone(s),stores.active(s.tenantId().value(),s.storeId().value()).orElseThrow().storeName(),stores.activeStores(s.tenantId().value()).stream().filter(store->allowedStores==null||allowedStores.contains(store.id())).map(store->new StoreOption(store.id(),store.storeName())).toList(),access.enabled(s),permissions,List.copyOf(latest.values()),products.categories(s).stream().filter(c->c.enabled()).map(c->new Category(c.id(),c.nameZh(),c.nameEn())).toList(),r.applications(s,customer),r.items(s,customer,clock.instant()),r.intents(s,customer).stream().map(i->!Set.of("completed","cancelled").contains(i.status())&&!clock.instant().isBefore(i.expiresAt())?new Intent(i.id(),i.customerId(),i.origin(),"expired",i.expiresAt(),i.completedAt(),i.selections(),null):i).toList(),r.events(s,customer));}
 public record ProductOptions(List<Product> items,boolean hasMore,int page,List<Category> categories){}
 public ProductOptions options(StoreScope s,CustomerAuthPrincipal p,String query,UUID category,int page,int size,String barcode){access.customer(s,p,false);var result=products.options(s,query,category,page,size,barcode);return new ProductOptions(result.items().stream().map(x->new Product(x.id(),x.categoryId(),x.code(),x.nameZh(),x.nameEn())).toList(),result.hasMore(),result.page(),result.categories().stream().map(c->new Category(c.id(),c.nameZh(),c.nameEn())).toList());}
 public List<Product> products(StoreScope s,CustomerAuthPrincipal p,String query){access.customer(s,p,false);return products(s,query,false);}
 public Product product(StoreScope s,CurrentActor a,UUID id){access.staff(s,a,"wine_storage.view",false);var p=products.selectable(s,id);return new Product(p.id(),p.categoryId(),p.code(),p.nameZh(),p.nameEn());}
 public List<Product> products(StoreScope s,CurrentActor a,String query){access.staff(s,a,"wine_storage.view",false);return products(s,query,true);}
 private List<Product> products(StoreScope s,String query,boolean review){return products.search(s,query,review).stream().map(p->new Product(p.id(),p.categoryId(),p.code(),p.nameZh(),p.nameEn())).toList();}
}
