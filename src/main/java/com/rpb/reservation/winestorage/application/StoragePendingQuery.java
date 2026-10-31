package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.persistence.StorageRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

@Service
@Transactional(readOnly=true)
public class StoragePendingQuery {
 private final StorageAccess access;private final StorageRepository r;
 public StoragePendingQuery(StorageAccess access,StorageRepository r){this.access=access;this.r=r;}
 public List<PendingApplication> list(StoreScope scope,CurrentActor actor){
  access.staff(scope,actor,"wine_storage.view",false);access.staff(scope,actor,"wine_storage.receive",false);
  return r.rows(scope,"select a.id,a.customer_id,c.display_name,c.phone_e164 from wine_storage_applications a join customers c on c.id=a.customer_id and c.tenant_id=a.tenant_id where a.tenant_id=? and a.store_id=? and a.status='pending' order by a.created_at,a.id limit 100").stream().map(row->new PendingApplication(r.application(scope,(UUID)row.get("id"),false),new StorageCustomerQuery.Customer((UUID)row.get("customer_id"),(String)row.get("display_name"),(String)row.get("phone_e164")))).toList();
 }
}
