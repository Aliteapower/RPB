package com.rpb.reservation.wallet.cards;
import com.rpb.reservation.wallet.api.WalletApiIdentity;
import com.rpb.reservation.cards.history.CardReportService;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet/cards/summary")
public class CardReportController {
 private final WalletApiIdentity identity;private final CardReportService reports;
 public CardReportController(WalletApiIdentity identity,CardReportService reports){this.identity=identity;this.reports=reports;}
 @GetMapping public Object report(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to){var a=identity.staff();return reports.query(identity.scope(a.tenantId(),storeId),a,from,to);}
}
