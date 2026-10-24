package com.rpb.reservation.dailyledger.migration;

import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("ledger-branding-import")
@ConditionalOnProperty(name = "rpb.daily-ledger.branding-tenant-code")
public class LedgerBrandingMigrationRunner implements ApplicationRunner {
    private final LedgerBrandingMigration migration;
    private final Environment env;
    private final ConfigurableApplicationContext context;
    public LedgerBrandingMigrationRunner(LedgerBrandingMigration migration, Environment env, ConfigurableApplicationContext context) {
        this.migration = migration; this.env = env; this.context = context;
    }
    public void run(ApplicationArguments args) {
        int count = migration.migrate(env.getRequiredProperty("rpb.daily-ledger.branding-tenant-code"));
        LoggerFactory.getLogger(getClass()).info("Ledger branding adopted: {} store settings", count);
        context.close();
    }
}
