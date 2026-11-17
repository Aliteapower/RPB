package com.rpb.reservation.cards.voucher;

import java.time.*;
import java.util.*;

public final class VoucherDtos {
    private VoucherDtos() {}

    public record Write(String name, String kind, UUID productId, Integer quantity, Long valueCents,
                        int total, List<UUID> stores, Instant startsAt, Instant endsAt,
                        LocalDate validFrom, LocalDate validThrough, String rules, String image,
                        Integer version) {}
    public record Store(UUID id, String name) {}
    public record Product(UUID id, String name, String specification, String unit, long priceCents, String currency) {}
    public record Face(String name, String kind, UUID productId, String productName, String specification,
                       String unit, Integer quantity, long valueCents, String currency, List<Store> stores,
                       LocalDate validFrom, LocalDate validThrough, String timezone, String rules, String image) {}
    public record Campaign(UUID id, String activityCode, String state, int version, int total, int issued,
                           int redeemed, Instant startsAt, Instant endsAt, Face face) {}
    public record Summary(UUID id, String activityCode, String state, int version, int total, int issued,
                          int redeemed, Instant startsAt, Instant endsAt, String name, String kind) {}
    public record PublicView(String status, Face face, String code) {}
    public record ScanResult(String status, Face face, String batchCode, Instant redeemedAt,
                             Integer issueTotal, Integer redemptionNumber) {}
    public record State(String status, int version) {}
    public record Token(String code) {}
}
