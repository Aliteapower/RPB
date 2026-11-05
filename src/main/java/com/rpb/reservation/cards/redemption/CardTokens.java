package com.rpb.reservation.cards.redemption;
import com.rpb.reservation.cards.application.CardException;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class CardTokens {
 private static final SecureRandom RANDOM=new SecureRandom();private CardTokens(){}
 public static String issue(String prefix){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return prefix+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 public static String hash(String token,String prefix){if(token==null||!token.matches(prefix+"\\.[A-Za-z0-9_-]{43}"))throw CardException.invalid();try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
