package com.rpb.reservation.customerauth.persistence;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class CustomerPasswordSecurityMigrationTest {
    @Test void migrationRequiresBootstrapChangePreservesCustomPasswordsAndRevokesLegacySessions() throws Exception {
        String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(l->l.startsWith("port=")).findFirst().orElseThrow().substring(5).trim();
        if(!port.matches("[0-9]{1,5}"))throw new IllegalStateException("invalid_pointer_port");
        String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
        String schema="customer_security_migration_"+UUID.randomUUID().toString().replace("-","");
        var root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));
        root.execute("create schema "+schema);
        try {
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(url+"&currentSchema="+schema,"postgres",""));
            try(var paths=Files.list(Path.of("src/main/resources/db/migration"))){
                for(var file:paths.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")&&!p.getFileName().toString().startsWith("V082__")&&!p.getFileName().toString().startsWith("V083__")).sorted().toList())jdbc.execute(Files.readString(file));
            }
            UUID tenant=UUID.randomUUID();
            jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"Migration test");
            String bootstrapHash=new BCryptPasswordEncoder(4).encode("234567"),customHash=new BCryptPasswordEncoder(4).encode("user-chosen-password");
            UUID bootstrap=fixture(jdbc,tenant,0,bootstrapHash),custom=fixture(jdbc,tenant,1,customHash);
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V082__customer_password_security.sql")));
            assertThat(jdbc.queryForObject("select password_change_required from customer_password_credentials where customer_id=?",Boolean.class,bootstrap)).isTrue();
            assertThat(jdbc.queryForObject("select password_change_required from customer_password_credentials where customer_id=?",Boolean.class,custom)).isFalse();
            assertThat(jdbc.queryForObject("select password_hash from customer_password_credentials where customer_id=?",String.class,bootstrap)).isEqualTo(bootstrapHash);
            assertThat(jdbc.queryForObject("select password_hash from customer_password_credentials where customer_id=?",String.class,custom)).isEqualTo(customHash);
            assertThat(jdbc.queryForObject("select count(*) from customer_auth_sessions where tenant_id=? and status='active'",Integer.class,tenant)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from customers where tenant_id=?",Integer.class,tenant)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from customer_security_email_codes",Integer.class)).isZero();
            jdbc.execute("insert into customer_auth_sessions(id,tenant_id,auth_account_id,customer_id,session_hash,status,expires_at) select gen_random_uuid(),tenant_id,id,customer_id,gen_random_uuid()::text,'active',now()+interval '1 hour' from customer_auth_accounts");
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V083__customer_in_person_activation.sql")));
            assertThat(jdbc.queryForObject("select count(*) from customer_auth_sessions where customer_id=? and status='active'",Integer.class,bootstrap)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from customer_auth_sessions where customer_id=? and status='active'",Integer.class,custom)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select password_hash from customer_password_credentials where customer_id=?",String.class,custom)).isEqualTo(customHash);
            assertThat(jdbc.queryForObject("select count(*) from customer_activation_codes",Integer.class)).isZero();
        } finally { root.execute("drop schema "+schema+" cascade"); }
    }
    private UUID fixture(JdbcTemplate jdbc,UUID tenant,int version,String hash){
        UUID customer=UUID.randomUUID(),account=UUID.randomUUID();
        jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,status) values(?,?,?,'regular','active')",customer,tenant,customer.toString());
        jdbc.update("insert into customer_auth_accounts(id,tenant_id,customer_id,status) values(?,?,?,'active')",account,tenant,customer);
        jdbc.update("insert into customer_password_credentials(tenant_id,customer_id,password_hash,version) values(?,?,?,?)",tenant,customer,hash,version);
        jdbc.update("insert into customer_auth_sessions(id,tenant_id,auth_account_id,customer_id,session_hash,status,expires_at) values(?,?,?,?,?,'active',now()+interval '1 hour')",UUID.randomUUID(),tenant,account,customer,UUID.randomUUID().toString());
        return customer;
    }
}
