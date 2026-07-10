package one.formwork.channel.sms.api;

import java.util.UUID;
import one.formwork.channel.sms.cost.SmsCostService;
import one.formwork.channel.sms.validation.PhoneNumberValidator.InvalidPhoneNumberException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsChannelServiceTest {
    private final UUID tenantId = UUID.randomUUID();

    @Mock
    private SmsGateway twilioGateway;

    @Mock
    private SmsGateway vonageGateway;

    @Mock
    private SmsChannelProperties properties;

    @Mock
    private SmsCostService costService;

    private SmsChannelService service;

    @BeforeEach
    void setUp() {
        // No-op sleeper + fixed jitter so retry/backoff runs instantly in unit tests.
        service = new SmsChannelService(List.of(twilioGateway, vonageGateway), properties, costService,
                millis -> { }, () -> 0.0);
    }

    @Nested
    class SendSms {

        @Test
        void sendSms_ValidMessage_DelegatesToResolvedGateway() {
            when(properties.getProvider()).thenReturn("TWILIO");
            when(twilioGateway.supports("TWILIO")).thenReturn(true);
            SmsResult expected = SmsResult.success("msg-123", "TWILIO", 1);
            when(twilioGateway.send(any(SmsMessage.class))).thenReturn(expected);

            SmsMessage message = new SmsMessage("+4915112345678", "Hello", tenantId);
            SmsResult result = service.sendSms(message);

            assertEquals(expected, result);
            verify(twilioGateway).send(message);
            verify(vonageGateway, never()).send(any());
        }

        @Test
        void sendSms_success_recordsCostOnce() {
            // RED on original: sendSms never called SmsCostService, so no cost was ever recorded.
            when(properties.getProvider()).thenReturn("TWILIO");
            when(twilioGateway.supports("TWILIO")).thenReturn(true);
            SmsResult ok = SmsResult.success("SM1", "TWILIO", 2);
            when(twilioGateway.send(any(SmsMessage.class))).thenReturn(ok);

            SmsMessage message = new SmsMessage("+4915112345678", "Hello there", tenantId);
            service.sendSms(message);

            verify(costService).recordCost(tenantId, "+4915112345678", ok);
        }

        @Test
        void sendSms_failure_doesNotRecordCost() {
            when(properties.getProvider()).thenReturn("TWILIO");
            when(twilioGateway.supports("TWILIO")).thenReturn(true);
            when(twilioGateway.send(any(SmsMessage.class)))
                    .thenReturn(SmsResult.failure("TWILIO", "500", "Server Error"));

            service.sendSms(new SmsMessage("+4915112345678", "Hello", tenantId));

            verify(costService, never()).recordCost(any(), any(), any());
        }

        @Test
        void sendSms_VonageProvider_UsesVonageGateway() {
            when(properties.getProvider()).thenReturn("VONAGE");
            when(twilioGateway.supports("VONAGE")).thenReturn(false);
            when(vonageGateway.supports("VONAGE")).thenReturn(true);
            SmsResult expected = SmsResult.success("msg-456", "VONAGE", 1);
            when(vonageGateway.send(any(SmsMessage.class))).thenReturn(expected);

            SmsMessage message = new SmsMessage("+4915112345678", "Hello", tenantId);
            SmsResult result = service.sendSms(message);

            assertEquals(expected, result);
            verify(vonageGateway).send(message);
        }

        @Test
        void sendSms_InvalidPhoneNumber_ThrowsBeforeGatewayCall() {
            SmsMessage message = new SmsMessage("invalid-number", "Hello", tenantId);

            assertThrows(InvalidPhoneNumberException.class, () -> service.sendSms(message));
            verify(twilioGateway, never()).send(any());
            verify(vonageGateway, never()).send(any());
        }

        @Test
        void sendSms_NoMatchingGateway_ThrowsIllegalStateException() {
            when(properties.getProvider()).thenReturn("UNKNOWN");
            when(twilioGateway.supports("UNKNOWN")).thenReturn(false);
            when(vonageGateway.supports("UNKNOWN")).thenReturn(false);

            SmsMessage message = new SmsMessage("+4915112345678", "Hello", tenantId);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.sendSms(message));
            assertTrue(ex.getMessage().contains("UNKNOWN"));
        }
    }

    @Nested
    class SendBulk {

        @Test
        void sendBulk_MultipleMessages_SendsEachIndividually() {
            when(properties.getProvider()).thenReturn("TWILIO");
            when(twilioGateway.supports("TWILIO")).thenReturn(true);
            SmsResult success = SmsResult.success("msg-1", "TWILIO", 1);
            when(twilioGateway.send(any(SmsMessage.class))).thenReturn(success);

            List<SmsMessage> messages = List.of(
                    new SmsMessage("+4915112345678", "Hello 1", tenantId),
                    new SmsMessage("+4915112345679", "Hello 2", tenantId),
                    new SmsMessage("+4915112345670", "Hello 3", tenantId)
            );

            List<SmsResult> results = service.sendBulk(messages);

            assertEquals(3, results.size());
            verify(twilioGateway, times(3)).send(any(SmsMessage.class));
        }

        @Test
        void sendBulk_EmptyList_ReturnsEmptyList() {
            List<SmsResult> results = service.sendBulk(List.of());
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    class HandleDeliveryCallback {

        @Test
        void handleDeliveryCallback_AnyInput_DoesNotThrow() {
            assertDoesNotThrow(() ->
                    service.handleDeliveryCallback("TWILIO", Map.of("status", "delivered")));
        }
    }

    @Nested
    class TenantRoutingAndFailover {

        private SmsChannelService serviceWith(SmsChannelProperties props) {
            return new SmsChannelService(List.of(twilioGateway, vonageGateway), props, costService,
                    millis -> { }, () -> 0.0);
        }

        private SmsChannelProperties props() {
            SmsChannelProperties p = new SmsChannelProperties();
            p.setProvider("TWILIO");
            return p;
        }

        @Test
        void tenantOverride_routesToTenantProvider() {
            SmsChannelProperties p = props();
            p.setTenantProviders(Map.of(tenantId.toString(), "VONAGE"));
            when(vonageGateway.supports("VONAGE")).thenReturn(true);
            SmsResult ok = SmsResult.success("VM1", "VONAGE", 1);
            when(vonageGateway.send(any())).thenReturn(ok);

            SmsResult result = serviceWith(p).sendSms(new SmsMessage("+4915112345678", "Hi", tenantId));

            assertEquals("VONAGE", result.provider());
            verify(vonageGateway).send(any());
            verify(twilioGateway, never()).send(any());
        }

        @Test
        void tenantWithoutOverride_usesGlobalProvider() {
            SmsChannelProperties p = props(); // no overrides
            when(twilioGateway.supports("TWILIO")).thenReturn(true);
            when(twilioGateway.send(any())).thenReturn(SmsResult.success("SM1", "TWILIO", 1));

            SmsResult result = serviceWith(p).sendSms(new SmsMessage("+4915112345678", "Hi", tenantId));

            assertEquals("TWILIO", result.provider());
            verify(vonageGateway, never()).send(any());
        }

        @Test
        void oneTenantOverride_doesNotAffectAnotherTenant() {
            UUID otherTenant = UUID.randomUUID();
            SmsChannelProperties p = props();
            p.setTenantProviders(Map.of(otherTenant.toString(), "VONAGE")); // override for a DIFFERENT tenant
            when(twilioGateway.supports("TWILIO")).thenReturn(true);
            when(twilioGateway.send(any())).thenReturn(SmsResult.success("SM1", "TWILIO", 1));

            SmsResult result = serviceWith(p).sendSms(new SmsMessage("+4915112345678", "Hi", tenantId));

            assertEquals("TWILIO", result.provider(), "this tenant must stay on the global provider");
            verify(vonageGateway, never()).send(any());
        }

        @Test
        void failover_primaryFails_secondarySucceeds() {
            SmsChannelProperties p = props();
            p.setFailover(List.of("VONAGE"));
            when(twilioGateway.supports("TWILIO")).thenReturn(true);
            when(twilioGateway.send(any())).thenReturn(SmsResult.failure("TWILIO", "400", "bad"));
            when(vonageGateway.supports("VONAGE")).thenReturn(true);
            SmsResult ok = SmsResult.success("VM1", "VONAGE", 1);
            when(vonageGateway.send(any())).thenReturn(ok);

            SmsResult result = serviceWith(p).sendSms(new SmsMessage("+4915112345678", "Hi", tenantId));

            assertTrue(result.isSuccess());
            assertEquals("VONAGE", result.provider());
            verify(twilioGateway).send(any());
            verify(vonageGateway).send(any());
            verify(costService).recordCost(tenantId, "+4915112345678", ok);
        }

        @Test
        void retry_transientFailureThenSuccess_retriesSameProvider() {
            SmsChannelProperties p = props(); // maxAttempts default 3
            when(twilioGateway.supports("TWILIO")).thenReturn(true);
            when(twilioGateway.send(any()))
                    .thenReturn(SmsResult.failure("TWILIO", "503", "unavailable"))
                    .thenReturn(SmsResult.success("SM1", "TWILIO", 1));

            SmsResult result = serviceWith(p).sendSms(new SmsMessage("+4915112345678", "Hi", tenantId));

            assertTrue(result.isSuccess());
            verify(twilioGateway, times(2)).send(any()); // 503 retried once, then succeeded
        }

        @Test
        void retry_nonRetryableFailure_isNotRetried() {
            SmsChannelProperties p = props();
            when(twilioGateway.supports("TWILIO")).thenReturn(true);
            when(twilioGateway.send(any())).thenReturn(SmsResult.failure("TWILIO", "400", "bad request"));

            SmsResult result = serviceWith(p).sendSms(new SmsMessage("+4915112345678", "Hi", tenantId));

            assertFalse(result.isSuccess());
            verify(twilioGateway, times(1)).send(any()); // 400 is deterministic: no retry
            verify(costService, never()).recordCost(any(), any(), any());
        }
    }
}
