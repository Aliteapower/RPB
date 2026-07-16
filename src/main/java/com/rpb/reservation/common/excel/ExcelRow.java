package com.rpb.reservation.common.excel;

import java.util.Map;

public record ExcelRow(
    int rowNumber,
    Map<String, String> cells
) {
    public String cell(String header) {
        return cells.get(header);
    }
}
