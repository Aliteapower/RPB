package com.rpb.reservation.workforce.documents;
import com.rpb.reservation.workforce.domain.WorkforceException;
import static com.rpb.reservation.workforce.documents.DocumentDtos.*;
import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;
public final class DocumentFileRules {
 private DocumentFileRules(){}
 public static FileData validate(UUID id,String filename,byte[] bytes){
  if(id==null||bytes==null||bytes.length==0)throw invalid();if(bytes.length>5*1024*1024)throw large();String type;
  if(bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))type="application/pdf";
  else if(bytes.length>=12&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xd8&&bytes[2]==(byte)0xff)type="image/jpeg";
  else if(bytes.length>=24&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))type="image/png";
  else if(bytes.length>=20&&new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP"))type="image/webp";
  else throw invalid();
  if(type.startsWith("image/")&&bytes.length>1024*1024)throw large();
  if(type.equals("image/jpeg")||type.equals("image/png")){
   try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(bytes))){var readers=javax.imageio.ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid();var reader=readers.next();try{reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);if(width<1||height<1||(long)width*height>40_000_000)throw invalid();}finally{reader.dispose();}}catch(java.io.IOException e){throw invalid();}
  }
  if(type.equals("image/webp")){long length=java.nio.ByteBuffer.wrap(bytes,4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()&0xffffffffL;String chunk=new String(bytes,12,4,StandardCharsets.US_ASCII);if(length!=bytes.length-8||!Set.of("VP8 ","VP8L","VP8X").contains(chunk))throw invalid();}
  String name=filename==null?"attachment":filename.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","").trim();if(name.isBlank())name="attachment";if(name.length()>150)name=name.substring(0,150);String ext=switch(type){case "image/jpeg"->"jpg";case "image/png"->"png";case "image/webp"->"webp";default->"pdf";};name=name.replaceAll("\\.[^.]*$","")+"."+ext;
  try{return new FileData(id,name,type,bytes,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),false);}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 private static WorkforceException invalid(){return new WorkforceException("WORKFORCE_DOCUMENT_FILE_INVALID",400);}private static WorkforceException large(){return new WorkforceException("WORKFORCE_DOCUMENT_FILE_TOO_LARGE",400);}
}
