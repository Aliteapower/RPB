package com.rpb.reservation.dailyledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.dailyledger.application.LedgerBrandingService;
import com.rpb.reservation.dailyledger.domain.LedgerException;
import com.rpb.reservation.tenantadmin.application.TenantAdminProfile;
import com.rpb.reservation.tenantadmin.application.TenantAdminProfileService;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LedgerBrandingServiceTest {
    final ObjectMapper json = new ObjectMapper();
    final StoreScope scope = new StoreScope(new TenantId(UUID.randomUUID()), UUID.randomUUID());
    final TenantAdminProfileService profiles = mock(TenantAdminProfileService.class);
    final TenantAdminProfile profile = mock(TenantAdminProfile.class);
    final LedgerBrandingService branding = new LedgerBrandingService(profiles, json);
    LedgerBrandingServiceTest() { when(profiles.getProfile(scope)).thenReturn(profile); when(profile.displayName()).thenReturn("统一门店名称"); }

    @Test void sharedNameAndLogoWinOverLegacyProductCopy() {
        var asset = UUID.randomUUID(); when(profile.logoMediaAssetId()).thenReturn(asset);
        var legacy = json.createObjectNode().put("store_name", "旧名称").put("logo_data", "legacy");
        var result = branding.resolve(scope, legacy);
        assertThat(result.path("store_name").asText()).isEqualTo("统一门店名称");
        assertThat(result.path("logo_data").asText()).isEqualTo("/api/v1/stores/" + scope.storeId().value() + "/daily-ledger/branding/logo/" + asset);
        assertThat(legacy.path("store_name").asText()).isEqualTo("旧名称");
    }
    @Test void clearedSharedLogoDoesNotResurrectMigratedLegacyLogo() {
        var legacy = json.createObjectNode().put("platform_branding_managed", true).put("logo_data", "legacy");
        assertThat(branding.resolve(scope, legacy).path("logo_data").asText()).isEmpty();
    }
    @Test void unadoptedLegacyLogoRemainsVisibleUntilExplicitMigration() {
        assertThat(branding.resolve(scope, json.createObjectNode().put("logo_data", "legacy")).path("logo_data").asText()).isEqualTo("legacy");
    }
    @Test void arbitraryOrOldAssetCannotBeReadThroughLedgerRoute() {
        when(profile.logoMediaAssetId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> branding.logo(scope, UUID.randomUUID())).isInstanceOf(LedgerException.class);
        verify(profiles, never()).readLogoMedia(any(), any());
    }
    @Test void currentTenantAssetUsesExistingScopedMediaService() {
        var asset = UUID.randomUUID(); when(profile.logoMediaAssetId()).thenReturn(asset);
        var content = new com.rpb.reservation.queuedisplay.application.CallScreenMediaContent(
            new org.springframework.core.io.ByteArrayResource(new byte[]{1,2,3}), "image/png", 3);
        when(profiles.readLogoMedia(scope,asset)).thenReturn(content);
        assertThat(branding.logo(scope,asset)).isSameAs(content);
        verify(profiles).readLogoMedia(scope,asset);
    }
}
