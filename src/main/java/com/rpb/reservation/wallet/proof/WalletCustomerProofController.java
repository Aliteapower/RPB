package com.rpb.reservation.wallet.proof;

import com.rpb.reservation.wallet.api.WalletApiIdentity;
import com.rpb.reservation.wallet.domain.WalletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/public/stores/{storeId}/wallet/collection-proofs")
public class WalletCustomerProofController {
 private final WalletApiIdentity identity;private final WalletCustomerProofService proofs;
 public WalletCustomerProofController(WalletApiIdentity identity,WalletCustomerProofService proofs){this.identity=identity;this.proofs=proofs;}
 @GetMapping("/{type}/{id}/context") public WalletProofViews.Context context(@PathVariable UUID storeId,@PathVariable String type,@PathVariable UUID id,@RequestParam String channel,HttpServletRequest request){var p=identity.customer(request);return proofs.context(identity.scope(p.tenantId(),storeId),p,type,id,channel);}
 @GetMapping("/{type}/{id}/paynow") public Map<String,String> paynow(@PathVariable UUID storeId,@PathVariable String type,@PathVariable UUID id,@RequestParam String channel,HttpServletRequest request){var p=identity.customer(request);return proofs.paynow(identity.scope(p.tenantId(),storeId),p,type,id,channel);}
 @PostMapping(value="/{type}/{id}/submit",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public WalletCustomerProofService.CustomerEvidence submit(@PathVariable UUID storeId,@PathVariable String type,@PathVariable UUID id,@RequestParam Map<String,String> fields,@RequestPart("image") MultipartFile image,HttpServletRequest request){
  var p=identity.customer(request);
  if(!fields.keySet().equals(Set.of("channel","amount","idempotencyKey"))||image.getSize()>5*1024*1024)throw WalletException.invalid();
  try{return proofs.submit(identity.scope(p.tenantId(),storeId),p,type,id,fields.get("channel"),fields.get("amount"),fields.get("idempotencyKey"),image.getOriginalFilename(),image.getContentType(),image.getBytes());}catch(IOException e){throw WalletException.invalid();}
 }
 @GetMapping("/{type}/{id}") public List<WalletCustomerProofService.CustomerEvidence> list(@PathVariable UUID storeId,@PathVariable String type,@PathVariable UUID id,HttpServletRequest request){var p=identity.customer(request);return proofs.list(identity.scope(p.tenantId(),storeId),p,type,id);}
 @GetMapping("/images/{id}") public ResponseEntity<byte[]> image(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest request){var p=identity.customer(request);var image=proofs.image(identity.scope(p.tenantId(),storeId),p,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=receipt").body(image.bytes());}
}
