package one.formwork.channel.sms.reliability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Finding 7: jitter must be applied before the cap, so the cap is always honoured. */
class BackoffTest {

    @Test
    void delayNeverExceedsCap_forAnyAttemptOrJitter() {
        long base = 1000, cap = 3000;
        for (int attempt = 1; attempt <= 10; attempt++) {
            assertTrue(Backoff.delayMillis(attempt, base, cap, 1.0) <= cap);
            assertTrue(Backoff.delayMillis(attempt, base, cap, 0.0) <= cap);
        }
    }

    @Test
    void largeExponential_isCappedNotOverflowed() {
        // attempt 6: exponential = 1000 * 2^5 = 32000, jitter 1.0 -> 32000, capped to 3000
        assertEquals(3000, Backoff.delayMillis(6, 1000, 3000, 1.0));
    }

    @Test
    void firstAttempt_minJitter_isHalfBase() {
        assertEquals(500, Backoff.delayMillis(1, 1000, 30000, 0.0));
    }

    @Test
    void moreJitter_yieldsLongerDelay() {
        long low = Backoff.delayMillis(2, 1000, 30000, 0.0);  // 2000 * 0.5 = 1000
        long high = Backoff.delayMillis(2, 1000, 30000, 1.0); // 2000 * 1.0 = 2000
        assertTrue(high > low);
    }
}
