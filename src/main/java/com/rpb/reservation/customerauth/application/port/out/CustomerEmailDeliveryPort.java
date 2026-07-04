package com.rpb.reservation.customerauth.application.port.out;

import com.rpb.reservation.customerauth.application.CustomerEmailDeliveryMessage;
import com.rpb.reservation.customerauth.application.CustomerEmailSettings;

public interface CustomerEmailDeliveryPort {
    void sendLoginCode(CustomerEmailDeliveryMessage message, CustomerEmailSettings settings);
}
