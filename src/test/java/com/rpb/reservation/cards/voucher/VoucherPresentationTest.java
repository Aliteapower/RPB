package com.rpb.reservation.cards.voucher;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.wallet.application.WalletDirectory;
import com.rpb.reservation.wallet.application.WalletViews;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoucherPresentationTest {
    @Test void logoIsResolvedFromCampaignTenantAndCurrentProfile() {
        UUID id=UUID.randomUUID(), tenant=UUID.randomUUID(), store=UUID.randomUUID(), logo=UUID.randomUUID();
        var scope=new StoreScope(new TenantId(tenant),store);
        var repository=mock(VoucherRepository.class);
        var directory=mock(WalletDirectory.class);
        var campaign=new VoucherDtos.Campaign(id,"4723","active",1,10,0,0,Instant.EPOCH,Instant.EPOCH.plusSeconds(10),null);
        when(repository.find(id,false)).thenReturn(new VoucherRepository.Row(tenant,store,0,campaign));
        when(directory.store(scope)).thenReturn(new WalletViews.StoreIdentity(
            tenant,store,UUID.randomUUID(),"主体","租户名称","tenant","Asia/Singapore","zh-CN",logo));
        var service=new VoucherPresentationService(repository,directory);
        var presentation=service.get(id);
        assertThat(presentation.batchCode()).isEqualTo("4723");
        assertThat(presentation.tenantName()).isEqualTo("租户名称");
        assertThat(presentation.logoUrl()).isEqualTo("/api/v1/public/voucher-campaigns/"+id+"/logo");
        service.logo(id);
        verify(directory).logo(scope,logo);

        when(directory.store(scope)).thenReturn(new WalletViews.StoreIdentity(
            tenant,store,UUID.randomUUID(),"主体","租户名称","tenant","Asia/Singapore","zh-CN",null));
        assertThat(service.get(id).logoUrl()).isNull();
        assertThatThrownBy(()->service.logo(id)).hasMessageContaining("Logo");
    }
}
