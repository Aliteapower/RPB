package com.rpb.reservation.cards.voucher;

import com.rpb.reservation.cards.application.CardAccess;
import com.rpb.reservation.cards.application.CardException;
import com.rpb.reservation.cards.voucher.VoucherDtos.*;
import com.rpb.reservation.cards.voucher.VoucherRepository.Row;
import com.rpb.reservation.catalog.application.ProductCatalogQuery;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.WalletAccess;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherService {
    private final VoucherRepository repository;
    private final CardAccess cards;
    private final ProductCatalogQuery catalog;
    private final Clock clock;
    private final WalletAccess wallet;

    public VoucherService(VoucherRepository repository, CardAccess cards, ProductCatalogQuery catalog,
                          Clock clock, WalletAccess wallet) {
        this.repository = repository;
        this.cards = cards;
        this.catalog = catalog;
        this.clock = clock;
        this.wallet = wallet;
    }

    public List<Summary> list(StoreScope scope, CurrentActor actor) {
        manage(scope, actor, false);
        return repository.list(scope);
    }

    public ReportPage report(StoreScope scope, CurrentActor actor, String query, String kind, int page) {
        manage(scope, actor, false);
        if (query.length() > 120 || !Set.of("", "cash", "product").contains(kind) || page < 0) {
            throw invalid("活动券报表筛选条件无效");
        }
        return repository.report(scope, query.strip(), kind, page, clock.instant());
    }

    public Campaign detail(StoreScope scope, CurrentActor actor, UUID id) {
        manage(scope, actor, false);
        return owned(scope, id, false).campaign();
    }

    @Transactional
    public Campaign save(StoreScope scope, CurrentActor actor, UUID id, Write input) {
        manage(scope, actor, true);
        validate(input);
        var row = id == null ? null : owned(scope, id, true);
        if (row != null) {
            var current = row.campaign();
            requireVersion(current, input.version());
            if (current.issued() > input.total()) throw conflict("发行量不能低于已领取数量");
            if (current.issued() > 0 && !current.face().kind().equals(input.kind())) {
                throw conflict("已发券的活动不能更改券类型");
            }
        }
        var face = face(scope, actor, input);
        if (row == null) {
            id = UUID.randomUUID();
            repository.create(scope, id, input, face);
        } else {
            repository.update(row, input, face);
        }
        return owned(scope, id, false).campaign();
    }

    @Transactional
    public Campaign state(StoreScope scope, CurrentActor actor, UUID id, String state, int version) {
        manage(scope, actor, "active".equals(state));
        var campaign = owned(scope, id, true).campaign();
        requireVersion(campaign, version);
        if (state == null || !Set.of("active","disabled","archived").contains(state)) {
            throw invalid("活动状态无效");
        }
        if ("active".equals(state)) {
            if (!clock.instant().isBefore(campaign.endsAt())) throw conflict("请先更新活动结束时间");
            verifyStores(scope, actor, campaign.face().stores().stream().map(Store::id).toList(),
                campaign.face().productId());
        }
        repository.state(id, state);
        return owned(scope, id, false).campaign();
    }

    @Transactional
    public void delete(StoreScope scope, CurrentActor actor, UUID id, int version) {
        manage(scope, actor, false);
        var campaign = owned(scope, id, true).campaign();
        if (campaign.version() != version) throw conflict("活动已更新，请刷新");
        if (campaign.issued() > 0) throw conflict("已发券的活动可停用或归档");
        repository.delete(id);
    }

    public PublicView view(UUID id, String ip, String receipt) {
        var row = repository.find(id, false);
        var prior = restore(id, receipt);
        return prior.orElseGet(() -> new PublicView(availability(row, clock.instant()),
            row.campaign().face(), null, row.campaign().activityCode()));
    }

    @Transactional
    public PublicView claim(UUID id, String ip, String receipt) {
        if (!validReceipt(receipt)) throw invalid("领取凭证无效");
        var row = repository.find(id, true);
        var prior = restore(id, receipt);
        if (prior.isPresent()) return prior.get();
        var now = clock.instant();
        var status = availability(row, now);
        var face = row.campaign().face();
        var batchCode = row.campaign().activityCode();
        if (!"open".equals(status)) return new PublicView(status, face, null, batchCode);
        String code = code(id, receipt);
        repository.claim(row, ip, hash("receipt:" + receipt), hash(code), now);
        return new PublicView("claimed", face, code, batchCode);
    }

    @Transactional
    public ScanResult redeem(StoreScope scope, CurrentActor actor, String code) {
        requireAdmin(actor);
        cards.staff(scope, actor, "wallet.cards.redeem", false);
        if (code == null || !code.matches("VC\\.[0-9a-f]{64}")) throw missing();
        String hash = hash(code);
        var claim = repository.lockClaim(scope.tenantId().value(), hash);
        UUID store = scope.storeId().value();
        if (claim.face().stores().stream().noneMatch(s -> s.id().equals(store))) throw CardException.forbidden();
        if (claim.redeemedAt() != null) return new ScanResult("used", claim.face(), claim.batchCode(),
            claim.redeemedAt(), claim.issueTotal(), claim.redemptionNumber());
        Instant now = clock.instant();
        var date = now.atZone(ZoneId.of(claim.face().timezone())).toLocalDate();
        if (date.isBefore(claim.face().validFrom())) return new ScanResult("too_early", claim.face(), claim.batchCode(), null, null, null);
        if (date.isAfter(claim.face().validThrough())) return new ScanResult("expired", claim.face(), claim.batchCode(), null, null, null);
        cards.staff(scope, actor, "wallet.cards.redeem", true);
        Instant redeemedAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var redemption = repository.redeem(claim.campaign(), hash, store, actor.actorId(), redeemedAt);
        return new ScanResult("redeemed", claim.face(), claim.batchCode(), redeemedAt,
            redemption.issueTotal(), redemption.number());
    }

    private Optional<PublicView> restore(UUID id, String receipt) {
        if (!validReceipt(receipt)) return Optional.empty();
        return repository.receipt(id, hash("receipt:" + receipt)).map(claim ->
            new PublicView(claim.redeemedAt() == null ? "claimed" : "used", claim.face(),
                claim.redeemedAt() == null ? code(id, receipt) : null, claim.batchCode()));
    }

    private String availability(Row row, Instant now) {
        var c = row.campaign();
        if (!"active".equals(c.state())) return "disabled";
        if (now.isBefore(c.startsAt())) return "upcoming";
        if (!now.isBefore(c.endsAt())) return "ended";
        var scope = new StoreScope(new TenantId(row.tenant()), row.owner());
        if (!wallet.commercial(scope).allowed() || !cards.settingsEnabled(scope)) return "disabled";
        return c.issued() >= c.total() ? "sold_out" : "open";
    }

    private Face face(StoreScope scope, CurrentActor actor, Write input) {
        var owner = repository.store(scope);
        var stores = verifyStores(scope, actor, input.stores(), input.productId());
        Product product = null;
        if ("product".equals(input.kind())) {
            var pricingScope = input.stores().contains(scope.storeId().value()) ? scope
                : new StoreScope(scope.tenantId(), input.stores().getFirst());
            var p = catalog.requireUsable(pricingScope, input.productId(), "cards");
            product = new Product(p.id(), p.displayName("zh"), p.descriptionZh(), p.unit(), p.priceCents(), p.currency());
        }
        long value = input.valueCents() != null ? input.valueCents() : Math.multiplyExact(product.priceCents(), input.quantity());
        if (value < 1 || value > 100_000_000L) throw invalid("券金额超出范围");
        return new Face(input.name().strip(), input.kind(), input.productId(), product == null ? null : product.name(),
            product == null ? null : product.specification(), product == null ? null : product.unit(),
            input.quantity(), value, owner.currency(), stores, input.validFrom(), input.validThrough(), owner.timezone(),
            input.rules() == null ? "" : input.rules(), VoucherImages.validate(input.image()));
    }

    private List<Store> verifyStores(StoreScope scope, CurrentActor actor, List<UUID> ids, UUID product) {
        String currency = repository.store(scope).currency();
        var stores = new ArrayList<Store>();
        for (UUID id : ids) {
            var target = new StoreScope(scope.tenantId(), id);
            requireCampaignAccess(target, actor, true);
            var info = repository.store(target);
            if (!currency.equals(info.currency())) throw invalid("适用门店须使用同一币种");
            if (product != null) catalog.requireUsable(target, product, "cards");
            stores.add(info.store());
        }
        return stores;
    }

    private Row owned(StoreScope scope, UUID id, boolean lock) {
        var row = repository.find(id, lock);
        if (!row.tenant().equals(scope.tenantId().value()) || !row.owner().equals(scope.storeId().value())) throw missing();
        return row;
    }

    private void manage(StoreScope scope, CurrentActor actor, boolean mutate) {
        requireAdmin(actor);
        requireCampaignAccess(scope, actor, mutate);
    }

    private void requireCampaignAccess(StoreScope scope, CurrentActor actor, boolean mutate) {
        try {
            cards.staff(scope, actor, "wallet.cards.manage", mutate);
        } catch (CardException e) {
            if (!"CARD_DISABLED".equals(e.code())) throw e;
            String name = repository.store(scope).store().name();
            throw new CardException(e.code(), e.status(), "门店「" + name + "」未启用卡功能，请切换到该店后在「持卡与核销」顶部启用");
        }
    }

    private static void requireAdmin(CurrentActor actor) {
        if (actor == null || !"tenant_admin".equals(actor.actorType())) throw CardException.forbidden();
    }

    private static void requireVersion(Campaign current, Integer version) {
        if (version == null || current.version() != version) throw conflict("活动已更新，请刷新");
        if ("archived".equals(current.state())) throw conflict("活动已归档");
    }

    private static void validate(Write input) {
        if (input == null || input.name() == null || input.name().isBlank() || input.name().strip().length() > 120
                || input.kind() == null || !Set.of("cash","product").contains(input.kind())
                || input.total() < 1 || input.total() > 1_000_000 || input.stores() == null
                || input.stores().isEmpty() || input.stores().size() > 200 || input.stores().stream().anyMatch(Objects::isNull)
                || new HashSet<>(input.stores()).size() != input.stores().size()
                || input.startsAt() == null || input.endsAt() == null || !input.startsAt().isBefore(input.endsAt())
                || input.validFrom() == null || input.validThrough() == null || input.validFrom().isAfter(input.validThrough())
                || input.rules() != null && input.rules().length() > 4000) throw invalid("请检查活动名称、日期、数量和门店");
        if ("product".equals(input.kind())) {
            if (input.productId() == null || input.quantity() == null || input.quantity() < 1 || input.quantity() > 10_000) {
                throw invalid("请选择兑换商品并填写兑换数量");
            }
        } else if (input.productId() != null || input.quantity() != null || input.valueCents() == null) {
            throw invalid("请填写代金券面额");
        }
    }

    private static boolean validReceipt(String value) {
        return value != null && value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }

    private static String code(UUID id, String receipt) {
        return "VC." + hash("voucher:" + id + ":" + receipt);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static CardException missing() { return new CardException("VOUCHER_NOT_FOUND",404,"活动或券码不存在"); }
    static CardException invalid(String message) { return new CardException("VOUCHER_INVALID",400,message); }
    static CardException conflict(String message) { return new CardException("VOUCHER_CONFLICT",409,message); }
}
