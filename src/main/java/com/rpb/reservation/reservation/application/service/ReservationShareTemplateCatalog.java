package com.rpb.reservation.reservation.application.service;

import java.util.List;

public final class ReservationShareTemplateCatalog {
    private static final String DEFAULT_SEED_KEY = "restaurant_reservation_confirmation_v1";

    private static final List<String> ALLOWED_VARIABLES = List.of(
        "storeName",
        "reservationNo",
        "reservationDate",
        "reservationTime",
        "partySize",
        "tableCode",
        "holdMinutes",
        "contactName",
        "guestSalutation",
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
        尊敬的 {{contactName}} {{guestSalutation}}，

        感谢您选择 {{storeName}}。我们很荣幸地确认您的预订安排，具体信息如下：

        预订编号：{{reservationNo}}

        日期：{{reservationDate}}

        时间：{{reservationTime}}

        人数：{{partySize}}位成人

        桌位：{{tableCode}} (已预留)

        预留时间：为保证所有宾客的用餐体验，我们将为您保留座位 {{holdMinutes}}分钟。若超过保留时间，座位可能被取消，敬请谅解。

        到店提示：{{arrivalNote}}

        门店地址：{{storeAddress}}

        联系电话：{{storePhone}}

        如需修改或取消，请至少提前 2 小时联系门店。

        期待为您奉上一场味蕾盛宴！

        顺颂时祺，
        {{storeName}} 预订部
        """;

    private ReservationShareTemplateCatalog() {
    }

    public static List<String> allowedVariables() {
        return ALLOWED_VARIABLES;
    }

    public static String defaultSeedKey() {
        return DEFAULT_SEED_KEY;
    }

    public static String defaultTemplate() {
        return DEFAULT_TEMPLATE.stripTrailing();
    }
}
