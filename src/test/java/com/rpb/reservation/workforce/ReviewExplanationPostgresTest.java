package com.rpb.reservation.workforce;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.approval.explanations.*;
import com.rpb.reservation.workforce.application.WorkforceAudit;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReviewExplanationPostgresTest extends WorkforcePostgresTest {
    ReviewExplanationRepository notes;ReviewExplanationService service;
    @BeforeEach void notesSetup(){notes=new ReviewExplanationRepository(jdbc);service=proxy(new ReviewExplanationService(access,notes,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));}
    @Test void dictionaryIsTenantScopedVersionedAndReplaySafe(){
        UUID id=UUID.randomUUID();var c=new ReviewExplanationService.Write(0L,"LATE","  交通延误，已核实  ",true);
        var saved=service.save(admin,store,id,c);assertThat(saved.text()).isEqualTo("交通延误，已核实");assertThat(service.save(admin,store,id,c)).isEqualTo(saved);
        assertThat(service.list(admin,sibling).items()).contains(saved);assertThat(notes.list(UUID.randomUUID())).isEmpty();
        var changed=service.save(admin,store,id,new ReviewExplanationService.Write(1L,"LATE","新说明",false));assertThat(changed.version()).isEqualTo(2);assertThat(changed.enabled()).isFalse();
        assertThatThrownBy(()->service.save(admin,store,id,new ReviewExplanationService.Write(1L,"LATE","旧编辑",true))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
    }
    @Test void dictionaryRejectsStaffInvalidDataAndDuplicateRows(){
        var worker=new CurrentActor(tenant,adminId,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));
        assertThatThrownBy(()->service.list(worker,store)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
        assertThatThrownBy(()->service.save(worker,store,UUID.randomUUID(),new ReviewExplanationService.Write(0L,"GENERAL","说明",true))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
        for(var c:List.of(new ReviewExplanationService.Write(0L,"INVALID","说明",true),new ReviewExplanationService.Write(0L,"LATE","  ",true),new ReviewExplanationService.Write(0L,"LATE","x".repeat(501),true)))assertThatThrownBy(()->service.save(admin,store,UUID.randomUUID(),c)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(400);
        service.save(admin,store,UUID.randomUUID(),new ReviewExplanationService.Write(0L,"GENERAL","说明",true));
        assertThatThrownBy(()->service.save(admin,store,UUID.randomUUID(),new ReviewExplanationService.Write(0L,"GENERAL","说明",true))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
    }
    @Test void dictionaryAuditFailureRollsBack(){
        doThrow(new IllegalStateException("audit failed")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
        assertThatThrownBy(()->service.save(admin,store,UUID.randomUUID(),new ReviewExplanationService.Write(0L,"GENERAL","说明",true))).isInstanceOf(IllegalStateException.class);
        assertThat(notes.list(tenant)).isEmpty();
    }
}
