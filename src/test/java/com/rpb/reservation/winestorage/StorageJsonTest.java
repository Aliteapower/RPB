package com.rpb.reservation.winestorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.rpb.reservation.winestorage.application.StorageDtos.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageJsonTest {
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 @Test void fractionalStringNullAndOverflowBottleCountsAreRejectedRatherThanTruncated(){
  for(String value:new String[]{"1.5","\"2\"","null","2147483648"})
   assertThatThrownBy(()->json.readValue("{\"itemId\":\"00000000-0000-0000-0000-000000000001\",\"quantity\":"+value+"}",Selection.class)).isInstanceOf(JsonProcessingException.class);
 }
 @Test void exactCountsAreAcceptedAndCalendarMonthsAreWholeNumbers()throws Exception{
  assertThat(json.readValue("{\"itemId\":\"00000000-0000-0000-0000-000000000001\",\"quantity\":2}",Selection.class).quantity()).isEqualTo(2);
  assertThatThrownBy(()->json.readValue("{\"name\":\"规则\",\"months\":1.5}",RuleWrite.class)).isInstanceOf(JsonProcessingException.class);
 }
 @Test void customerApplicationCountUsesTheSameStrictContract()throws Exception{
  assertThat(json.readValue("{\"quantity\":2}",Line.class).quantity()).isEqualTo(2);
  assertThatThrownBy(()->json.readValue("{\"quantity\":1.5}",Line.class)).isInstanceOf(JsonProcessingException.class);
 }
 @Test void merchantReceiptCountUsesTheSameStrictContract()throws Exception{
  assertThat(json.readValue("{\"quantity\":2}",ReceiptLine.class).quantity()).isEqualTo(2);
  assertThatThrownBy(()->json.readValue("{\"quantity\":1.5}",ReceiptLine.class)).isInstanceOf(JsonProcessingException.class);
 }

 @Test void historicalReceiptAndSnapshotWithoutCustodianRemainReadable()throws Exception{
  assertThat(json.writeValueAsString(new Receive(java.util.List.of(),"legacy","key"))).isEqualTo("{\"lines\":[],\"reason\":\"legacy\",\"idempotencyKey\":\"key\"}");
  assertThat(json.readValue("{\"lines\":[],\"reason\":\"legacy\",\"idempotencyKey\":\"key\"}",Receive.class).custodianId()).isNull();
  assertThat(json.readValue("{\"nameZh\":\"old wine\",\"receiptPhotos\":[],\"applicationPhotos\":[]}",Snapshot.class).custodian()).isNull();
 }
 @Test void legacyLocationFieldsKeepTheirCanonicalShapeAndOptionalSnapshotsStayReadable()throws Exception{
  var old=json.readValue("{\"quantity\":2,\"location\":\"旧仓位\"}",ReceiptLine.class);
  assertThat(old.storageLocationId()).isNull();assertThat(old.storageLocation()).isNull();assertThat(old.location()).isEqualTo("旧仓位");
  assertThat(json.writeValueAsString(old)).doesNotContain("storageLocation");
  assertThat(json.readValue("{\"nameZh\":\"旧酒\"}",Snapshot.class).storageLocation()).isNull();
 }
 @Test void historicalEventsWithoutNamesStayReadableAndActorIdIsRetained()throws Exception{
  var old=json.readValue("{\"actorId\": \"30000000-0000-0000-0000-000000000902\", \"action\": \"received\", \"details\": {}}",Event.class);
  assertThat(old.actorName()).isNull();assertThat(old.actorId().toString()).isEqualTo("30000000-0000-0000-0000-000000000902");
  var named=new Event(old.id(),old.sourceId(),old.action(),old.actorId(),old.details(),old.createdAt(),"王经理");
  assertThat(json.writeValueAsString(named)).contains("王经理",old.actorId().toString());
 }
}
