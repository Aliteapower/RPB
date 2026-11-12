package com.rpb.reservation.workforce.documents.ocr;

import com.rpb.reservation.workforce.documents.DocumentDtos.FileData;

public interface DocumentOcrEngine {
    String recognize(FileData file);
}
