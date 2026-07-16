package com.rpb.reservation.common.excel;

import java.util.function.Function;

public record ExcelColumn<T>(
    String header,
    Function<T, Object> valueExtractor
) {
    public ExcelColumn {
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("excel_header_required");
        }
        if (valueExtractor == null) {
            throw new IllegalArgumentException("excel_value_extractor_required");
        }
    }
}
