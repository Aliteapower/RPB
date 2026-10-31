package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforcePayrollWorkbenchController;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import java.util.*;import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class WorkforcePayrollWorkbenchApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);PayrollWorkbenchService workbench=mock(PayrollWorkbenchService.class);
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/payroll-workbench";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforcePayrollWorkbenchController(actors,workbench)).build();
 @Test void anonymousQueryIsPrivateUnauthorized()throws Exception{when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(base).param("companyId",UUID.randomUUID().toString()).param("month","2026-10")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(workbench);}
 @Test void exactQueryIdentityAndVersionArePassedAndConflictsAndDenialsAreStable()throws Exception{
  var actor=new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of());when(actors.currentActor()).thenReturn(Optional.of(actor));
  UUID company=UUID.randomUUID(),batch=UUID.randomUUID();when(workbench.read(eq(actor),any(),eq(company),eq("2026-10"),eq(3L),eq(batch))).thenThrow(WorkforceException.conflict());
  mvc.perform(get(base).param("companyId",company.toString()).param("month","2026-10").param("batchId",batch.toString()).param("expectedVersion","3")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_CONFLICT"));
  when(workbench.read(eq(actor),any(),eq(company),eq("2026-10"),isNull(),isNull())).thenThrow(WorkforceException.forbidden());
  mvc.perform(get(base).param("companyId",company.toString()).param("month","2026-10")).andExpect(status().isForbidden()).andExpect(header().string("Cache-Control","private, no-store"));
 }
}
