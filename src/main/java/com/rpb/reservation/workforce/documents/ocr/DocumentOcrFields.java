package com.rpb.reservation.workforce.documents.ocr;

import java.time.LocalDate;
import java.util.List;

/** Candidate values only. OCR source text is deliberately absent from the API model. */
public record DocumentOcrFields(String name, String number, LocalDate approvedOn,
                                LocalDate expiresOn, LocalDate prDate, List<String> warnings) {}
