package com.rpb.reservation.customer.application;
public record CustomerPrivacyNotice(String version,String textZh,String textEn,String labelZh,String labelEn) {
 public static final CustomerPrivacyNotice CURRENT=new CustomerPrivacyNotice("customer-profile-v1",
  "顾客资料用于本租户的身份识别、必要联系、顾客请求的服务及相关交易记录。此登记不包含营销推送同意或钱包扣款授权。",
  "Customer data is used for identification, necessary contact, customer-requested services and related transaction records within this tenant. This registration does not authorize marketing messages or wallet payments.",
  "我确认已向顾客说明上述个人资料用途，并已取得顾客同意（PDPA，由商户登记）",
  "I confirm that I explained the data purposes above to the customer and obtained their consent (PDPA, registered by the merchant)");
}
