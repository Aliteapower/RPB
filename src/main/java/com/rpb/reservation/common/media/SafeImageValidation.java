package com.rpb.reservation.common.media;


import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

public final class SafeImageValidation {
 private SafeImageValidation(){}
 public static void validate(String type,byte[] bytes){
  if(type==null||bytes==null||bytes.length==0||bytes.length>5*1024*1024)throw new IllegalArgumentException("Invalid image");
  if(!java.util.Set.of("image/png","image/jpeg").contains(type))throw new IllegalArgumentException("Invalid image");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Invalid image");var reader=readers.next();
   try{reader.setInput(input);String format=reader.getFormatName();if(!(type.equals("image/png")?format.equalsIgnoreCase("png"):format.equalsIgnoreCase("jpeg"))||reader.getWidth(0)<=0||reader.getHeight(0)<=0||(long)reader.getWidth(0)*reader.getHeight(0)>30000000)throw new IllegalArgumentException("Invalid image");if(reader.read(0)==null)throw new IllegalArgumentException("Invalid image");}finally{reader.dispose();}
  }catch(java.io.IOException e){throw new IllegalArgumentException("Invalid image");}
 }
}
