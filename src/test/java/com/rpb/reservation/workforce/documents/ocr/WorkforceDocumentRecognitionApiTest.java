package com.rpb.reservation.workforce.documents.ocr;

import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.workforce.api.WorkforceDocumentRecognitionController;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkforceDocumentRecognitionApiTest {
    final CurrentActorProvider actors=mock(CurrentActorProvider.class);
    final DocumentRecognitionService service=mock(DocumentRecognitionService.class);
    final MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceDocumentRecognitionController(actors,service)).build();
    final String path="/api/v1/stores/"+UUID.randomUUID()+"/workforce/employees/"+UUID.randomUUID()+"/documents/"+UUID.randomUUID()+"/attachments/"+UUID.randomUUID()+"/recognition";
    @Test void requiresSameOriginProtectionBeforeService()throws Exception {
        mvc.perform(put(path).contentType("application/json").content("{\"expectedVersion\":3}")).andExpect(status().isForbidden()).andExpect(header().string("Cache-Control","private, no-store"));
        mvc.perform(put(path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{\"expectedVersion\":3}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void returnsStableErrorsAndNeverRawText()throws Exception {
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
        for(var entry:Map.of("WORKFORCE_DOCUMENT_OCR_UNAVAILABLE",503,"WORKFORCE_DOCUMENT_OCR_BUSY",429,"WORKFORCE_DOCUMENT_OCR_UNSUPPORTED",400).entrySet()){
            doThrow(new WorkforceException(entry.getKey(),entry.getValue())).when(service).recognize(any(),any(),any(),any(),any(),any());
            mvc.perform(put(path).header("X-RPB-Workforce","1").contentType("application/json").content("{\"expectedVersion\":3}"))
                .andExpect(status().is(entry.getValue())).andExpect(jsonPath("$.code").value(entry.getKey())).andExpect(jsonPath("$.rawText").doesNotExist()).andExpect(header().string("Cache-Control","private, no-store"));
        }
    }
}
