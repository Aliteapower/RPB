package com.rpb.reservation.payment.application;

import java.util.*;
import java.util.regex.*;

/** Distinct receipt identifiers; ambiguous OCR is missing evidence, never a guessed match. */
public record PaymentReceiptIdentifiers(String systemReference,String bankReference,String recipient,boolean failedOrPending) {
    public static PaymentReceiptIdentifiers parse(String raw){
        String text=Objects.toString(raw,"");
        String nonBankLines=text.replaceAll("(?im)^.*(?:transaction\\s*(?:id|ref(?:erence)?|number)|交易编号|银行交易号)[^\\r\\n]*","");
        String system=unique(nonBankLines,"(?i)\\bW[0-9a-f]{20}\\b",0);
        String bank=unique(text,"(?im)(?:transaction\\s*(?:id|ref(?:erence)?(?:\\s*(?:no\\.?|number))?|number)|银行交易号|交易编号)\\s*[:：#]?\\s*([A-Z0-9][A-Z0-9./-]{2,159})",1);
        String recipient=unique(text,"(?im)^\\s*(?:paynow\\s+uen|recipient\\s+(?:uen|mobile)|uen|收款UEN|收款手机号)\\s*[:：]?\\s*(\\+?[A-Z0-9]{8,32})\\s*$",1);
        boolean blocked=Pattern.compile("(?i)\\b(?:unsuccessful|failed|pending|processing)\\b|失败|处理中|待处理").matcher(text).find();
        return new PaymentReceiptIdentifiers(system,bank,recipient,blocked);
    }
    private static String unique(String text,String regex,int group){
        var values=new LinkedHashSet<String>();var m=Pattern.compile(regex).matcher(text);
        while(m.find())values.add(m.group(group).toUpperCase(Locale.ROOT));
        return values.size()==1?values.iterator().next():null;
    }
}
