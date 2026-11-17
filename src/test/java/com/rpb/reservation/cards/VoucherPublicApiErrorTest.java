package com.rpb.reservation.cards;

import com.rpb.reservation.cards.application.CardException;
import com.rpb.reservation.cards.voucher.PublicVoucherController;
import com.rpb.reservation.cards.voucher.VoucherService;
import com.rpb.reservation.wallet.cards.CardErrorAdvice;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoucherPublicApiErrorTest {
    @Test void missingCampaignReturnsVoucherError() throws Exception {
        var id = UUID.randomUUID();
        var service = mock(VoucherService.class);
        when(service.view(eq(id), eq("127.0.0.1"), isNull()))
            .thenThrow(new CardException("VOUCHER_NOT_FOUND", 404, "活动或券码不存在"));
        var mvc = MockMvcBuilders.standaloneSetup(new PublicVoucherController(service, ""))
            .setControllerAdvice(new CardErrorAdvice()).build();

        mvc.perform(get("/api/v1/public/voucher-campaigns/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("VOUCHER_NOT_FOUND"));
    }
}
