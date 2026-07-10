package one.formwork.channel.sms.reliability;

import one.formwork.channel.sms.api.SmsChannelProperties;
import one.formwork.channel.sms.api.SmsGateway;
import one.formwork.channel.sms.api.SmsMessage;
import one.formwork.channel.sms.api.SmsResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Finding 7: retry only transient failures, cap attempts, never wait in tests. */
class RetryableSenderTest {

    private final Sleeper noSleep = millis -> { };

    private RetryableSender sender(int maxAttempts) {
        SmsChannelProperties.RetryProperties r = new SmsChannelProperties.RetryProperties();
        r.setMaxAttempts(maxAttempts);
        r.setBackoff("1ms");
        r.setMaxBackoff("2ms");
        return new RetryableSender(r, noSleep, () -> 0.0);
    }

    private SmsMessage msg() {
        return new SmsMessage("+4915112345678", "Hi", UUID.randomUUID());
    }

    @Test
    void retriesTransientFailure_untilSuccess() {
        SmsGateway gw = mock(SmsGateway.class);
        when(gw.send(any()))
                .thenReturn(SmsResult.failure("P", "500", "x"))
                .thenReturn(SmsResult.success("id", "P", 1));

        SmsResult result = sender(3).send(gw, msg());

        assertTrue(result.isSuccess());
        verify(gw, times(2)).send(any());
    }

    @Test
    void stopsAfterMaxAttempts_onPersistentTransientFailure() {
        SmsGateway gw = mock(SmsGateway.class);
        when(gw.send(any())).thenReturn(SmsResult.failure("P", "503", "x"));

        SmsResult result = sender(3).send(gw, msg());

        assertFalse(result.isSuccess());
        verify(gw, times(3)).send(any());
    }

    @Test
    void doesNotRetry_deterministicFailure() {
        SmsGateway gw = mock(SmsGateway.class);
        when(gw.send(any())).thenReturn(SmsResult.failure("P", "400", "bad"));

        sender(3).send(gw, msg());

        verify(gw, times(1)).send(any());
    }

    @Test
    void isRetryable_classifiesFailures() {
        assertTrue(RetryableSender.isRetryable(SmsResult.failure("P", "SEND_ERROR", "x")));
        assertTrue(RetryableSender.isRetryable(SmsResult.failure("P", "503", "x")));
        assertTrue(RetryableSender.isRetryable(SmsResult.failure("P", "429", "x")));
        assertFalse(RetryableSender.isRetryable(SmsResult.failure("P", "400", "x")));
        assertFalse(RetryableSender.isRetryable(SmsResult.failure("P", "CONFIG_ERROR", "x")));
        assertFalse(RetryableSender.isRetryable(SmsResult.failure("P", "EMPTY_RESPONSE", "x")));
    }
}
