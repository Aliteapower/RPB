package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.api.WorkforcePayrollController;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.walkin.api.*;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class WorkforceDiagnosticsTest {
 @Test void persistenceFailureHasSafeTraceWithoutRequestBodyDatabaseMessageOrClientSuppliedId(CapturedOutput output)throws Exception{
  var actors=mock(CurrentActorProvider.class);var batches=mock(PayrollBatchService.class);
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  var mvc=MockMvcBuilders.standaloneSetup(new WorkforcePayrollController(actors,mock(PayrollConfigurationService.class),batches,mock(PayrollPaymentService.class),mock(PayslipService.class))).build();
  when(batches.detail(any(),any(),any())).thenThrow(new DataAccessResourceFailureException("PRIVATE_SALARY=999999 PRIVATE_BIRTH",new SQLException("PRIVATE_SQL_BIND", "08006")));
  String path="/api/v1/stores/"+UUID.randomUUID()+"/workforce/payroll-batches/"+UUID.randomUUID();
  var result=mvc.perform(get(path).header("X-RPB-Request-Id","UNTRUSTED_ID")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("WORKFORCE_PERSISTENCE_ERROR")).andExpect(header().string("Cache-Control","private, no-store")).andReturn();
  var id=result.getResponse().getHeader("X-RPB-Request-Id");assertThat(id).isNotNull();assertThatCode(()->UUID.fromString(id)).doesNotThrowAnyException();
  assertThat(output.getOut()).contains(id,"DataAccessResourceFailureException","08006","/api/v1/stores/{storeId}/workforce/payroll-batches/{id}").doesNotContain("PRIVATE_","UNTRUSTED_ID");
  assertThat(result.getResponse().getContentAsString()).doesNotContain("PRIVATE_","SQLException","08006");
  doThrow(new IllegalStateException("PRIVATE_AUDIT_SNAPSHOT")).when(batches).detail(any(),any(),any());
  var second=mvc.perform(get(path)).andExpect(status().isInternalServerError()).andReturn();assertThat(second.getResponse().getHeader("X-RPB-Request-Id")).isNotEqualTo(id);assertThat(output.getOut()).contains("IllegalStateException").doesNotContain("PRIVATE_AUDIT_SNAPSHOT");
 }
}
