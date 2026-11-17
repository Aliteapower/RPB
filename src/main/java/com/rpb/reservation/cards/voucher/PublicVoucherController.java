package com.rpb.reservation.cards.voucher;

import com.rpb.reservation.cards.voucher.VoucherDtos.PublicView;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/voucher-campaigns/{id}")
public class PublicVoucherController {
    private final VoucherService vouchers;
    private final Set<String> proxies;

    public PublicVoucherController(VoucherService vouchers, @Value("${rpb.vouchers.trusted-proxies:}") String proxies) {
        this.vouchers = vouchers;
        this.proxies = Arrays.stream(proxies.split(",")).map(String::strip).filter(s -> !s.isEmpty())
            .map(PublicVoucherController::ip).collect(Collectors.toUnmodifiableSet());
    }

    @GetMapping
    public ResponseEntity<PublicView> view(@PathVariable UUID id,
            @RequestHeader(name="X-Voucher-Receipt", required=false) String receipt, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vouchers.view(id, sourceIp(request), receipt));
    }

    @PostMapping("/claims")
    public ResponseEntity<PublicView> claim(@PathVariable UUID id, @RequestHeader("X-Voucher-Receipt") String receipt,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vouchers.claim(id, sourceIp(request), receipt));
    }

    private String sourceIp(HttpServletRequest request) {
        String peer = ip(request.getRemoteAddr());
        return proxies.contains(peer) ? ip(request.getHeader("X-Real-IP")) : peer;
    }

    static String ip(String raw) {
        if (raw == null || raw.length() > 45 || !raw.matches("[0-9a-fA-F:.]+") ||
                (!raw.contains(":") && !raw.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))) {
            throw VoucherService.invalid("无法识别来源 IP");
        }
        try {
            return InetAddress.getByName(raw).getHostAddress();
        } catch (UnknownHostException e) {
            throw VoucherService.invalid("无法识别来源 IP");
        }
    }
}
