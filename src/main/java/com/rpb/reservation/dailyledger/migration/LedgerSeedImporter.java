package com.rpb.reservation.dailyledger.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.dailyledger.domain.LedgerRules;
import com.rpb.reservation.dailyledger.persistence.LedgerRepository;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.platformbilling.application.*;
import com.rpb.reservation.tenantadmin.application.TenantAdminStaffService;
import com.rpb.reservation.tenantadmin.application.TenantAdminStaffMutationCommand;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit snapshot migration, never an automatic production seed. All-or-nothing, no overwrite. */
@Service
@Profile({"local", "ledger-import"})
public class LedgerSeedImporter {
    private final PlatformTenantService tenants;private final ProductSubscriptionService subscriptions;
    private final TenantAdminStaffService staff;private final LedgerRepository ledger;
    private final LedgerRules rules;private final ObjectMapper json;private final JdbcTemplate jdbc;private final LedgerBrandingMigration brandingMigration;
    public LedgerSeedImporter(PlatformTenantService tenants,ProductSubscriptionService subscriptions,TenantAdminStaffService staff,LedgerRepository ledger,LedgerRules rules,ObjectMapper json,JdbcTemplate jdbc,LedgerBrandingMigration brandingMigration){this.tenants=tenants;this.subscriptions=subscriptions;this.staff=staff;this.ledger=ledger;this.rules=rules;this.json=json;this.jdbc=jdbc;this.brandingMigration=brandingMigration;}
    public record Result(UUID tenantId,UUID storeId,int days,int records,int audit,String sha256) {}
    @Transactional
    public Result importSnapshot(Path path,String tenantCode,String password) throws Exception {
        byte[] bytes=Files.readAllBytes(path);JsonNode source=json.readTree(bytes);
        if(!source.path("source").asText().equals("https://yumstone-daily-ledger.yesmycup.chatgpt.site")||!source.path("days").isArray()||!source.path("audit").isArray())throw new IllegalArgumentException("Not a complete original ledger snapshot");
        if(jdbc.queryForObject("select count(*) from tenants where tenant_code=?",Integer.class,tenantCode)>0)throw new IllegalStateException("Tenant already exists; refusing to overwrite "+tenantCode);
        // Validate full source JSON before creating the tenant. History and old accounting versions are kept verbatim.
        rules.settings(source.path("settings"));
        int recordCount=0;Set<String> dates=new HashSet<>();
        for(JsonNode day:source.path("days")){
            LocalDate.parse(day.path("date").asText());rules.settings(day.path("config"));
            if(!dates.add(day.path("date").asText()))throw new IllegalArgumentException("Duplicate source date");
            var fields=day.path("records").fields();while(fields.hasNext()){
                var field=fields.next();if(!LedgerRules.PERIODS.contains(field.getKey())||!field.getValue().isObject())throw new IllegalArgumentException("Invalid source record");
                JsonNode record=field.getValue();if(record.path("version").asInt()<1||!rules.totals(record).equals(record.path("totals")))throw new IllegalArgumentException("Source totals mismatch: "+day.path("date")+"/"+field.getKey());recordCount++;
            }
        }
        var operator=new PlatformOperator(null,"platform_admin");
        var tenant=tenants.createTenant(new PlatformTenantMutationCommand(tenantCode,"蜀香","active","zh-CN",null,null,null,password,null,"single_store",null,null),operator);
        UUID storeId=jdbc.queryForObject("select id from stores where tenant_id=? and deleted_at is null",UUID.class,tenant.id());
        StoreScope scope=new StoreScope(new TenantId(tenant.id()),storeId);
        ledger.lockSettings(scope,source.path("settings"));
        ledger.updateSettings(scope,source.path("settings"),source.path("branding"),0);
        for(JsonNode day:source.path("days")) {
            LocalDate date=LocalDate.parse(day.path("date").asText());ledger.createDay(scope,date,day.path("config"));
            var fields=day.path("records").fields();while(fields.hasNext()) {
                var field=fields.next();JsonNode submitted=day.path("submitted_records").get(field.getKey());
                ledger.writeRecord(scope,date,field.getKey(),0,field.getValue(),submitted);
                jdbc.update("update daily_ledger_records set version=? where tenant_id=? and store_id=? and business_date=? and period=?",field.getValue().path("version").asInt(),tenant.id(),storeId,date,field.getKey());
            }
        }
        for(JsonNode row:source.path("audit")) {
            jdbc.update("insert into daily_ledger_audit(tenant_id,store_id,business_date,period,action,actor_name,payload,source_id,created_at) values(?,?,?,?,'import',?,?::jsonb,?,?)",
                tenant.id(),storeId,LocalDate.parse(row.path("date").asText()),row.path("period").asText(),row.path("actor").asText(),row.path("payload").toString(),row.path("id").asText(),OffsetDateTime.parse(row.path("saved_at").asText()));
        }
        // Source PBKDF2 passwords are not compatible with RPB. Preserve staff identity, require admin reset before enabling.
        for(JsonNode user:source.path("users"))if(user.path("role").asText().equals("staff")) {
            String randomPassword=UUID.randomUUID().toString().replace("-","").substring(0,6);
            var created=staff.createStaff(scope,new TenantAdminStaffMutationCommand(user.path("username").asText(),user.path("name").asText(),null,null,"active",randomPassword,List.of(storeId),storeId));
            jdbc.update("update auth_accounts set status='disabled',updated_at=now() where id=? and tenant_id=?",created.id(),tenant.id());
        }
        subscriptions.purchase(tenant.id(),new ProductSubscriptionCommand("daily-ledger-source-seed-v21","daily_ledger","monthly",null,null,null,"SGD","测试租户开通；原站点迁入，非真实收款",1,null),new PlatformBillingOperator(null,"platform_admin"));
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        ledger.audit(scope,null,null,"import",null,"源站点迁移",json.createObjectNode().put("source",source.path("source").asText()).put("source_commit",source.path("sourceCommit").asText()).put("sha256",sha).put("records",recordCount));
        brandingMigration.migrate(tenantCode);
        return new Result(tenant.id(),storeId,source.path("days").size(),recordCount,source.path("audit").size(),sha);
    }
}
