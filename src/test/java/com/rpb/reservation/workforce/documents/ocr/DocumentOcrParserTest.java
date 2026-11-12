package com.rpb.reservation.workforce.documents.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DocumentOcrParserTest {
    private final DocumentOcrParser parser = new DocumentOcrParser();
    @Test void extractsExplicitLabelsAndFormalEntryPermitDate() {
        var result = parser.parse("ENTRY PERMIT\nName: TAN AH KOW\nNRIC No: S1234567A\nDate of Approval: 15 Oct 2024\nDate of Issue: 15 Oct 2024\nValid Until: 2029-10-15");
        assertThat(result.name()).isEqualTo("TAN AH KOW");
        assertThat(result.number()).isEqualTo("S1234567A");
        assertThat(result.approvedOn()).isEqualTo(LocalDate.of(2024,10,15));
        assertThat(result.prDate()).isEqualTo(result.approvedOn());
        assertThat(result.expiresOn()).isEqualTo(LocalDate.of(2029,10,15));
        assertThat(result.warnings()).contains("请人工核对所有识别候选后再保存。");
    }
    @Test void extractsChineseAndRejectsAmbiguousNumericDate() {
        var result=parser.parse("姓名：张三\n证件号码：E12345678\n批准日期：2024年10月15日\n有效期至：03/04/2029");
        assertThat(result.name()).isEqualTo("张三");
        assertThat(result.approvedOn()).isEqualTo(LocalDate.of(2024,10,15));
        assertThat(result.expiresOn()).isNull();
        assertThat(result.prDate()).isNull();
    }
    @Test void issueDateOnlyCountsForFormalEntryPermit() {
        assertThat(parser.parse("ENTRY PERMIT\nDate of Approval: 15 Oct 2024").prDate()).isNull();
        assertThat(parser.parse("PASSPORT\nDATE OF ISSUE: 15 Oct 2024").approvedOn()).isNull();
        assertThat(parser.parse("ENTRY PERMIT\nDATE OF ISSUE: 15 Oct 2024").prDate()).isEqualTo(LocalDate.of(2024,10,15));
        for(String title : new String[]{"IN-PRINCIPLE APPROVAL ENTRY PERMIT", "RE-ENTRY PERMIT", "REENTRY PERMIT", "IPA ENTRY PERMIT", "APPLICATION FOR ENTRY PERMIT"})
            assertThat(parser.parse(title+"\nENTRY PERMIT\nDate of Issue: 15 Oct 2024").prDate()).as(title).isNull();
    }
    @Test void avoidsFalseLabelsAndConflictingCandidates() {
        var result=parser.parse("Employer Name: ACME\nFather Name: JOHN\nApplication Number: ABC\nName: TAN\nName: LIM\nApproval date: 2024-02-30");
        assertThat(result.name()).isNull();assertThat(result.number()).isNull();assertThat(result.approvedOn()).isNull();
        var falseLabels=parser.parse("Name of Employer: ACME\nPassport Application Number: ABC123");
        assertThat(falseLabels.name()).isNull();assertThat(falseLabels.number()).isNull();
    }
    @Test void formalIssueDateAfterExpiryDoesNotReturnContradictoryDates() {
        var result=parser.parse("ENTRY PERMIT\nDate of Issue: 2026-01-01\nValid Until: 2025-01-01");
        assertThat(result.prDate()).isNull();assertThat(result.expiresOn()).isNull();
        assertThat(result.warnings()).contains("日期顺序有冲突，请手工核对。");
    }
    @Test void supportsCrLfLabelsOnSeparateLines() {
        var result=parser.parse("Name:\r\nTAN AH KOW\r\nDate of Approval:\r\n2024-10-15");
        assertThat(result.name()).isEqualTo("TAN AH KOW");assertThat(result.approvedOn()).isEqualTo(LocalDate.of(2024,10,15));
    }
}
