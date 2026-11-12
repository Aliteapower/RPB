package com.rpb.reservation.workforce.payroll.governance;

import com.fasterxml.jackson.databind.*;
import com.rpb.reservation.workforce.payroll.PayrollDtos.Rule;
import static com.rpb.reservation.workforce.payroll.governance.TemplateUpgradeDtos.*;
import java.util.*;

/** Three snapshots only; this class never merges or applies payroll rules. */
public final class TemplateUpgradeDiff {
 private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
 private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("basis","计薪方式"),Map.entry("rate","基本工资标准"),Map.entry("dayHours","每日标准工时"),Map.entry("weekHours","每周标准工时"),Map.entry("overtimeEnabled","加班计薪"),Map.entry("overtimeMultiplier","加班倍率"),Map.entry("items","工资项目（完整列表）"),Map.entry("generation","工资生成规则"),Map.entry("amount","金额"),Map.entry("kind","项目类型"),Map.entry("proration","折算方式"),Map.entry("label","项目名称"),Map.entry("source","项目来源"));
 private TemplateUpgradeDiff(){}
 public static List<Difference> compare(Rule oldBase,Rule local,Rule target){
  JsonNode a=JSON.valueToTree(oldBase),b=JSON.valueToTree(local),c=JSON.valueToTree(target);
  var result=new ArrayList<Difference>();var fields=new TreeSet<String>();a.fieldNames().forEachRemaining(fields::add);b.fieldNames().forEachRemaining(fields::add);c.fieldNames().forEachRemaining(fields::add);
  for(var field:fields){if(field.equals("items")&&stable(a.get(field),b.get(field),c.get(field))){var left=items(a.get(field));var own=items(b.get(field));var right=items(c.get(field));var ids=new TreeSet<String>();ids.addAll(left.keySet());ids.addAll(own.keySet());ids.addAll(right.keySet());for(var id:ids)walk("items."+id,left.get(id),own.get(id),right.get(id),result);}else walk(field,a.get(field),b.get(field),c.get(field),result);}
  return result.stream().map(d->{if(!d.path().startsWith("items."))return d;String id=d.path().split("\\.")[1];for(var rule:List.of(local,target,oldBase))for(var item:rule.items())if(item.itemId()!=null&&item.itemId().toString().equals(id)){String field=d.path().substring(d.path().lastIndexOf('.')+1);return new Difference(d.path(),item.label()+" · "+LABELS.getOrDefault(field,"工资项目"),d.oldBase(),d.local(),d.target(),d.localChanged(),d.targetChanged(),d.conflict());}return d;}).toList();
 }
 private static void walk(String path,JsonNode a,JsonNode b,JsonNode c,List<Difference> result){
  if(Objects.equals(a,b)&&Objects.equals(a,c))return;
  if(a!=null&&b!=null&&c!=null&&a.isObject()&&b.isObject()&&c.isObject()){
   var fields=new TreeSet<String>();a.fieldNames().forEachRemaining(fields::add);b.fieldNames().forEachRemaining(fields::add);c.fieldNames().forEachRemaining(fields::add);for(var field:fields)walk(path+"."+field,a.get(field),b.get(field),c.get(field),result);return;
  }
  boolean lc=!Objects.equals(a,b),tc=!Objects.equals(a,c);String field=path.substring(path.lastIndexOf('.')+1);String label=LABELS.getOrDefault(field,path.startsWith("items.")?"工资项目":"工资规则："+field);
  if(path.startsWith("items.")){JsonNode item=b!=null&&b.isObject()?b:c!=null&&c.isObject()?c:a;String name=item!=null&&item.hasNonNull("label")?item.get("label").asText():"工资项目";label=name+" · "+label;}
  result.add(new Difference(path,label,text(a),text(b),text(c),lc,tc,lc&&tc&&!Objects.equals(b,c)));
 }
 private static boolean stable(JsonNode... arrays){for(var array:arrays){if(array==null||!array.isArray())return false;var seen=new HashSet<String>();for(var item:array){var id=item.get("itemId");if(id==null||id.isNull()||!seen.add(id.asText()))return false;}}return true;}
 private static Map<String,JsonNode> items(JsonNode array){var result=new TreeMap<String,JsonNode>();array.forEach(item->result.put(item.get("itemId").asText(),item));return result;}
 public static String value(Rule rule,String path){JsonNode node=JSON.valueToTree(rule);String[] parts=path.split("\\.");for(int i=0;i<parts.length;i++){if(node==null||node.isNull())return null;if(node.isArray()){String id=parts[i];JsonNode selected=null;for(var item:node)if(item.hasNonNull("itemId")&&item.get("itemId").asText().equals(id)){selected=item;break;}node=selected;}else node=node.get(parts[i]);}return text(node);}
 private static String text(JsonNode value){return value==null||value.isNull()?null:value.isTextual()?value.asText():value.toString();}
}
