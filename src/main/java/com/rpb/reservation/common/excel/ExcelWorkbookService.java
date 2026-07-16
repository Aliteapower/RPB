package com.rpb.reservation.common.excel;

import java.util.List;

public interface ExcelWorkbookService {
    <T> byte[] writeSheet(String sheetName, List<ExcelColumn<T>> columns, List<T> rows);

    List<ExcelRow> readFirstSheet(byte[] content, List<String> requiredHeaders);
}
