package com.rpb.reservation.cards.voucher;

import com.rpb.reservation.cards.application.CardException;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.wallet.application.WalletDirectory;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VoucherPresentationService {
    public record Contact(UUID id, String name, String address, String phone) {}
    public record Presentation(VoucherDtos.Face face, String batchCode, String tenantName,
                               String logoUrl, Instant startsAt, Instant endsAt, List<Contact> contacts) {}
    private final VoucherRepository repository;
    private final WalletDirectory directory;

    public VoucherPresentationService(VoucherRepository repository, WalletDirectory directory) {
        this.repository=repository;
        this.directory=directory;
    }

    public Presentation get(UUID id) {
        var row=repository.find(id,false);
        var campaign=row.campaign();
        var brand=directory.store(new StoreScope(new TenantId(row.tenant()),row.owner()));
        return new Presentation(campaign.face(),campaign.activityCode(),brand.storeName(),
            brand.logoMediaAssetId()==null?null:"/api/v1/public/voucher-campaigns/"+id+"/logo",
            campaign.startsAt(),campaign.endsAt(),repository.contacts(row.tenant(),campaign.face()));
    }

    public CallScreenMediaContent logo(UUID id) {
        var row=repository.find(id,false);
        var scope=new StoreScope(new TenantId(row.tenant()),row.owner());
        var asset=directory.store(scope).logoMediaAssetId();
        if(asset==null)throw new CardException("VOUCHER_LOGO_NOT_FOUND",404,"租户未设置 Logo");
        return directory.logo(scope,asset);
    }
}
