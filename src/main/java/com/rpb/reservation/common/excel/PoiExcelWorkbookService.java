package com.rpb.reservation.common.excel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class PoiExcelWorkbookService implements ExcelWorkbookService {

    @Override
    public <T> byte[] writeSheet(String sheetName, List<ExcelColumn<T>> columns, List<T> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int index = 0; index < columns.size(); index++) {
                header.createCell(index).setCellValue(columns.get(index).header());
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                T item = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    writeCell(row.createCell(columnIndex), columns.get(columnIndex).valueExtractor().apply(item));
                }
            }

            for (int index = 0; index < columns.size(); index++) {
                sheet.autoSizeColumn(index);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("excel_write_failed", exception);
        }
    }

    @Override
    public List<ExcelRow> readFirstSheet(byte[] content, List<String> requiredHeaders) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("excel_sheet_required");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Map<String, Integer> indexes = headerIndexes(header);
            for (String requiredHeader : requiredHeaders) {
                if (!indexes.containsKey(requiredHeader)) {
                    throw new IllegalArgumentException("excel_header_missing:" + requiredHeader);
                }
            }

            DataFormatter formatter = new DataFormatter();
            List<ExcelRow> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlank(row, indexes.values(), formatter)) {
                    continue;
                }
                Map<String, String> cells = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : indexes.entrySet()) {
                    cells.put(entry.getKey(), formatter.formatCellValue(row.getCell(entry.getValue())).trim());
                }
                rows.add(new ExcelRow(rowIndex + 1, cells));
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("excel_read_failed", exception);
        }
    }

    private static Map<String, Integer> headerIndexes(Row header) {
        if (header == null) {
            throw new IllegalArgumentException("excel_header_required");
        }
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> indexes = new HashMap<>();
        for (Cell cell : header) {
            String name = formatter.formatCellValue(cell).trim();
            if (!name.isBlank()) {
                indexes.put(name, cell.getColumnIndex());
            }
        }
        return indexes;
    }

    private static boolean isBlank(Row row, Iterable<Integer> indexes, DataFormatter formatter) {
        for (Integer index : indexes) {
            if (!formatter.formatCellValue(row.getCell(index)).trim().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean booleanValue) {
            cell.setCellType(CellType.BOOLEAN);
            cell.setCellValue(booleanValue);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }
}
