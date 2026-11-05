package com.rpb.reservation.payment.application;

import java.nio.file.Path;

public interface PaymentProofOcrAdapter {
    PaymentProofOcrFields extract(Path file, PaymentProofOcrExpected expected);
}
