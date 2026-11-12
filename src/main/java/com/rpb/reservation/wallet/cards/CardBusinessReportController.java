package com.rpb.reservation.wallet.cards;

import com.rpb.reservation.wallet.api.WalletApiIdentity;
import com.rpb.reservation.wallet.cards.report.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet/cards/reports")
public class CardBusinessReportController {
 private final WalletApiIdentity identity;private final CardBusinessReportService reports;
 public CardBusinessReportController(WalletApiIdentity identity,CardBusinessReportService reports){this.identity=identity;this.reports=reports;}
 @GetMapping public CardBusinessReport.Page report(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to,
  @RequestParam(defaultValue="business")String section,@RequestParam(defaultValue="")String query,@RequestParam(required=false)UUID planId,
  @RequestParam(defaultValue="all")String kind,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@RequestParam(defaultValue="plan")String groupBy){
  var actor=identity.staff();return reports.query(identity.scope(actor.tenantId(),storeId),actor,new CardBusinessReport.Filter(from,to,section,query,planId,kind,page,size,groupBy));
 }
}
