package com.rpb.reservation.dailyledger.migration;

import java.nio.file.Path;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.slf4j.LoggerFactory;

@Component
@Profile({"local", "ledger-import"})
@ConditionalOnProperty(name="rpb.daily-ledger.import-file")
public class LedgerSeedRunner implements ApplicationRunner {
    private final LedgerSeedImporter importer;private final Environment env;private final ConfigurableApplicationContext context;
    public LedgerSeedRunner(LedgerSeedImporter importer,Environment env,ConfigurableApplicationContext context){this.importer=importer;this.env=env;this.context=context;}
    @Override public void run(ApplicationArguments args) throws Exception {
        var result=importer.importSnapshot(Path.of(env.getRequiredProperty("rpb.daily-ledger.import-file")),env.getProperty("rpb.daily-ledger.tenant-code","sx"),env.getRequiredProperty("rpb.daily-ledger.seed-password"));
        LoggerFactory.getLogger(getClass()).info("Imported daily ledger: {}",result);
        if(env.acceptsProfiles(Profiles.of("ledger-import")))context.close();
    }
}
