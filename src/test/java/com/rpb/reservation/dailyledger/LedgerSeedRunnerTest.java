package com.rpb.reservation.dailyledger;

import com.rpb.reservation.dailyledger.migration.LedgerSeedImporter;
import com.rpb.reservation.dailyledger.migration.LedgerSeedRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Path;
import static org.mockito.Mockito.*;

class LedgerSeedRunnerTest {
    @Test void explicitImportModeClosesItsOwnContextAfterSuccessfulImport() throws Exception {
        var importer = mock(LedgerSeedImporter.class);
        var context = mock(ConfigurableApplicationContext.class);
        var env = new MockEnvironment().withProperty("rpb.daily-ledger.import-file", "snapshot.json")
            .withProperty("rpb.daily-ledger.seed-password", "fixture-only");
        env.setActiveProfiles("ledger-import");
        new LedgerSeedRunner(importer, env, context).run(new DefaultApplicationArguments());
        verify(importer).importSnapshot(Path.of("snapshot.json"), "sx", "fixture-only");
        verify(context).close();
    }
    @Test void failedImportPropagatesAndDoesNotReportSuccessfulCompletion() throws Exception {
        var importer = mock(LedgerSeedImporter.class);
        var context = mock(ConfigurableApplicationContext.class);
        var env = new MockEnvironment().withProperty("rpb.daily-ledger.import-file", "snapshot.json")
            .withProperty("rpb.daily-ledger.seed-password", "fixture-only");
        env.setActiveProfiles("ledger-import");
        doThrow(new IllegalStateException("Tenant exists")).when(importer).importSnapshot(any(), any(), any());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> new LedgerSeedRunner(importer, env, context).run(new DefaultApplicationArguments()));
        verify(context, never()).close();
    }
}
