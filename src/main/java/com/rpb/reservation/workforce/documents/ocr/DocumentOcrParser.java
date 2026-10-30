package com.rpb.reservation.workforce.documents.ocr;

import java.time.LocalDate;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

/** Conservative label parser: unknown, invalid and conflicting values remain null. */
@Component
public class DocumentOcrParser {
    private static final Map<String,Pattern> LABELS = Map.of(
        "name", label("name(?: of holder)?|holder(?:'s)? name|full name|姓名|持有人姓名"),
        "number", label("(?:passport|document|identity card)\\s*(?:no\\.?|number)|(?:nric|fin)(?:\\s*(?:no\\.?|number))?|证件号(?:码)?|护照号(?:码)?|身份证号(?:码)?"),
        "approved", label("date of approval|approval date|approved on|批准日期|获批日期"),
        "expiry", label("date of expiry|expiry date|expiration date|valid until|valid till|有效期至|到期日期|有效截止日期"),
        "issued", label("date of issue|issue date|issued on|签发日期"));
    private static Pattern label(String labels) {
        return Pattern.compile("^(?:"+labels+")(?:\\s*[:：]\\s*|\\s+|$)(.*?)$",Pattern.CASE_INSENSITIVE);
    }
    public DocumentOcrFields parse(String text) {
        String source=text==null?"":text;
        String[] lines=source.replace("\r\n","\n").replace('\r','\n').split("\\n");
        Map<String,Set<String>> values=new HashMap<>();
        List<String> warnings=new ArrayList<>();
        warnings.add("请人工核对所有识别候选后再保存。");
        for(int i=0;i<lines.length;i++) {
            String line=lines[i].trim();
            for(var entry:LABELS.entrySet()) {
                Matcher matcher=entry.getValue().matcher(line);
                if(!matcher.matches())continue;
                String value=matcher.group(1).trim();
                if(value.isEmpty() && i+1<lines.length) {
                    String next=lines[i+1].trim();
                    if(LABELS.values().stream().noneMatch(p->p.matcher(next).matches()))value=next;
                }
                if(!value.isBlank())values.computeIfAbsent(entry.getKey(), k->new LinkedHashSet<>()).add(value);
            }
        }
        String name=unique(values,"name",warnings),number=unique(values,"number",warnings);
        if(name!=null && (name.length()>100||!name.matches("[\\p{L}\\p{M} .,'’()/-]+")||name.matches("(?i)^of\\s+.*")))name=null;
        if(number!=null && (number.length()>100||!number.matches("[A-Za-z0-9][A-Za-z0-9 /-]{3,99}")))number=null;
        LocalDate approved=date(unique(values,"approved",warnings));
        LocalDate expiry=date(unique(values,"expiry",warnings));
        LocalDate issued=date(unique(values,"issued",warnings));
        boolean formal=Pattern.compile("(?im)^\\s*(?:ENTRY PERMIT|永久居留许可证|入境准证)\\s*$").matcher(source).find();
        boolean excluded=Pattern.compile("(?i)RE[ -]?ENTRY|IN[ -]?PRINCIPLE|\\bIPA\\b|APPLICATION|申请|原则批准").matcher(source).find();
        LocalDate prDate=formal&&!excluded?issued:null;
        if(expiry!=null && ((approved!=null && expiry.isBefore(approved))||(prDate!=null && expiry.isBefore(prDate)))) {
            approved=null;expiry=null;prDate=null;warnings.add("日期顺序有冲突，请手工核对。");
        }
        return new DocumentOcrFields(name,number,approved,expiry,prDate,List.copyOf(warnings));
    }
    private static String unique(Map<String,Set<String>> values,String key,List<String> warnings) {
        Set<String> set=values.getOrDefault(key,Set.of());
        if(set.size()>1){warnings.add("存在多个不同的"+key+"候选，请手工核对。");return null;}
        return set.isEmpty()?null:set.iterator().next();
    }
    static LocalDate date(String value) {
        if(value==null)return null;
        List<DateTimeFormatter> formats=new ArrayList<>(List.of(DateTimeFormatter.ISO_LOCAL_DATE));
        for(String pattern:List.of("uuuu/M/d","uuuu年M月d日","d MMM uuuu","d MMMM uuuu"))
            formats.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));
        String clean=value.trim().replaceAll("\\s+"," ");
        for(var format:formats)try{return LocalDate.parse(clean,format);}catch(DateTimeParseException ignored){}
        Matcher numeric=Pattern.compile("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$").matcher(clean);
        if(numeric.matches()) {
            int a=Integer.parseInt(numeric.group(1)),b=Integer.parseInt(numeric.group(2)),year=Integer.parseInt(numeric.group(3));
            try {if(a>12&&b<=12)return LocalDate.of(year,b,a);if(b>12&&a<=12)return LocalDate.of(year,a,b);}catch(java.time.DateTimeException ignored){}
        }
        return null;
    }
}
