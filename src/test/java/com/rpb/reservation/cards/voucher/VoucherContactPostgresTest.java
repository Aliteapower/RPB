package com.rpb.reservation.cards.voucher;

import com.rpb.reservation.catalog.CatalogPostgresTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VoucherContactPostgresTest extends CatalogPostgresTest {
    @Test void contactsAreScopedToCampaignTenantAndApplicableStores() {
        jdbc.update("update stores set share_address=?,share_contact_phone=? where id=?", "测试地址 18 号", "+6591234567", store);
        var repository = new VoucherRepository(jdbc, json);
        var face = new VoucherDtos.Face("券", "cash", null, null, null, null, null, 1000, "SGD",
            List.of(new VoucherDtos.Store(store,"Store")), null, null, "Asia/Singapore", "", null);
        assertThat(repository.contacts(tenant,face)).singleElement().satisfies(contact -> {
            assertThat(contact.name()).isEqualTo("Store");
            assertThat(contact.address()).isEqualTo("测试地址 18 号");
            assertThat(contact.phone()).isEqualTo("+6591234567");
        });
        assertThat(repository.contacts(UUID.randomUUID(),face)).isEmpty();
    }
}
