package com.rpb.reservation.wallet.proof;
import com.rpb.reservation.wallet.domain.WalletException;
import org.junit.jupiter.api.Test;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.assertj.core.api.Assertions.*;
class WalletProofImagesTest {
 @Test void acceptsDecodedPngAndJpegButRejectsSpoofedTruncatedAndOversizedImages()throws Exception{
  var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);
  for(String format:java.util.List.of("png","jpeg")){var out=new ByteArrayOutputStream();ImageIO.write(image,format,out);byte[] bytes=out.toByteArray();WalletProofImages.validate("image/"+format,bytes);assertThatThrownBy(()->WalletProofImages.validate("image/"+format,java.util.Arrays.copyOf(bytes,bytes.length/2))).isInstanceOf(WalletException.class);}
  assertThatThrownBy(()->WalletProofImages.validate(null,new byte[]{1,2,3})).isInstanceOf(WalletException.class);
  assertThatThrownBy(()->WalletProofImages.validate("image/png",new byte[]{1,2,3})).isInstanceOf(WalletException.class);
  assertThatThrownBy(()->WalletProofImages.validate("image/png",new byte[5242881])).isInstanceOf(WalletException.class);
 }
 @Test void rejectsFakeWebpBeforeOcrRatherThanTrustingItsContainerHeader(){
  byte[] fake=new byte[30];System.arraycopy("RIFF".getBytes(),0,fake,0,4);java.nio.ByteBuffer.wrap(fake,4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(22);System.arraycopy("WEBPVP8X".getBytes(),0,fake,8,8);
  assertThatThrownBy(()->WalletProofImages.validate("image/webp",fake)).isInstanceOf(WalletException.class);
 }
}
