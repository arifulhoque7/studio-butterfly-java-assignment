package one.formwork.channel.sms.cost;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SmsCostEntityTest {

    @Test
    void settersAndGetters_AllFields_RoundtripCorrectly() {
        SmsCostEntity entity = new SmsCostEntity();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        BigDecimal costPerSegment = new BigDecimal("0.075000");
        BigDecimal totalCost = new BigDecimal("0.150000");

        entity.setId(id);
        UUID tid = UUID.randomUUID();
        entity.setTenantId(tid);
        entity.setMessageId("msg-001");
        entity.setProvider("TWILIO");
        entity.setRecipient("+491234567890");
        entity.setSegmentCount(2);
        entity.setCostPerSegment(costPerSegment);
        entity.setTotalCost(totalCost);
        entity.setCurrency("EUR");
        entity.setCountryCode("DE");
        entity.setSentAt(now);

        assertEquals(id, entity.getId());
        assertEquals(tid, entity.getTenantId());
        assertEquals("msg-001", entity.getMessageId());
        assertEquals("TWILIO", entity.getProvider());
        assertEquals("+491234567890", entity.getRecipient());
        assertEquals(2, entity.getSegmentCount());
        assertEquals(0, costPerSegment.compareTo(entity.getCostPerSegment()));
        assertEquals(0, totalCost.compareTo(entity.getTotalCost()));
        assertEquals("EUR", entity.getCurrency());
        assertEquals("DE", entity.getCountryCode());
        assertEquals(now, entity.getSentAt());
    }

    @Test
    void defaultConstructor_IdAndSentAt_AreNullInitially() {
        SmsCostEntity entity = new SmsCostEntity();

        assertNull(entity.getId());
        assertNull(entity.getSentAt());
        assertNull(entity.getTenantId());
        assertNull(entity.getProvider());
    }

    @Test
    void setSegmentCount_Zero_IsAllowed() {
        SmsCostEntity entity = new SmsCostEntity();
        entity.setSegmentCount(0);
        assertEquals(0, entity.getSegmentCount());
    }

    @Test
    void setCurrency_ThreeLetterCode_IsStored() {
        SmsCostEntity entity = new SmsCostEntity();
        entity.setCurrency("USD");
        assertEquals("USD", entity.getCurrency());
    }

    @Test
    void setCountryCode_Null_IsAllowed() {
        SmsCostEntity entity = new SmsCostEntity();
        entity.setCountryCode(null);
        assertNull(entity.getCountryCode());
    }

    @Test
    void setMessageId_Null_IsAllowed() {
        SmsCostEntity entity = new SmsCostEntity();
        entity.setMessageId(null);
        assertNull(entity.getMessageId());
    }
}
