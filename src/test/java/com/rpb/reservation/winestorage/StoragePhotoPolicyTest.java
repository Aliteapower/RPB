package com.rpb.reservation.winestorage;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.persistence.StorageRepository;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class StoragePhotoPolicyTest {
 @Test void optionalReceiptAcceptsNoPhotosButRetainsEvidenceValidation(){
  var scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());var customer=UUID.randomUUID();var repo=mock(StorageRepository.class);var service=new StoragePhotoService(null,repo);
  service.requireOptional(scope,customer,List.of(),"receipt");verifyNoInteractions(repo);
  assertThatThrownBy(()->service.requireOptional(scope,customer,null,"receipt")).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->service.requireOptional(scope,customer,List.of(UUID.randomUUID()),"receipt")).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->service.require(scope,customer,List.of(),"followup")).isInstanceOf(StorageException.class);
 }
}
