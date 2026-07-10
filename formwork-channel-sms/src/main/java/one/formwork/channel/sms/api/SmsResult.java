package one.formwork.channel.sms.api;

import java.math.BigDecimal;

public record SmsResult(
    String messageId,
    String provider,
    DeliveryStatus status,
    int segmentCount,
    BigDecimal cost,
    String errorCode,
    String errorMessage
) {
    public static SmsResult success(String messageId, String provider, int segments) {
        return new SmsResult(messageId, provider, DeliveryStatus.ACCEPTED, segments, null, null, null);
    }
    public static SmsResult failure(String provider, String errorCode, String errorMessage) {
        return new SmsResult(null, provider, DeliveryStatus.FAILED, 0, null, errorCode, errorMessage);
    }
    public boolean isSuccess() {
        return status == DeliveryStatus.ACCEPTED || status == DeliveryStatus.SENT || status == DeliveryStatus.DELIVERED;
    }
}
