package com.rpb.reservation.winestorage;

import com.rpb.reservation.catalog.CatalogPostgresTest;
import com.rpb.reservation.catalog.application.CatalogDtos.*;
import com.rpb.reservation.catalog.domain.CatalogException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageCatalogPricingTest extends CatalogPostgresTest {
 @Test void catalogCanMaintainUnpricedArchiveWithoutConvertingNullToZero(){
  var command=new ProductWrite("UNPRICED",product.categoryId(),"新酒","New Wine","","","physical","瓶","",(Long)null,null,null,null,List.of("storage"),null,key());
  var p=tx.execute(s->catalog.save(scope,actor,null,command));assertThat(p.priceCents()).isNull();assertThat(p.taxRate()).isNull();
  var update=new ProductWrite("UNPRICED",p.categoryId(),"新酒补充","Updated Wine","","","physical","瓶","",(Long)null,null,null,null,List.of("storage"),p.version(),key());
  var changed=tx.execute(s->catalog.save(scope,actor,p.id(),update));assertThat(changed.priceCents()).isNull();assertThat(changed.nameEn()).isEqualTo("Updated Wine");
  var invalid=new ProductWrite("BAD",p.categoryId(),"酒","Wine","","","physical","瓶","",(Long)null,null,null,null,List.of("shopping"),null,key());
  assertThatThrownBy(()->tx.execute(s->catalog.save(scope,actor,null,invalid))).isInstanceOf(CatalogException.class);
 }
}
