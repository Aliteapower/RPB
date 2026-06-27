package com.rpb.reservation.reservation.application.service;

import java.util.List;

public final class ReservationShareTemplateCatalog {
    private static final List<String> ALLOWED_VARIABLES = List.of(
        "storeName",
        "reservationNo",
        "reservationDate",
        "reservationTime",
        "partySize",
        "contactName",
        "maskedPhone",
        "storeAddress",
        "googleMapUrl",
        "storePhone",
        "arrivalNote",
        "confirmInstruction",
        "cancelInstruction",
        "changeInstruction",
        "replyInstruction"
    );

    private static final String DEFAULT_TEMPLATE = """
        【订位确认】
        门店：{{storeName}}
        预约编号：{{reservationNo}}
        日期：{{reservationDate}}
        时间：{{reservationTime}}
        人数：{{partySize}} 位
        联系人：{{contactName}}
        电话：{{maskedPhone}}

        地址：
        {{storeAddress}}

        Google Map：
        {{googleMapUrl}}

        到店提示：
        {{arrivalNote}}

        联系电话：
        {{storePhone}}
        """;

    private ReservationShareTemplateCatalog() {
    }

    public static List<String> allowedVariables() {
        return ALLOWED_VARIABLES;
    }

    public static String defaultTemplate() {
        return DEFAULT_TEMPLATE.stripTrailing();
    }
}
