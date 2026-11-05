package com.rpb.reservation.workforce.documents.ocr;

import com.rpb.reservation.workforce.documents.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

/** Keeps OCR outside database transactions; each snapshot reuses the existing scoped document service. */
@Service
public class DocumentRecognitionService {
    private final EmployeeDocumentService documents;
    private final DocumentOcrEngine engine;
    private final DocumentOcrParser parser;
    public DocumentRecognitionService(EmployeeDocumentService documents,DocumentOcrEngine engine,DocumentOcrParser parser) {
        this.documents=documents;this.engine=engine;this.parser=parser;
    }
    public record Recognition(UUID documentId,UUID attachmentId,long documentVersion,String name,String number,
                              LocalDate approvedOn,LocalDate expiresOn,LocalDate prDate,List<String> warnings) {}
    public Recognition recognize(CurrentActor actor,UUID store,UUID employee,UUID doc,UUID attachment,DocumentDtos.Version command) {
        if(command==null||command.expectedVersion()==null||command.expectedVersion()<1)throw WorkforceException.invalid();
        checkVersion(actor,store,employee,doc,command.expectedVersion());
        var file=documents.download(actor,store,employee,doc,attachment);
        var fields=parser.parse(engine.recognize(file));
        checkVersion(actor,store,employee,doc,command.expectedVersion());
        var current=documents.download(actor,store,employee,doc,attachment);
        if(!Objects.equals(file.hash(),current.hash()))throw WorkforceException.conflict();
        List<String> warnings=new ArrayList<>(fields.warnings());
        if("application/pdf".equals(file.contentType()))warnings.add("PDF_FIRST_TWO_PAGES_ONLY");
        return new Recognition(doc,attachment,command.expectedVersion(),fields.name(),fields.number(),fields.approvedOn(),fields.expiresOn(),fields.prDate(),List.copyOf(warnings));
    }
    private void checkVersion(CurrentActor actor,UUID store,UUID employee,UUID doc,long expected) {
        var current=documents.list(actor,store,employee).items().stream().filter(d->d.id().equals(doc)).findFirst().orElseThrow(WorkforceException::missing);
        if(current.version()!=expected)throw WorkforceException.conflict();
    }
}
