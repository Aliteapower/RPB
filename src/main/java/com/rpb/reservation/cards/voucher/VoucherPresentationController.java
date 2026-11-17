package com.rpb.reservation.cards.voucher;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/voucher-campaigns/{id}")
public class VoucherPresentationController {
    private final VoucherPresentationService service;
    public VoucherPresentationController(VoucherPresentationService service) {
        this.service=service;
    }
    @GetMapping("/presentation")
    public ResponseEntity<VoucherPresentationService.Presentation> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
    }
    @GetMapping("/logo")
    public ResponseEntity<Resource> logo(@PathVariable UUID id) {
        var logo=service.logo(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(logo.contentType())).contentLength(logo.byteSize())
            .header("X-Content-Type-Options","nosniff").body(logo.resource());
    }
}
