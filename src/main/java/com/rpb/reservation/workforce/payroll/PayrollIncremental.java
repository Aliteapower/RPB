package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.YearMonth;
import java.util.*;

/** Pure row reuse over a complete source capture; never caches source reads or payroll facts. */
public final class PayrollIncremental {
 /** Bump whenever row calculation, aggregation, or dependency serialization semantics change. */
 public static final String ENGINE_KEY="workforce-payroll-incremental-v6-daily-contract-compliance";
 private static final ObjectMapper MAPPER=new ObjectMapper().registerModule(new JavaTimeModule())
  .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
  .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true)
  .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,false);
 private final PayrollCalculationService calculator;

 public record Cache(String engineKey,String globalKey,Map<UUID,String> lineKeys,PayrollSnapshot snapshot){}
 public record Result(PayrollSnapshot snapshot,Cache cache,int recalculated,int reused,List<UUID> changedEmployeeIds){}
 public PayrollIncremental(PayrollCalculationService calculator){this.calculator=Objects.requireNonNull(calculator);}

 public Result calculate(PayrollSources.Capture capture,YearMonth month,Cache previous,boolean full){
  var keys=lineKeys(capture);var global=globalKey(capture,month);var reusable=valid(previous)&&global.equals(previous.globalKey());
  var changed=changed(capture,keys,previous,reusable);
  PayrollSnapshot snapshot;int recalculated;
  if(full||!reusable){snapshot=calculator.calculate(capture,month);recalculated=capture.lines().size();}
  else {
   var old=new HashMap<UUID,PayrollLine>();previous.snapshot().lines().forEach(line->old.put(line.employeeId(),line));
   var pending=capture.lines().stream().filter(line->!Objects.equals(keys.get(line.employee().id()),previous.lineKeys().get(line.employee().id()))).toList();
   var replacements=new HashMap<UUID,PayrollLine>();
   if(!pending.isEmpty()){
    var partial=new PayrollSources.Capture(capture.fingerprint(),capture.company(),pending,capture.stores(),capture.statutoryPolicy());
    calculator.calculate(partial,month).lines().forEach(line->replacements.put(line.employeeId(),line));
   }
   var ordered=new ArrayList<PayrollLine>();
   for(var source:capture.lines()){
    var id=source.employee().id();ordered.add(replacements.containsKey(id)?replacements.get(id):old.get(id));
   }
   snapshot=calculator.aggregate(capture,month,ordered);recalculated=pending.size();
  }
  return new Result(snapshot,new Cache(ENGINE_KEY,global,Collections.unmodifiableMap(keys),snapshot),recalculated,capture.lines().size()-recalculated,changed);
 }

 public Cache cache(PayrollSources.Capture capture,YearMonth month,PayrollSnapshot snapshot){
  return new Cache(ENGINE_KEY,globalKey(capture,month),Collections.unmodifiableMap(lineKeys(capture)),snapshot);
 }
 public List<UUID> changed(PayrollSources.Capture capture,YearMonth month,Cache previous){
  return changed(capture,lineKeys(capture),previous,valid(previous)&&globalKey(capture,month).equals(previous.globalKey()));
 }
 private List<UUID> changed(PayrollSources.Capture capture,Map<UUID,String> keys,Cache previous,boolean reusable){
  var changed=new LinkedHashSet<UUID>();
  for(var source:capture.lines()){
   var id=source.employee().id();if(!reusable||!Objects.equals(keys.get(id),previous.lineKeys().get(id)))changed.add(id);
  }
  // Report removals even if an older/incomplete cache forces all current lines to recalculate.
  if(previous!=null){
   if(previous.snapshot()!=null&&previous.snapshot().lines()!=null)for(var line:previous.snapshot().lines())if(line!=null&&!keys.containsKey(line.employeeId()))changed.add(line.employeeId());
   if(previous.lineKeys()!=null)previous.lineKeys().keySet().stream().filter(Objects::nonNull).filter(id->!keys.containsKey(id)).sorted().forEach(changed::add);
  }
  return List.copyOf(changed);
 }
 private boolean valid(Cache cache){
  if(cache==null||!ENGINE_KEY.equals(cache.engineKey())||!hashKey(cache.globalKey())||cache.lineKeys()==null||cache.snapshot()==null||cache.snapshot().lines()==null)return false;
  var ids=new HashSet<UUID>();
  for(var line:cache.snapshot().lines())if(line==null||line.employeeId()==null||!ids.add(line.employeeId()))return false;
  return ids.equals(cache.lineKeys().keySet())&&cache.lineKeys().values().stream().allMatch(PayrollIncremental::hashKey);
 }
 private static boolean hashKey(String key){return key!=null&&key.matches("[0-9a-f]{64}");}
 private String globalKey(PayrollSources.Capture capture,YearMonth month){
  return hash(Arrays.asList(ENGINE_KEY,month.toString(),capture.company(),capture.statutoryPolicy()));
 }
 private LinkedHashMap<UUID,String> lineKeys(PayrollSources.Capture capture){
  var keys=new LinkedHashMap<UUID,String>();
  for(var line:capture.lines())if(keys.put(line.employee().id(),hash(line))!=null)throw new IllegalArgumentException("duplicate payroll employee");
  return keys;
 }
 private String hash(Object value){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}
  catch(JsonProcessingException|NoSuchAlgorithmException e){throw new IllegalStateException("payroll_incremental_key",e);}
 }
 /** Compare every persisted result field; calculation wall time is the only exception. */
 public static boolean sameResult(PayrollSnapshot left,PayrollSnapshot right){
  return Objects.equals(withoutTime(left),withoutTime(right));
 }
 private static PayrollSnapshot withoutTime(PayrollSnapshot value){
  return value==null?null:new PayrollSnapshot(value.sourceFingerprint(),value.companyName(),value.month(),value.policyVersion(),value.lines(),value.issues(),value.totalGross(),value.totalNet(),value.totalEmployeeCpf(),value.totalEmployerCpf(),value.totalSdl(),value.totalEmployerCost(),null,value.statutoryPolicy());
 }
}
