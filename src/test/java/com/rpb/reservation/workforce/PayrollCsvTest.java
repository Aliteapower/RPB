package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.reporting.PayrollCsv;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PayrollCsvTest {
 @Test void protectsFormulaTextAndQuotesCommasNewlinesAndDoubleQuotes(){
  assertThat(PayrollCsv.text("  =SUM(A1) ")).isEqualTo("\"'  =SUM(A1) \"");
  for(String v:new String[]{"+cmd","-123","@formula","\tX","\rX","=HYPERLINK(\"a\")\nlabel","\n=cmd"})assertThat(PayrollCsv.text(v)).startsWith("\"'");
  assertThat(PayrollCsv.text("00123")).isEqualTo("\"00123\"");
  assertThat(PayrollCsv.text("李,\"明\"\n员工")).isEqualTo("\"李,\"\"明\"\"\n员工\"");
 }
}
