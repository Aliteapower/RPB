package com.rpb.reservation.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HostPrefixContextResolverTest {

    @Test
    void configuredBaseHostKeepsRootLegacyAndAlphanumericPrefixTenantScoped() {
        HostPrefixContextResolver resolver = new HostPrefixContextResolver("app.example.co.uk");

        assertThat(resolver.resolveHost("app.example.co.uk").kind())
            .isEqualTo(HostPrefixContext.HostPrefixKind.NONE);
        assertThat(resolver.resolveHost("platform.app.example.co.uk").kind())
            .isEqualTo(HostPrefixContext.HostPrefixKind.PLATFORM);

        HostPrefixContext tenant = resolver.resolveHost("lsc106.app.example.co.uk");
        assertThat(tenant.kind()).isEqualTo(HostPrefixContext.HostPrefixKind.TENANT);
        assertThat(tenant.tenantCode()).isEqualTo("lsc106");
    }

    @Test
    void fallbackKeepsThreeLabelRootLegacyButAllowsFourLabelAlphanumericTenantPrefix() {
        HostPrefixContextResolver resolver = new HostPrefixContextResolver("");

        assertThat(resolver.resolveHost("booking.yumstone.sg").kind())
            .isEqualTo(HostPrefixContext.HostPrefixKind.NONE);

        HostPrefixContext tenant = resolver.resolveHost("lsc106.booking.yumstone.sg");
        assertThat(tenant.kind()).isEqualTo(HostPrefixContext.HostPrefixKind.TENANT);
        assertThat(tenant.tenantCode()).isEqualTo("lsc106");
    }
}
