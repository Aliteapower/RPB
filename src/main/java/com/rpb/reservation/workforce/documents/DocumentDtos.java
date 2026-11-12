package com.rpb.reservation.workforce.documents;
import java.time.*;import java.util.*;
public final class DocumentDtos {
 private DocumentDtos(){}
 public record Type(UUID id,String name,boolean enabled,long version){}
 public record TypeWrite(Long expectedVersion,String name,Boolean enabled,Boolean deleted){}
 public record Attachment(UUID id,String filename,String contentType,long size){}
 public record Document(UUID id,UUID typeId,String typeName,String number,LocalDate expiresOn,String notes,long version,List<Attachment> attachments,String holderName,LocalDate approvedOn,LocalDate prEffectiveOn){
 public Document(UUID id,UUID typeId,String typeName,String number,LocalDate expiresOn,String notes,long version,List<Attachment> attachments){this(id,typeId,typeName,number,expiresOn,notes,version,attachments,"",null,null);}
 }
 public record Page(List<Type> types,List<Document> items){}
 public record Write(Long expectedVersion,UUID typeId,String number,LocalDate expiresOn,String notes,Boolean deleted,String holderName,LocalDate approvedOn,LocalDate prEffectiveOn){
 public Write(Long expectedVersion,UUID typeId,String number,LocalDate expiresOn,String notes,Boolean deleted){this(expectedVersion,typeId,number,expiresOn,notes,deleted,null,null,null);}
 }
 public record Version(Long expectedVersion){}
 public record FileData(UUID id,String filename,String contentType,byte[] bytes,String hash,boolean deleted){}
}
