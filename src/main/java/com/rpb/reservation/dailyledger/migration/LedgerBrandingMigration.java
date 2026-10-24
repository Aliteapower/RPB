package com.rpb.reservation.dailyledger.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.application.TenantAdminProfileService;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Explicit, tenant-scoped adoption into the existing logo store; never runs on a read. */
@Service
public class LedgerBrandingMigration {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TenantAdminProfileService profiles;

    public LedgerBrandingMigration(JdbcTemplate jdbc, ObjectMapper json, TenantAdminProfileService profiles) {
        this.jdbc = jdbc; this.json = json; this.profiles = profiles;
    }

    @Transactional
    public int migrate(String tenantCode) {
        UUID tenant = jdbc.queryForObject("select id from tenants where tenant_code=? and deleted_at is null for update", UUID.class, tenantCode);
        var rows = jdbc.query("""
            select l.store_id,l.branding::text from daily_ledger_settings l
            join stores s on s.id=l.store_id and s.tenant_id=l.tenant_id and s.deleted_at is null
            where l.tenant_id=? order by l.store_id for update of l
            """, (rs, n) -> new Legacy(rs.getObject(1, UUID.class), parse(rs.getString(2))), tenant);
        if (rows.isEmpty()) return 0;
        var scope = new StoreScope(new TenantId(tenant), rows.getFirst().storeId());
        var current = profiles.getProfile(scope);
        boolean managed = rows.stream().anyMatch(r -> r.branding().path("platform_branding_managed").asBoolean(false));
        if (current.logoMediaAssetId() == null && !managed) {
            var logos = rows.stream().map(r -> r.branding().path("logo_data").asText("")).filter(s -> !s.isBlank()).distinct().toList();
            if (logos.size() > 1) throw new IllegalStateException("门店存在不同旧Logo，请先在租户资料选择共享Logo，再迁移");
            if (!logos.isEmpty()) profiles.uploadLogo(scope, png(logos.getFirst()));
        }
        return jdbc.update("""
            update daily_ledger_settings set branding=jsonb_set(branding,'{platform_branding_managed}','true'::jsonb),
              version=version+1,updated_at=now()
            where tenant_id=? and not coalesce((branding->>'platform_branding_managed')::boolean,false)
            """, tenant);
    }

    private JsonNode parse(String value) { try { return json.readTree(value); } catch (IOException e) { throw new IllegalArgumentException("Invalid legacy branding", e); } }
    private record Legacy(UUID storeId, JsonNode branding) {}

    private static MultipartFile png(String data) {
        String prefix = "data:image/png;base64,";
        if (!data.startsWith(prefix)) throw new IllegalArgumentException("旧Logo不是PNG图片");
        byte[] bytes = Base64.getDecoder().decode(data.substring(prefix.length()));
        return new MultipartFile() {
            public String getName() { return "file"; }
            public String getOriginalFilename() { return "ledger-logo.png"; }
            public String getContentType() { return "image/png"; }
            public boolean isEmpty() { return bytes.length == 0; }
            public long getSize() { return bytes.length; }
            public byte[] getBytes() { return bytes.clone(); }
            public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
            public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), bytes); }
        };
    }
}
