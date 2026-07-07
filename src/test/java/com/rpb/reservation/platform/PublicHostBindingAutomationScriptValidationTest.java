package com.rpb.reservation.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PublicHostBindingAutomationScriptValidationTest {
    @Test
    void reconcileScriptExpandsCertificateAndUpdatesBindingStatus() throws Exception {
        Path scriptPath = Path.of("ops", "reconcile-public-host-bindings.sh");

        assertThat(scriptPath).exists();

        String script = Files.readString(scriptPath);

        assertThat(script)
            .contains("public_host_bindings")
            .contains("tls_status in ('pending', 'failed')")
            .contains("tenant_host_aliases")
            .contains("certbot --nginx")
            .contains("--cert-name")
            .contains("--expand")
            .contains("nginx -t")
            .contains("systemctl reload nginx")
            .contains("tls_status = 'covered'")
            .contains("tls_status = 'failed'")
            .contains("RPB_HOST_PREFIX_BASE_HOST")
            .contains("RPB_PUBLIC_HOST_CERT_NAME");
    }
}
