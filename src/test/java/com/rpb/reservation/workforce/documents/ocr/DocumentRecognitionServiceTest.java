package com.rpb.reservation.workforce.documents.ocr;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rpb.reservation.workforce.documents.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.Test;

class DocumentRecognitionServiceTest {
    final EmployeeDocumentService documents=mock(EmployeeDocumentService.class);
    final DocumentOcrEngine engine=mock(DocumentOcrEngine.class);
    final DocumentRecognitionService service=new DocumentRecognitionService(documents,engine,new DocumentOcrParser());
    final UUID store=UUID.randomUUID(),employee=UUID.randomUUID(),doc=UUID.randomUUID(),file=UUID.randomUUID();
    final CurrentActor actor=new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of(store));
    DocumentDtos.Page page(long version){return new DocumentDtos.Page(List.of(),List.of(new DocumentDtos.Document(doc,UUID.randomUUID(),"Passport","",null,"",version,List.of())));}
    void ready(){when(documents.list(actor,store,employee)).thenReturn(page(3));when(documents.download(actor,store,employee,doc,file)).thenReturn(new DocumentDtos.FileData(file,"x.png","image/png",new byte[]{1},"hash",false));when(engine.recognize(any())).thenReturn("Name: TAN");}
    @Test void authorizationFailureNeverStartsOcr(){when(documents.list(actor,store,employee)).thenThrow(WorkforceException.forbidden());assertThatThrownBy(()->service.recognize(actor,store,employee,doc,file,new DocumentDtos.Version(3L))).isInstanceOf(WorkforceException.class);verifyNoInteractions(engine);}
    @Test void staleBeforeRecognitionNeverStartsOcr(){ready();assertThatThrownBy(()->service.recognize(actor,store,employee,doc,file,new DocumentDtos.Version(2L))).isInstanceOf(WorkforceException.class);verifyNoInteractions(engine);}
    @Test void successfulRecognitionChecksAuthorizationAndVersionTwiceWithoutWriting(){ready();assertThat(service.recognize(actor,store,employee,doc,file,new DocumentDtos.Version(3L)).name()).isEqualTo("TAN");verify(documents,times(2)).list(actor,store,employee);verify(documents,times(2)).download(actor,store,employee,doc,file);verifyNoMoreInteractions(documents);}
    @Test void concurrentEditRejectsResult(){ready();when(documents.list(actor,store,employee)).thenReturn(page(3),page(4));assertThatThrownBy(()->service.recognize(actor,store,employee,doc,file,new DocumentDtos.Version(3L))).isInstanceOf(WorkforceException.class);}
    @Test void removedAttachmentRejectsResult(){ready();when(documents.download(actor,store,employee,doc,file)).thenReturn(new DocumentDtos.FileData(file,"x.png","image/png",new byte[]{1},"hash",false)).thenThrow(WorkforceException.missing());assertThatThrownBy(()->service.recognize(actor,store,employee,doc,file,new DocumentDtos.Version(3L))).isInstanceOf(WorkforceException.class);}
    @Test void missingAttachmentNeverStartsOcr(){ready();when(documents.download(actor,store,employee,doc,file)).thenThrow(WorkforceException.missing());assertThatThrownBy(()->service.recognize(actor,store,employee,doc,file,new DocumentDtos.Version(3L))).isInstanceOf(WorkforceException.class);verifyNoInteractions(engine);}
    @Test void revokedPermissionAfterRecognitionDoesNotReleaseCandidates(){ready();when(documents.list(actor,store,employee)).thenReturn(page(3)).thenThrow(WorkforceException.forbidden());assertThatThrownBy(()->service.recognize(actor,store,employee,doc,file,new DocumentDtos.Version(3L))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_FORBIDDEN");}
    @Test void pdfResponseExplicitlyWarnsThatOnlyFirstTwoPagesAreRead(){ready();when(documents.download(actor,store,employee,doc,file)).thenReturn(new DocumentDtos.FileData(file,"x.pdf","application/pdf",new byte[]{1},"hash",false));assertThat(service.recognize(actor,store,employee,doc,file,new DocumentDtos.Version(3L)).warnings()).contains("PDF_FIRST_TWO_PAGES_ONLY");}
    @Test void recognitionFailureDoesNotWriteOrDeleteAttachment(){ready();when(engine.recognize(any())).thenThrow(new WorkforceException("WORKFORCE_DOCUMENT_OCR_UNAVAILABLE",503));assertThatThrownBy(()->service.recognize(actor,store,employee,doc,file,new DocumentDtos.Version(3L))).isInstanceOf(WorkforceException.class);verify(documents).list(actor,store,employee);verify(documents).download(actor,store,employee,doc,file);verifyNoMoreInteractions(documents);}
}
