package com.rpb.reservation.winestorage;

import com.rpb.reservation.winestorage.application.*;
import static com.rpb.reservation.winestorage.application.StorageInventoryDtos.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageInventoryWorkbookTest {
 final Instant now=Instant.parse("2026-10-27T00:00:00Z");
 @Test void workbookHasFilteredSummaryTypedDetailsTimezoneAndLiteralBusinessText()throws Exception{
  var row=new StorageInventoryDtos.Row(UUID.randomUUID(),UUID.randomUUID(),"=HYPERLINK(\"bad\")","+6591234567","茅台","Moutai",5,2,false,now,now.plusSeconds(86400),"stored","前台","A柜","0012","小兰");
  byte[] bytes=new StorageInventoryWorkbook().write(new Page(List.of(row),new Summary(1,2,0,0,1),0,50000,false),"测试门店","Asia/Singapore",now,"小兰","stored","zh-CN");
  Files.createDirectories(Path.of("target/storage-inventory-export"));Files.write(Path.of("target/storage-inventory-export/example.xlsx"),bytes);
  try(var book=WorkbookFactory.create(new ByteArrayInputStream(bytes))){
   assertThat(book.getNumberOfSheets()).isEqualTo(2);var summary=book.getSheetAt(0);assertThat(summary.getSheetName()).isEqualTo("统计汇总");assertThat(summary.getRow(7).getCell(1).getNumericCellValue()).isEqualTo(1);assertThat(summary.getRow(8).getCell(1).getNumericCellValue()).isEqualTo(2);
   var details=book.getSheetAt(1);assertThat(details.getLastRowNum()).isEqualTo(1);assertThat(details.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(2);assertThat(details.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.STRING);assertThat(details.getRow(1).getCell(2).getStringCellValue()).startsWith("=HYPERLINK");assertThat(details.getRow(1).getCell(3).getStringCellValue()).isEqualTo("+6591234567");assertThat(details.getRow(1).getCell(8).getStringCellValue()).isEqualTo("2026-10-27 08:00:00");assertThat(details.getRow(1).getCell(12).getStringCellValue()).isEqualTo("0012");assertThat(((org.apache.poi.xssf.usermodel.XSSFSheet)details).getCTWorksheet().isSetAutoFilter()).isTrue();assertThat(details.getPaneInformation().isFreezePane()).isTrue();
  }
 }
 @Test void emptyEnglishWorkbookStillIncludesZeroSummaryAndDetailHeaders()throws Exception{
  var bytes=new StorageInventoryWorkbook().write(new Page(List.of(),new Summary(0,0,0,0,0),0,50000,false),"Store","Asia/Singapore",now,"","all","en");
  try(var book=WorkbookFactory.create(new ByteArrayInputStream(bytes))){assertThat(book.getSheetName(0)).isEqualTo("Summary");assertThat(book.getSheetAt(0).getRow(8).getCell(1).getNumericCellValue()).isZero();assertThat(book.getSheetAt(1).getLastRowNum()).isZero();assertThat(book.getSheetAt(1).getRow(0).getCell(4).getStringCellValue()).isEqualTo("Remaining bottles");}
 }
}
