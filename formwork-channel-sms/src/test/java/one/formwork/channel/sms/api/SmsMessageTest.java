package one.formwork.channel.sms.api;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SmsMessageTest {
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void constructor_AllArgs_SetsAllFields() {
        Map<String, String> meta = Map.of("key", "value");
        SmsMessage msg = new SmsMessage("+491511234", "body", tenantId, "ref-1", meta);

        assertEquals("+491511234", msg.to());
        assertEquals("body", msg.body());
        assertEquals(tenantId, msg.tenantId());
        assertEquals("ref-1", msg.referenceId());
        assertEquals(meta, msg.metadata());
    }

    @Test
    void constructor_ShortForm_SetsDefaults() {
        SmsMessage msg = new SmsMessage("+491511234", "body", tenantId);

        assertNull(msg.referenceId());
        assertEquals(Map.of(), msg.metadata());
    }
}
