package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.persistence.TenantEntryLinksRepository;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class TenantEntryLinksPostgresTest {
    static final String SCHEMA="tenant_entry_test_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate root,jdbc;static TenantEntryLinksRepository repository;
    @BeforeAll static void database() throws Exception {
        var port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(l->l.startsWith("port=")).findFirst().orElseThrow().substring(5);
        var url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
        root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+SCHEMA);
        jdbc=new JdbcTemplate(new DriverManagerDataSource(url+"&currentSchema="+SCHEMA+",public","postgres",""));repository=new TenantEntryLinksRepository(jdbc);
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var file:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())jdbc.execute(Files.readString(file));}
    }
    @AfterAll static void close(){if(root!=null)root.execute("drop schema if exists "+SCHEMA+" cascade");}
    @Test void readsTheSharedTenantNamePerStoreAndNeverCopiesOrWritesIdentity() {
        var tenant=UUID.randomUUID();var one=UUID.randomUUID();var two=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"统一租户名称");
        for(var store:List.of(one,two))jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,?, 'active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,store.toString(),store.equals(one)?"门店甲":"门店乙");
        String before=jdbc.queryForObject("select md5(string_agg(to_jsonb(t)::text,'' order by id)) from tenants t",String.class);
        var a=repository.find(new StoreScope(new TenantId(tenant),one)).orElseThrow();var b=repository.find(new StoreScope(new TenantId(tenant),two)).orElseThrow();
        assertThat(a.tenantName()).isEqualTo("统一租户名称");assertThat(a.storeName()).isEqualTo("门店甲");assertThat(b.tenantName()).isEqualTo(a.tenantName());assertThat(b.storeName()).isEqualTo("门店乙");
        assertThat(jdbc.queryForObject("select md5(string_agg(to_jsonb(t)::text,'' order by id)) from tenants t",String.class)).isEqualTo(before);
        jdbc.update("update tenants set display_name='更新后的统一名称' where id=?",tenant);assertThat(repository.find(new StoreScope(new TenantId(tenant),two)).orElseThrow().tenantName()).isEqualTo("更新后的统一名称");
        assertThat(repository.find(new StoreScope(new TenantId(UUID.randomUUID()),one))).isEmpty();
        jdbc.update("update stores set deleted_at=now() where id=?",one);assertThat(repository.find(new StoreScope(new TenantId(tenant),one))).isEmpty();
        jdbc.update("update tenants set deleted_at=now() where id=?",tenant);assertThat(repository.find(new StoreScope(new TenantId(tenant),two))).isEmpty();
    }
}
