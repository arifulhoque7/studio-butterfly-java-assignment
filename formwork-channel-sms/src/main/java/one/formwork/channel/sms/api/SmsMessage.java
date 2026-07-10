package one.formwork.channel.sms.api;

import java.util.Map;
import java.util.UUID;

public record SmsMessage(
    String to,
    String body,
    UUID tenantId,
    String referenceId,
    Map<String, String> metadata
) {
    public SmsMessage(String to, String body, UUID tenantId) {
        this(to, body, tenantId, null, Map.of());
    }
}
