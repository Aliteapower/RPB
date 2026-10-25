package com.rpb.reservation.catalog.application;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.catalog.application.CatalogDtos.Product;
import com.rpb.reservation.catalog.persistence.CatalogRepository;
import com.rpb.reservation.catalog.domain.CatalogException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Trusted application boundary. Callers must authorize their own business line before invoking. */
@Service
@Transactional(readOnly=true)
public class ProductCatalogQuery {
 private final CatalogRepository r;
 public ProductCatalogQuery(CatalogRepository r){this.r=r;}
 public Product get(StoreScope s,UUID id){return r.get(s,id);}
 public Product requireUsable(StoreScope s,UUID id,String channel){var p=r.get(s,id);if(!p.listingEnabled()||!p.status().equals("active")||!p.channels().contains(channel)||r.categories(s.tenantId().value()).stream().noneMatch(c->c.id().equals(p.categoryId())&&c.enabled()))throw new CatalogException("CATALOG_UNAVAILABLE",409,"产品未上架或不适用于本门店渠道");return p;}
}
