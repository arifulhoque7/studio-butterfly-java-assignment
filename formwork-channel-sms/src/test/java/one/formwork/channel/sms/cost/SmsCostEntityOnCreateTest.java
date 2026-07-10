package one.formwork.channel.sms.cost;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SmsCostEntityOnCreateTest {
    @Test
    void onCreate_NoIdNoSentAt_GeneratesBoth() throws Exception {
        SmsCostEntity e = new SmsCostEntity();
        var method = SmsCostEntity.class.getDeclaredMethod("onCreate");
        method.setAccessible(true);
        method.invoke(e);
        assertNotNull(e.getId());
        assertNotNull(e.getSentAt());
    }

    @Test
    void onCreate_IdAlreadySet_KeepsId() throws Exception {
        SmsCostEntity e = new SmsCostEntity();
        UUID existingId = UUID.randomUUID();
        e.setId(existingId);
        var method = SmsCostEntity.class.getDeclaredMethod("onCreate");
        method.setAccessible(true);
        method.invoke(e);
        assertEquals(existingId, e.getId());
    }

    @Test
    void onCreate_SentAtAlreadySet_KeepsSentAt() throws Exception {
        SmsCostEntity e = new SmsCostEntity();
        Instant existing = Instant.parse("2025-01-01T00:00:00Z");
        e.setSentAt(existing);
        var method = SmsCostEntity.class.getDeclaredMethod("onCreate");
        method.setAccessible(true);
        method.invoke(e);
        assertEquals(existing, e.getSentAt());
    }
}
