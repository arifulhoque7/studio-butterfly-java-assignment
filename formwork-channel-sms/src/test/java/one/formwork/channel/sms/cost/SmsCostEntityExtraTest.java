package one.formwork.channel.sms.cost;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SmsCostEntityExtraTest {
    @Test
    void allFields_RoundTrip() {
        SmsCostEntity e = new SmsCostEntity();
        UUID id = UUID.randomUUID();
        e.setId(id);
        UUID tid = UUID.randomUUID();
        e.setTenantId(tid);
        e.setProvider("TWILIO");
        e.setMessageId("SM-1");
        e.setRecipient("+49151");
        e.setSegmentCount(2);
        e.setCostPerSegment(BigDecimal.valueOf(0.04));
        e.setTotalCost(BigDecimal.valueOf(0.08));
        e.setCurrency("EUR");
        e.setCountryCode("DE");
        e.setSentAt(Instant.now());

        assertEquals(id, e.getId());
        assertEquals(tid, e.getTenantId());
        assertEquals("TWILIO", e.getProvider());
        assertEquals("SM-1", e.getMessageId());
        assertEquals("+49151", e.getRecipient());
        assertEquals(2, e.getSegmentCount());
        assertEquals(BigDecimal.valueOf(0.04), e.getCostPerSegment());
        assertEquals(BigDecimal.valueOf(0.08), e.getTotalCost());
        assertEquals("EUR", e.getCurrency());
        assertEquals("DE", e.getCountryCode());
        assertNotNull(e.getSentAt());
    }
}
