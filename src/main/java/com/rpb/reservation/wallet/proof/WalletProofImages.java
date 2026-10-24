package com.rpb.reservation.wallet.proof;

import com.rpb.reservation.wallet.domain.WalletException;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

final class WalletProofImages {
 private WalletProofImages(){}
 static void validate(String type,byte[] bytes){
  if(type==null||bytes==null||bytes.length==0||bytes.length>5*1024*1024)throw WalletException.invalid();
  if(!java.util.Set.of("image/png","image/jpeg").contains(type))throw WalletException.invalid();
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw WalletException.invalid();var reader=readers.next();
   try{reader.setInput(input);String format=reader.getFormatName();if(!(type.equals("image/png")?format.equalsIgnoreCase("png"):format.equalsIgnoreCase("jpeg"))||reader.getWidth(0)<=0||reader.getHeight(0)<=0||(long)reader.getWidth(0)*reader.getHeight(0)>30000000)throw WalletException.invalid();if(reader.read(0)==null)throw WalletException.invalid();}finally{reader.dispose();}
  }catch(java.io.IOException e){throw WalletException.invalid();}
 }
}
