package com.rpb.reservation.wallet.proof;

import com.rpb.reservation.wallet.api.WalletApiIdentity;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet/collection-proofs")
public class WalletCollectionProofController {
 private final WalletApiIdentity identity;private final WalletCollectionProofService proofs;
 public WalletCollectionProofController(WalletApiIdentity identity,WalletCollectionProofService proofs){this.identity=identity;this.proofs=proofs;}
 @GetMapping("/{type}/{id}/context") public WalletProofViews.Context context(@PathVariable UUID storeId,@PathVariable String type,@PathVariable UUID id,@RequestParam String channel){var a=identity.staff();return proofs.context(scope(storeId,a),a,type,id,channel);}
 @PostMapping(value="/{type}/{id}/scan",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public WalletProofViews.Evidence scan(@PathVariable UUID storeId,@PathVariable String type,@PathVariable UUID id,@RequestParam String channel,@RequestParam String amount,@RequestParam String idempotencyKey,@RequestPart("image") MultipartFile image){
  var a=identity.staff();var scope=scope(storeId,a);if(image.getSize()>5*1024*1024)throw WalletException.invalid();
  try{return proofs.scan(scope,a,type,id,channel,amount,idempotencyKey,image.getOriginalFilename(),image.getContentType(),image.getBytes());}catch(IOException e){throw WalletException.invalid();}
 }
 @GetMapping("/{type}/{id}/submitted") public List<WalletProofViews.Evidence> submitted(@PathVariable UUID storeId,@PathVariable String type,@PathVariable UUID id){var a=identity.staff();return proofs.submitted(scope(storeId,a),a,type,id);}
 @GetMapping("/{type}/{id}") public List<WalletProofViews.Evidence> list(@PathVariable UUID storeId,@PathVariable String type,@PathVariable UUID id){var a=identity.staff();return proofs.list(scope(storeId,a),a,type,id);}
 @GetMapping("/images/{id}") public ResponseEntity<byte[]> image(@PathVariable UUID storeId,@PathVariable UUID id){var a=identity.staff();var image=proofs.image(scope(storeId,a),a,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=receipt").body(image.bytes());}
 private StoreScope scope(UUID store,CurrentActor a){return identity.scope(a.tenantId(),store);}
}
