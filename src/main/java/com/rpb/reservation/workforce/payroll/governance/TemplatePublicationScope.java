package com.rpb.reservation.workforce.payroll.governance;
import static com.rpb.reservation.workforce.payroll.governance.TemplatePublicationDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;

/** Scope governs candidates only; it never grants company payroll access. */
final class TemplatePublicationScope {
 private final WorkforceAccess access;private final TemplatePublicationRepository repository;
 TemplatePublicationScope(WorkforceAccess access,TemplatePublicationRepository repository){this.access=access;this.repository=repository;}
 TemplatePublicationRepository.Store managed(CurrentActor actor,UUID store){access.enter(actor,store);access.manage(actor,store);var shop=repository.store(actor.tenantId(),store);if(!shop.active()||shop.companyId()==null||!repository.companyActive(actor.tenantId(),shop.companyId()))throw WorkforceException.forbidden();return shop;}
 void head(CurrentActor actor,UUID store){managed(actor,store);access.headOffice(actor);}
 /** Existing-scope ownership is separate from the current validity of a replacement scope. */
 void authorizeExisting(CurrentActor actor,List<UUID> companies,List<UUID> selected){var shops=repository.stores(actor.tenantId());for(var id:selected){var shop=shops.stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(WorkforceException::forbidden);authorizeStore(actor,shop);}for(var shop:shops)if(companies.contains(shop.companyId()))authorizeStore(actor,shop);}
 private void authorizeStore(CurrentActor actor,TemplatePublicationRepository.Store shop){if(shop.active()){access.enter(actor,shop.id());return;}if(!actor.canAccessStore(shop.id())&&!repository.inactiveStoreAssigned(actor.tenantId(),actor.actorId(),shop.id()))throw WorkforceException.forbidden();}
 List<UUID> ids(List<UUID> ids,boolean required){if(ids==null||(required&&ids.isEmpty())||ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size())throw WorkforceException.invalid();return ids.stream().sorted().toList();}
 void validate(CurrentActor actor,List<UUID> companies,List<UUID> stores){var shops=repository.stores(actor.tenantId());for(var company:companies){if(!repository.companyActive(actor.tenantId(),company))throw WorkforceException.invalid();for(var shop:shops)if(shop.active()&&company.equals(shop.companyId()))access.enter(actor,shop.id());}for(var store:stores){var shop=shops.stream().filter(s->s.id().equals(store)).findFirst().orElseThrow(WorkforceException::invalid);if(!shop.active()||!companies.contains(shop.companyId()))throw WorkforceException.invalid();access.enter(actor,store);}}
 static boolean visible(Release release,TemplatePublicationRepository.Store store){return store.active()&&release.companyIds().contains(store.companyId())&&(release.storeIds().contains(store.id())||(release.includeFutureStores()&&!release.existingStoreIds().contains(store.id())));}
}
