package com.rpb.reservation.winestorage.application;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import static com.rpb.reservation.winestorage.application.StorageInventoryDtos.*;

/** Storage-specific report layout; uses the project's existing POI dependency. */
@Component
public class StorageInventoryWorkbook {
 public byte[] write(Page data,String store,String timezone,Instant now,String query,String status,String locale){
  boolean en=locale.equals("en");var format=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of(timezone));
  SXSSFWorkbook book=new SXSSFWorkbook(100);book.setCompressTempFiles(true);
  try(book;var out=new ByteArrayOutputStream()){
   var header=book.createCellStyle();header.setWrapText(true);header.setFillForegroundColor(IndexedColors.TEAL.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);var font=book.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());header.setFont(font);
   var text=book.createCellStyle();text.setWrapText(true);text.setVerticalAlignment(VerticalAlignment.TOP);
   var number=book.createCellStyle();number.setDataFormat(book.createDataFormat().getFormat("#,##0"));
   Sheet summary=book.createSheet(en?"Summary":"统计汇总");summary.setColumnWidth(0,26*256);summary.setColumnWidth(1,70*256);summary.createFreezePane(0,1);
   row(summary,0,header,en?"Current inventory":"当前寄存清单",en?"Filtered result":"筛选结果");
   row(summary,1,text,en?"Store":"门店",store);row(summary,2,text,en?"Timezone":"门店时区",timezone);row(summary,3,text,en?"Exported at":"导出时间",format.format(now));row(summary,4,text,en?"Search":"搜索条件",query.isEmpty()?(en?"All":"全部"):query);row(summary,5,text,en?"Storage status":"保存状态",state(status,en));row(summary,6,text,en?"Scope":"统计口径",en?"All matching held bottles; includes expired stock. One opened bottle counts as one.":"全部筛选结果；包含过期仍在店的酒。已开瓶每瓶计 1。");
   var totals=data.summary();String[] labels=en?new String[]{"Customers","Remaining bottles","Including opened","Including expired","Storage records"}:new String[]{"寄存人数","剩余瓶数","其中已开瓶","其中已过期","寄存记录数"};long[] values={totals.customerCount(),totals.bottleCount(),totals.openedBottleCount(),totals.expiredBottleCount(),totals.itemCount()};
   for(int i=0;i<labels.length;i++){row(summary,7+i,text,labels[i],values[i]);summary.getRow(7+i).getCell(1).setCellStyle(number);}
   Sheet details=book.createSheet(en?"Wine details":"酒品明细");String[] columns=en?new String[]{"Wine name (Chinese)","Wine name (English)","Customer","Phone","Remaining bottles","Originally stored","Bottle state","Storage status","Stored on","Expires on","Location","Shelf / bin","Label","Hostess / custodian"}:new String[]{"中文酒名","英文酒名","顾客姓名","顾客电话","剩余瓶数","原寄存瓶数","酒瓶状态","保存状态","寄存日期","到期日期","存放位置","仓位","标签编号","酒妹 / 保管员"};row(details,0,header,(Object[])columns);details.createFreezePane(0,1);details.setAutoFilter(new CellRangeAddress(0,data.items().size(),0,columns.length-1));details.setRepeatingRows(new CellRangeAddress(0,0,-1,-1));
   for(int i=0;i<columns.length;i++)details.setColumnWidth(i,(i==8||i==9?23:i<2?28:20)*256);
   int index=1;for(var item:data.items()){
    row(details,index,text,item.nameZh(),item.nameEn(),item.customerName(),item.customerPhone(),item.remaining(),item.quantity(),item.opened()?(en?"Opened":"已开瓶"):(en?"Whole":"整瓶"),state(item.status(),en),format.format(item.receivedAt()),format.format(item.expiresAt()),item.locationName(),item.slot(),item.label(),item.custodianName());details.getRow(index).getCell(4).setCellStyle(number);details.getRow(index).getCell(5).setCellStyle(number);index++;
   }
   book.write(out);return out.toByteArray();
  }catch(IOException e){throw new IllegalStateException("storage_inventory_excel_failed",e);}finally{book.dispose();}
 }
 private static String state(String status,boolean en){return status.equals("expired")?(en?"Expired":"已过期"):status.equals("stored")?(en?"In storage":"寄存中"):(en?"All held bottles":"全部在店酒品");}
 private static void row(Sheet sheet,int index,CellStyle style,Object...values){
  var row=sheet.createRow(index);float height=index==0?30:32;
  for(int i=0;i<values.length;i++){
   var cell=row.createCell(i);cell.setCellStyle(style);Object value=values[i];
   if(value instanceof Number n)cell.setCellValue(n.doubleValue());
   else{String content=value==null?"":String.valueOf(value);cell.setCellValue(content);int units=content.codePoints().map(c->c>255?2:1).sum();double width=Math.max(6,sheet.getColumnWidth(i)/256.0-2);height=Math.max(height,(float)(Math.ceil(units/width)*15+8));}
  }
  row.setHeightInPoints(Math.min(height,409));
 }
}
