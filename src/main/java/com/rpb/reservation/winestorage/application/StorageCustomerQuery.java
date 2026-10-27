package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.CustomerDirectoryQuery;
import com.rpb.reservation.customer.application.CustomerManagementItem;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class StorageCustomerQuery {
 private final StorageAccess access;
 private final CustomerDirectoryQuery customers;
 public StorageCustomerQuery(StorageAccess access,CustomerDirectoryQuery customers){this.access=access;this.customers=customers;}
 public record Customer(UUID id,String displayName,String phone){}
 public List<Customer> search(StoreScope scope,CurrentActor actor,String query){
  require(scope,actor);String text=query==null?"":query.trim();if(text.length()>120)throw StorageException.invalid();
  if(text.isEmpty())return List.of();return customers.search(scope.tenantScope(),text).stream().map(this::view).toList();
 }
 public Customer get(StoreScope scope,CurrentActor actor,UUID id){require(scope,actor);if(id==null)throw StorageException.invalid();return customers.active(scope.tenantScope(),id).map(this::view).orElseThrow(StorageException::missing);}
 // History readers need the same bounded customer lookup as operators; writes still check receive/deliver.
 private void require(StoreScope scope,CurrentActor actor){access.staff(scope,actor,"wine_storage.view",false);}
 private Customer view(CustomerManagementItem value){return new Customer(value.id(),value.displayName(),value.phoneE164());}
}
