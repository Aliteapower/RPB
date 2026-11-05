package com.rpb.reservation.wallet.transactions;

import java.time.LocalDate;
import java.util.List;

/** Completed activity only; monetary rows are cents and points rows are quantities. */
public record WalletBusinessReport(String period,LocalDate startDate,LocalDate endDate,String timezone,String currency,List<Row> rows) {
    public record Row(String bucket,String metric,String channel,String channelName,long amount,long count) {}
}
