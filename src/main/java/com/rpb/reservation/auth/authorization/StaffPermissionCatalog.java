package com.rpb.reservation.auth.authorization;

import java.util.*;
import java.util.stream.Collectors;

/** Employee-delegable capabilities only. Identity and hard role-only settings stay outside this catalogue. */
public final class StaffPermissionCatalog {
    public record Permission(String code, String labelZh, String labelEn, List<String> requires) {}
    public record Group(String key, String appKey, String labelZh, String labelEn, List<Permission> permissions) {}

    private static Permission p(String code, String zh, String en, String... requires) {
        return new Permission(code, zh, en, List.of(requires));
    }
    private static Group g(String key, String app, String zh, String en, Permission... permissions) {
        return new Group(key, app, zh, en, List.of(permissions));
    }
    private static final List<Group> GROUPS = List.of(
        g("reservation", "reservation_queue", "预约", "Reservations",
            p("reservation.today_view", "查看预约", "View reservations"),
            p("reservation.create", "新增预约", "Create reservations", "reservation.today_view", "customer.lookup"),
            p("reservation.check_in", "到店登记", "Check in", "reservation.today_view"),
            p("reservation.seat", "安排入座", "Seat reservations", "reservation.today_view", "table.view"),
            p("reservation.queue", "转入排队", "Queue reservations", "reservation.today_view", "queue.view"),
            p("reservation.cancel", "取消预约", "Cancel reservations", "reservation.today_view"),
            p("reservation.no_show", "标记未到店", "Mark no-show", "reservation.today_view"),
            p("reservation.complete", "完成预约", "Complete reservations", "reservation.today_view"),
            p("customer.lookup", "查找顾客", "Look up customers")),
        g("queue", "reservation_queue", "排队、桌台与清台", "Queue, tables and cleaning",
            p("queue.view", "查看排队", "View queue"),
            p("queue.call", "叫号", "Call queue", "queue.view"),
            p("queue.seat", "排队入座", "Seat queue", "queue.view", "table.view"),
            p("queue.skip", "过号", "Skip queue", "queue.view"),
            p("queue.rejoin", "重新排队", "Rejoin queue", "queue.view"),
            p("queue.cancel", "取消排队", "Cancel queue", "queue.view"),
            p("queue.display.view", "查看叫号屏", "View call display", "queue.view"),
            p("table.view", "查看桌台", "View tables"),
            p("table.switch", "换桌", "Switch tables", "table.view"),
            p("walkin.direct_seating.create", "散客直接入座", "Seat walk-ins", "table.view", "customer.lookup"),
            p("walkin.queue.create", "散客排队", "Queue walk-ins", "queue.view", "customer.lookup"),
            p("cleaning.start", "开始清台", "Start cleaning", "table.view"),
            p("cleaning.complete", "完成清台", "Complete cleaning", "table.view")),
        g("payment", "payment", "PayNow 收款", "PayNow payments",
            p("payment.intent.view", "查看收款记录", "View payments"),
            p("payment.intent.create", "建立及手动确认收款", "Create and manually confirm payments", "payment.intent.view"),
            p("payment.proof.review", "审核付款凭证", "Review payment proofs", "payment.intent.view")),
        g("wallet", "wallet", "钱包", "Wallet",
            p("wallet.view", "查看钱包和交易", "View wallet and transactions"),
            p("wallet.topup.create", "新增充值", "Create top-ups", "wallet.view"),
            p("wallet.charge.create", "发起扣款", "Create charges", "wallet.view"),
            p("wallet.collection.confirm", "确认收款与充值", "Confirm collections and top-ups", "wallet.view"),
            p("wallet.refund.manage", "退款与异常处理", "Manage refunds and exceptions", "wallet.view"),
            p("wallet.reconcile.view", "查看报表与对账", "View reports and reconciliation", "wallet.view")),
        g("points", "wallet", "积分与营销", "Points and marketing",
            p("wallet.points.view", "查看积分", "View points"),
            p("wallet.points.grant", "发放积分", "Grant points", "wallet.points.view"),
            p("wallet.points.redeem", "兑换积分", "Redeem points", "wallet.points.view"),
            p("wallet.points.adjust", "调整积分", "Adjust points", "wallet.points.view"),
            p("wallet.points.reverse", "冲正积分", "Reverse points", "wallet.points.view"),
            p("wallet.marketing.view", "查看营销", "View marketing", "wallet.view"),
            p("wallet.marketing.manage", "管理营销", "Manage marketing", "wallet.marketing.view")),
        g("cards", "wallet", "卡券", "Cards",
            p("catalog.view", "查看卡券可选商品", "View products for card plans"),
            p("wallet.cards.view", "查看卡券", "View cards", "wallet.view"),
            p("wallet.cards.manage", "管理卡券方案", "Manage card plans", "wallet.cards.view", "catalog.view"),
            p("wallet.cards.sell", "售卡与确认付款", "Sell cards and confirm payment", "wallet.cards.view"),
            p("wallet.cards.stamp", "盖章", "Stamp cards", "wallet.cards.view"),
            p("wallet.cards.redeem", "核销卡券", "Redeem cards", "wallet.cards.view"),
            p("wallet.cards.reverse", "冲正卡券", "Reverse card operations", "wallet.cards.view"),
            p("wallet.cards.refund", "卡券退款", "Refund cards", "wallet.cards.view")),
        g("wine_storage", "wine_storage", "存酒", "Bottle storage",
            p("wine_storage.view", "查看存酒和记录", "View stored bottles and records"),
            p("wine_storage.receive", "登记与接收存酒", "Register and receive bottles", "wine_storage.view"),
            p("wine_storage.deliver", "领取与交付存酒", "Collect and deliver bottles", "wine_storage.view"),
            p("wine_storage.correct", "更正存酒记录", "Correct storage records", "wine_storage.view")),
        g("daily_ledger", "daily_ledger", "每日账本", "Daily ledger",
            p("daily_ledger.view", "查看账本", "View ledger"),
            p("daily_ledger.write", "填写账本", "Write ledger", "daily_ledger.view"),
            p("daily_ledger.settings.manage", "管理账本设置", "Manage ledger settings", "daily_ledger.view"))
    );
    private static final Map<String, Permission> PERMISSIONS = GROUPS.stream().flatMap(g -> g.permissions().stream())
        .collect(Collectors.toUnmodifiableMap(Permission::code, p -> p));

    private StaffPermissionCatalog() {}
    public static List<Group> groups() { return GROUPS; }
    public static Set<String> permissions() { return PERMISSIONS.keySet(); }
    public static Set<String> expand(Set<String> selected) {
        if (selected == null || !permissions().containsAll(selected)) throw new IllegalArgumentException("unknown_staff_permission");
        Set<String> expanded = new LinkedHashSet<>(selected);
        boolean changed;
        do {
            changed = false;
            for (String code : List.copyOf(expanded)) changed |= expanded.addAll(PERMISSIONS.get(code).requires());
        } while (changed);
        return Set.copyOf(expanded);
    }
}
