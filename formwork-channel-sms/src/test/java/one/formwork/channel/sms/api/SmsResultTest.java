package one.formwork.channel.sms.api;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class SmsResultTest {

    @Nested
    class FactoryMethods {

        @Test
        void success_ValidArgs_CreatesAcceptedResult() {
            SmsResult result = SmsResult.success("msg-1", "TWILIO", 2);

            assertEquals("msg-1", result.messageId());
            assertEquals("TWILIO", result.provider());
            assertEquals(DeliveryStatus.ACCEPTED, result.status());
            assertEquals(2, result.segmentCount());
            assertNull(result.cost());
            assertNull(result.errorCode());
            assertNull(result.errorMessage());
        }

        @Test
        void failure_ValidArgs_CreatesFailedResult() {
            SmsResult result = SmsResult.failure("VONAGE", "401", "Unauthorized");

            assertNull(result.messageId());
            assertEquals("VONAGE", result.provider());
            assertEquals(DeliveryStatus.FAILED, result.status());
            assertEquals(0, result.segmentCount());
            assertEquals("401", result.errorCode());
            assertEquals("Unauthorized", result.errorMessage());
        }
    }

    @Nested
    class IsSuccess {

        @ParameterizedTest
        @EnumSource(value = DeliveryStatus.class, names = {"ACCEPTED", "SENT", "DELIVERED"})
        void isSuccess_SuccessStatuses_ReturnsTrue(DeliveryStatus status) {
            SmsResult result = new SmsResult("id", "P", status, 1, null, null, null);
            assertTrue(result.isSuccess());
        }

        @ParameterizedTest
        @EnumSource(value = DeliveryStatus.class, names = {"FAILED", "REJECTED"})
        void isSuccess_FailureStatuses_ReturnsFalse(DeliveryStatus status) {
            SmsResult result = new SmsResult("id", "P", status, 1, null, null, null);
            assertFalse(result.isSuccess());
        }
    }
}
