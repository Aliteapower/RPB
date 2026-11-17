package com.rpb.reservation.cards.voucher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.cards.voucher.VoucherDtos.*;
import com.rpb.reservation.common.scope.StoreScope;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VoucherRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public VoucherRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    record Row(UUID tenant, UUID owner, int revision, Campaign campaign) {}
    record Claimed(UUID campaign, Face face, Instant redeemedAt, String batchCode,
                   Integer issueTotal, Integer redemptionNumber) {}
    record Redemption(int issueTotal, int number) {}
    record StoreInfo(Store store, String currency, String timezone) {}

    StoreInfo store(StoreScope scope) {
        return jdbc.query("""
            select id, display_name, currency, timezone from stores
            where tenant_id=? and id=? and status='active'
            """, (r, n) -> new StoreInfo(new Store(r.getObject(1, UUID.class), r.getString(2)),
                    r.getString(3), r.getString(4)), scope.tenantId().value(), scope.storeId().value())
            .stream().findFirst().orElseThrow(VoucherService::missing);
    }

    List<Summary> list(StoreScope scope) {
        return jdbc.query("""
            select c.*, r.face->>'name' as name, r.face->>'kind' as kind
            from wallet_voucher_campaigns c join wallet_voucher_revisions r
            on r.campaign_id=c.id and r.revision=c.revision
            where c.tenant_id=? and c.owner_store_id=? order by c.created_at desc
            """, (r, n) -> new Summary(r.getObject("id", UUID.class), activityCode(r), r.getString("status"),
                r.getInt("version"), r.getInt("total"), r.getInt("issued"), r.getInt("redeemed"),
                instant(r,"starts_at"), instant(r,"ends_at"), r.getString("name"), r.getString("kind")),
            scope.tenantId().value(), scope.storeId().value());
    }

    ReportPage report(StoreScope scope, String query, String kind, int page, Instant now) {
        int size = 20;
        var rows = jdbc.query("""
            with campaigns as (
                select c.id, c.tenant_id, c.activity_code, c.total, c.issued, c.redeemed, c.created_at,
                       r.face->>'name' as name, r.face->>'kind' as kind
                from wallet_voucher_campaigns c join wallet_voucher_revisions r
                    on r.campaign_id=c.id and r.revision=c.revision
                where c.tenant_id=? and c.owner_store_id=?
                    and (position(lower(?) in lower(r.face->>'name')) > 0 or lpad(c.activity_code::text,4,'0')=?)
                    and (?='' or r.face->>'kind'=?)
                order by c.created_at desc, c.id desc
                limit ? offset ?
            )
            select c.*, expiry.expired
            from campaigns c
            cross join lateral (
                select count(*) as expired
                from wallet_voucher_claims x join wallet_voucher_revisions r
                    on r.campaign_id=x.campaign_id and r.revision=x.revision
                where x.tenant_id=c.tenant_id and x.campaign_id=c.id and x.redeemed_at is null
                    and (r.face->>'validThrough')::date < (cast(? as timestamptz) at time zone (r.face->>'timezone'))::date
            ) expiry
            order by c.created_at desc, c.id desc
            """, (r,n) -> new ReportRow(r.getObject("id", UUID.class), activityCode(r), r.getString("name"),
                r.getString("kind"), r.getInt("total"), r.getInt("issued"), r.getInt("redeemed"), r.getInt("expired")),
            scope.tenantId().value(), scope.storeId().value(), query, query, kind, kind, size+1,
            (long) page * size, now.atOffset(ZoneOffset.UTC));
        return new ReportPage(List.copyOf(rows.subList(0, Math.min(rows.size(), size))), rows.size() > size);
    }

    Row find(UUID id, boolean lock) {
        if (lock) jdbc.queryForList("select id from wallet_voucher_campaigns where id=? for update", UUID.class, id);
        return jdbc.query("""
            select c.*, r.face from wallet_voucher_campaigns c join wallet_voucher_revisions r
            on r.campaign_id=c.id and r.revision=c.revision where c.id=?
            """, (r,n) -> {
                var campaign = new Campaign(id, activityCode(r), r.getString("status"), r.getInt("version"),
                    r.getInt("total"), r.getInt("issued"), r.getInt("redeemed"), instant(r,"starts_at"),
                    instant(r,"ends_at"), face(r));
                return new Row(r.getObject("tenant_id",UUID.class), r.getObject("owner_store_id",UUID.class),
                    r.getInt("revision"), campaign);
            }, id).stream().findFirst().orElseThrow(VoucherService::missing);
    }

    void create(StoreScope scope, UUID id, Write input, Face face) {
        UUID tenant = scope.tenantId().value();
        int start = java.util.concurrent.ThreadLocalRandom.current().nextInt(10000);
        int inserted = jdbc.update("""
            insert into wallet_voucher_campaigns(id, tenant_id, owner_store_id, activity_code,
                status, total, starts_at, ends_at)
            select ?, ?, ?, candidate, 'draft', ?, ?, ?
            from (select (generate_series(0,9999) + ?) % 10000 as candidate) codes
            where not exists (select 1 from wallet_voucher_campaigns where tenant_id=? and activity_code=candidate)
            limit 1 on conflict (tenant_id, activity_code) do nothing
            """, id, tenant, scope.storeId().value(), input.total(), Timestamp.from(input.startsAt()),
            Timestamp.from(input.endsAt()), start, tenant);
        if (inserted == 0) throw VoucherService.conflict("活动代码暂不可用，请重试");
        revision(id, 0, face);
    }

    void update(Row row, Write input, Face face) {
        UUID id = row.campaign().id();
        int revision = row.revision()+1;
        revision(id, revision, face);
        jdbc.update("""
            update wallet_voucher_campaigns set revision=?, version=version+1, total=?,
                starts_at=?, ends_at=?, updated_at=now() where id=?
            """, revision, input.total(), Timestamp.from(input.startsAt()), Timestamp.from(input.endsAt()), id);
    }

    void state(UUID id, String status) {
        jdbc.update("update wallet_voucher_campaigns set status=?, version=version+1, updated_at=now() where id=?",
            status, id);
    }

    void delete(UUID id) {
        jdbc.update("delete from wallet_voucher_campaigns where id=?", id);
    }

    Optional<Claimed> receipt(UUID id, String hash) {
        return jdbc.query("""
            select x.campaign_id, x.redeemed_at, r.face, c.activity_code,
                   x.redemption_issue_total, x.redemption_number from wallet_voucher_claims x
            join wallet_voucher_campaigns c on c.id=x.campaign_id and c.tenant_id=x.tenant_id
            join wallet_voucher_revisions r on r.campaign_id=x.campaign_id and r.revision=x.revision where x.campaign_id=? and x.receipt_hash=?
            """, this::claimed, id, hash).stream().findFirst();
    }

    boolean taken(UUID id, String ip) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from wallet_voucher_claims where campaign_id=? and ip=cast(? as inet))
            """, Boolean.class, id, ip));
    }

    void claim(Row row, String ip, String receipt, String code, Instant now) {
        UUID id = row.campaign().id();
        jdbc.update("""
            insert into wallet_voucher_claims(campaign_id, tenant_id, revision, ip, receipt_hash, code_hash, claimed_at)
            values (?,?,?,cast(? as inet),?,?,?)
            """, id, row.tenant(), row.revision(), ip, receipt, code, Timestamp.from(now));
        jdbc.update("update wallet_voucher_campaigns set issued=issued+1 where id=?", id);
    }

    Claimed lockClaim(UUID tenant, String hash) {
        return jdbc.query("""
            select x.campaign_id, x.redeemed_at, r.face, c.activity_code,
                   x.redemption_issue_total, x.redemption_number from wallet_voucher_claims x
            join wallet_voucher_campaigns c on c.id=x.campaign_id and c.tenant_id=x.tenant_id
            join wallet_voucher_revisions r on r.campaign_id=x.campaign_id and r.revision=x.revision
            where x.tenant_id=? and x.code_hash=? for update of x
            """, this::claimed, tenant, hash).stream().findFirst().orElseThrow(VoucherService::missing);
    }

    Redemption redeem(UUID campaign, String hash, UUID store, UUID actor, Instant now) {
        return jdbc.queryForObject("""
            with next_number as (
                update wallet_voucher_campaigns set redeemed=redeemed+1 where id=?
                returning id, total, redeemed
            )
            update wallet_voucher_claims x
            set redeemed_at=?, redeemed_store_id=?, redeemed_by=?,
                redemption_issue_total=n.total, redemption_number=n.redeemed
            from next_number n
            where x.campaign_id=n.id and x.code_hash=? and x.redeemed_at is null
            returning x.redemption_issue_total, x.redemption_number
            """, (r,n) -> new Redemption(r.getInt(1), r.getInt(2)),
            campaign, Timestamp.from(now), store, actor, hash);
    }

    private void revision(UUID id, int revision, Face face) {
        try {
            jdbc.update("insert into wallet_voucher_revisions values (?,?,cast(? as jsonb))",
                id, revision, json.writeValueAsString(face));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize voucher face", e);
        }
    }

    private Claimed claimed(ResultSet r, int row) throws SQLException {
        return new Claimed(r.getObject("campaign_id", UUID.class), face(r), instant(r,"redeemed_at"), activityCode(r),
            r.getObject("redemption_issue_total", Integer.class), r.getObject("redemption_number", Integer.class));
    }

    private Face face(ResultSet r) throws SQLException {
        try {
            return json.readValue(r.getString("face"), Face.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Invalid voucher face", e);
        }
    }

    private static String activityCode(ResultSet r) throws SQLException {
        return String.format(Locale.ROOT, "%04d", r.getInt("activity_code"));
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        var timestamp = r.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
