package com.rpb.reservation.workforce.api;

import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.documents.DocumentDtos.Version;
import com.rpb.reservation.workforce.documents.ocr.DocumentRecognitionService;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceDocumentRecognitionController extends WorkforceEndpointSupport {
    private final DocumentRecognitionService service;
    public WorkforceDocumentRecognitionController(CurrentActorProvider actors,DocumentRecognitionService service){super(actors);this.service=service;}
    @PutMapping("/employees/{employee}/documents/{id}/attachments/{fileId}/recognition")
    public DocumentRecognitionService.Recognition recognize(@PathVariable UUID storeId,@PathVariable UUID employee,
            @PathVariable UUID id,@PathVariable UUID fileId,@RequestBody Version command) {
        return service.recognize(actor(),storeId,employee,id,fileId,command);
    }
}
