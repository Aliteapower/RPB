package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PublicAcceptanceDeploymentPackageValidationTest {

    @Test
    void protectedPublicAcceptanceDeploymentPackageDocumentsRuntimeAndOuterAccessControls() throws Exception {
        Path packageRoot = Path.of("deploy", "public-acceptance");

        String readme = FrontendSourceSupport.readString(packageRoot.resolve("README.md"));
        String nginx = FrontendSourceSupport.readString(packageRoot.resolve("nginx").resolve("rpb-public-acceptance.conf"));
        String service = FrontendSourceSupport.readString(packageRoot.resolve("systemd").resolve("rpb-backend.service"));
        String env = FrontendSourceSupport.readString(packageRoot.resolve("env").resolve("rpb-backend.env.example"));
        String smoke = FrontendSourceSupport.readString(packageRoot.resolve("scripts").resolve("smoke-check.sh"));

        assertThat(readme)
            .contains("protected public acceptance")
            .contains("No real business data")
            .contains("Basic Auth")
            .contains("IP allowlist")
            .contains("production security hardening");

        assertThat(nginx)
            .contains("auth_basic")
            .contains("allow 203.0.113.10;")
            .contains("deny all;")
            .contains("proxy_pass http://127.0.0.1:8080;")
            .contains("client_max_body_size 90m;")
            .contains("try_files $uri $uri/ /index.html;");

        assertThat(service)
            .contains("EnvironmentFile=/etc/rpb/rpb-backend.env")
            .contains("ExecStart=/usr/bin/java")
            .contains("--rpb.local-auth.enabled=${RPB_LOCAL_AUTH_ENABLED}")
            .contains("--rpb.call-screen-media.storage-root=${RPB_CALL_SCREEN_MEDIA_STORAGE_ROOT}");

        assertThat(env)
            .contains("DB_NAME=rpb_acceptance")
            .contains("RPB_LOCAL_AUTH_ENABLED=false")
            .contains("RPB_CALL_SCREEN_MEDIA_STORAGE_ROOT=/opt/rpb/media")
            .contains("JWT_SECRET=replace-with-acceptance-only-secret");

        assertThat(smoke)
            .contains("RPB_BASE_URL")
            .contains("/api/v1/auth/captcha/slider")
            .contains("/api/v1/auth/me")
            .contains("front door protection")
            .contains("tenant logo");
    }
}
