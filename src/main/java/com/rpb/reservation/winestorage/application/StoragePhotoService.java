package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.common.media.SafeImageValidation;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.winestorage.persistence.StorageRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

@Service
@Transactional(readOnly=true)
public class StoragePhotoService {
 private final StorageAccess access;private final StorageRepository r;
 public StoragePhotoService(StorageAccess access,StorageRepository r){this.access=access;this.r=r;}
 @Transactional public Photo upload(StoreScope s,CustomerAuthPrincipal p,String type,byte[] bytes){access.customer(s,p,true);return upload(s,p.customerId(),p.customerId(),"application",type,bytes);}
 @Transactional public Photo upload(StoreScope s,CurrentActor a,UUID customer,String phase,String type,byte[] bytes){access.staff(s,a,"wine_storage.receive",false);access.customerId(s,customer);if(!Set.of("receipt","followup").contains(Objects.toString(phase,"")))throw StorageException.invalid();return upload(s,customer,a.actorId(),phase,type,bytes);}
 private Photo upload(StoreScope s,UUID customer,UUID actor,String phase,String type,byte[] bytes){
  try{SafeImageValidation.validate(type,bytes);}catch(IllegalArgumentException e){throw StorageException.invalid();}UUID id=UUID.randomUUID();r.update(s,"insert into wine_storage_photos(tenant_id,store_id,id,customer_id,phase,uploaded_by,content_type,image) values(?,?,?,?,?,?,?,?)",id,customer,phase,actor,type,bytes);return new Photo(id,phase);
 }
 public void requireOptional(StoreScope s,UUID customer,List<UUID> photos,String phase){
  if(photos!=null&&photos.isEmpty())return;
  require(s,customer,photos,phase);
 }
 public void require(StoreScope s,UUID customer,List<UUID> photos,String phase){
  if(photos==null||photos.isEmpty()||photos.size()>10||new HashSet<>(photos).size()!=photos.size())throw StorageException.invalid();
  for(UUID id:photos){var rows=r.rows(s,"select id from wine_storage_photos where tenant_id=? and store_id=? and customer_id=? and id=? and phase=?",customer,id,phase);if(rows.isEmpty())throw StorageException.missing();}
 }
 public void attach(StoreScope s,UUID customer,List<UUID> photos,UUID source,String type){for(UUID id:photos)r.update(s,"insert into wine_storage_photo_links(tenant_id,store_id,customer_id,photo_id,source_id,source_type) values(?,?,?,?,?,?)",customer,id,source,type);}
 public Image image(StoreScope s,CustomerAuthPrincipal p,UUID id){access.customer(s,p,false);return image(s,id,p.customerId());}
 public Image image(StoreScope s,CurrentActor a,UUID id){access.staff(s,a,"wine_storage.view",false);return image(s,id,(UUID)null);}
 private Image image(StoreScope s,UUID id,UUID customer){var list=r.rows(s,"select image,content_type from wine_storage_photos where tenant_id=? and store_id=? and id=?"+(customer==null?"":" and customer_id=?"),customer==null?new Object[]{id}:new Object[]{id,customer});if(list.isEmpty())throw StorageException.missing();return new Image((byte[])list.getFirst().get("image"),list.getFirst().get("content_type").toString());}
}
