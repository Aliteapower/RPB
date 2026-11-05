package com.rpb.reservation.workforce.api;
import com.rpb.reservation.workforce.documents.*;import static com.rpb.reservation.workforce.documents.DocumentDtos.*;
import com.rpb.reservation.walkin.api.CurrentActorProvider;import java.util.*;import java.io.IOException;import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;import org.springframework.http.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceDocumentController extends WorkforceEndpointSupport {
 private final EmployeeDocumentService service;public WorkforceDocumentController(CurrentActorProvider actors,EmployeeDocumentService service){super(actors);this.service=service;}
 @GetMapping("/document-types") public List<Type> types(@PathVariable UUID storeId){return service.types(actor(),storeId);}
 @PutMapping("/document-types/{id}") public Type type(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody TypeWrite c){return service.type(actor(),storeId,id,c);}
 @GetMapping("/employees/{employee}/documents") public Page list(@PathVariable UUID storeId,@PathVariable UUID employee){return service.list(actor(),storeId,employee);}
 @PutMapping("/employees/{employee}/documents/{id}") public Document save(@PathVariable UUID storeId,@PathVariable UUID employee,@PathVariable UUID id,@RequestBody Write c){return service.save(actor(),storeId,employee,id,c);}
 @PutMapping(value="/employees/{employee}/documents/{id}/attachments/{fileId}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Document upload(@PathVariable UUID storeId,@PathVariable UUID employee,@PathVariable UUID id,@PathVariable UUID fileId,@RequestParam long expectedVersion,@RequestPart("file")MultipartFile file)throws IOException{if(file.getSize()>5*1024*1024)throw new com.rpb.reservation.workforce.domain.WorkforceException("WORKFORCE_DOCUMENT_FILE_TOO_LARGE",400);return service.upload(actor(),storeId,employee,id,fileId,expectedVersion,file.getOriginalFilename(),file.getBytes());}
 @GetMapping("/employees/{employee}/documents/{id}/attachments/{fileId}") public ResponseEntity<byte[]> download(@PathVariable UUID storeId,@PathVariable UUID employee,@PathVariable UUID id,@PathVariable UUID fileId){var file=service.download(actor(),storeId,employee,id,fileId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString()).body(file.bytes());}
 @PutMapping("/employees/{employee}/documents/{id}/attachments/{fileId}/deletion") public Document delete(@PathVariable UUID storeId,@PathVariable UUID employee,@PathVariable UUID id,@PathVariable UUID fileId,@RequestBody Version c){return service.deleteFile(actor(),storeId,employee,id,fileId,c);}
}
