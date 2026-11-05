package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceSegmentController;
import com.rpb.reservation.workforce.payroll.segment.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import java.util.*;import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class WorkforceSegmentApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);SegmentService service=mock(SegmentService.class);String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceSegmentController(actors,service)).build();
 @Test void anonymousCannotReadOrWritePrivatePayroll()throws Exception{when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(base+"/payroll-segments").param("companyId",UUID.randomUUID().toString()).param("month","2026-10")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));mvc.perform(put(base+"/payroll-segments/"+UUID.randomUUID()).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void confirmRequiresSameOriginHeaderAndForwardsExactCommandVersion()throws Exception{var a=new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of());when(actors.currentActor()).thenReturn(Optional.of(a));var id=UUID.randomUUID();var cmd=UUID.randomUUID();String path=base+"/payroll-segments/"+id+"/commands/"+cmd;String body="{\"expectedVersion\":3,\"action\":\"confirm\",\"note\":\"reviewed\"}";mvc.perform(post(path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content(body)).andExpect(status().isForbidden());verifyNoInteractions(service);mvc.perform(post(path).header("X-RPB-Workforce","1").contentType("application/json").content(body)).andExpect(status().isOk());verify(service).action(eq(a),any(),eq(id),eq(cmd),eq(new SegmentDtos.Action(3L,"confirm","reviewed")));}
 @Test void denialAndMissingCommandAreStable()throws Exception{when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(service.detail(any(),any(),any())).thenThrow(WorkforceException.missing());mvc.perform(get(base+"/payroll-segments/"+UUID.randomUUID())).andExpect(status().isNotFound());mvc.perform(get(base+"/payroll-segments").param("month","2026-10")).andExpect(status().isBadRequest());}
}
