package com.rpb.reservation.wallet.proof;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.WalletException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.sql.*;
import static com.rpb.reservation.wallet.proof.WalletProofViews.*;

@Repository
public class WalletCollectionProofRepository {
 private static final String METADATA="id,evidence_json,confirmation_json,request_digest,collection_id,content_type,uploaded_by_customer_id";
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public WalletCollectionProofRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public record Stored(Evidence evidence,String requestDigest,UUID collectionId,byte[] image,String contentType,UUID customerId){}
 public Optional<Stored> byKey(StoreScope s,String key){return jdbc.query("select * from wallet_collection_proofs where tenant_id=? and store_id=? and idempotency_key=?",this::row,s.tenantId().value(),s.storeId().value(),key).stream().findFirst();}
 public Stored get(StoreScope s,UUID id,boolean lock){return jdbc.query("select * from wallet_collection_proofs where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),this::row,s.tenantId().value(),s.storeId().value(),id).stream().findFirst().orElseThrow(WalletException::missing);}
 public Stored insert(StoreScope s,Evidence e,String key,String requestDigest,String imageDigest,String contentType,byte[] image,UUID actor){
  return insert(s,e,key,requestDigest,imageDigest,contentType,image,actor,null);
 }
 public Stored insert(StoreScope s,Evidence e,String key,String requestDigest,String imageDigest,String contentType,byte[] image,UUID actor,UUID customer){
  var c=e.context();jdbc.update("insert into wallet_collection_proofs(id,tenant_id,store_id,topup_id,bill_id,channel,expected_account,amount_cents,system_reference,idempotency_key,request_digest,image_digest,content_type,image_bytes,evidence_json,created_by,uploaded_by_customer_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?) on conflict(tenant_id,store_id,idempotency_key) do nothing",e.id(),s.tenantId().value(),s.storeId().value(),c.sourceType().equals("topup")?c.sourceId():null,c.sourceType().equals("bill")?c.sourceId():null,c.channel(),c.expectedAccount(),c.amountCents(),c.systemReference(),key,requestDigest,imageDigest,contentType,image,write(e),actor,customer);
  return byKey(s,key).orElseThrow(WalletException::missing);
 }
 public List<Evidence> list(StoreScope s,String type,UUID source){return jdbc.query("select "+METADATA+" from wallet_collection_proofs where tenant_id=? and store_id=? and "+(type.equals("topup")?"topup_id":"bill_id")+"=? and (collection_id is not null or uploaded_by_customer_id is not null) order by created_at,id",this::metadata,s.tenantId().value(),s.storeId().value(),source).stream().map(Stored::evidence).toList();}
 public List<Stored> customerProofs(StoreScope s,String type,UUID source,UUID customer,boolean pending){
  var args=new ArrayList<Object>(List.of(s.tenantId().value(),s.storeId().value(),source));if(customer!=null)args.add(customer);
  return jdbc.query("select "+METADATA+" from wallet_collection_proofs where tenant_id=? and store_id=? and "+(type.equals("topup")?"topup_id":"bill_id")+"=? and uploaded_by_customer_id is not null"+(customer!=null?" and uploaded_by_customer_id=?":"")+(pending?" and collection_id is null":"")+" order by created_at desc,id limit 100",this::metadata,args.toArray());
 }
 public void bind(StoreScope s,UUID id,UUID collection,Map<String,Object> confirmation){
  try{if(jdbc.update("update wallet_collection_proofs set collection_id=?,confirmation_json=?::jsonb,updated_at=now() where tenant_id=? and store_id=? and id=? and collection_id is null",collection,write(confirmation),s.tenantId().value(),s.storeId().value(),id)!=1)throw WalletCollectionProofService.conflict("凭证已用于其他收款");}
  catch(org.springframework.dao.DuplicateKeyException e){throw WalletCollectionProofService.conflict("此凭证图片已用于其他收款，请核查原交易");}
 }
 private Stored row(ResultSet r,int n)throws SQLException {return read(r,true);}
 private Stored metadata(ResultSet r,int n)throws SQLException {return read(r,false);}
 private Stored read(ResultSet r,boolean image)throws SQLException {
  try {var e=json.readValue(r.getString("evidence_json"),Evidence.class);var confirmation=json.readValue(r.getString("confirmation_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
   return new Stored(new Evidence(e.id(),e.context(),e.bankReference(),e.extracted(),e.checks(),confirmation,e.imageUrl()),r.getString("request_digest"),r.getObject("collection_id",UUID.class),image?r.getBytes("image_bytes"):null,r.getString("content_type"),r.getObject("uploaded_by_customer_id",UUID.class));
  }catch(java.io.IOException ex){throw new IllegalStateException("Invalid stored proof",ex);}
 }
 private String write(Object value){try{return json.writeValueAsString(value);}catch(java.io.IOException e){throw WalletException.invalid();}}
}
