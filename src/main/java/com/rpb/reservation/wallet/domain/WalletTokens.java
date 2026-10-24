package com.rpb.reservation.wallet.domain;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class WalletTokens {
    public static final long PERSONAL_CODE_LIFETIME_SECONDS=120;
    private static final SecureRandom RANDOM=new SecureRandom();
    private WalletTokens() {}
    public static String issue(String prefix) {
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);
        return prefix+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String required(String value,String prefix) {
        if(value==null || !value.matches(java.util.regex.Pattern.quote(prefix)+"\\.[A-Za-z0-9_-]{43}")) throw WalletException.invalid();
        return hash(value);
    }
    public static String customerCodeHash(String value) {
        return required(value,value!=null && value.startsWith("WC.")?"WC":"WP");
    }
}
