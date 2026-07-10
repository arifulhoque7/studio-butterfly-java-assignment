package one.formwork.channel.sms.api;

import java.util.UUID;
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

    private SmsChannelService service;

    @BeforeEach
    void setUp() {
        service = new SmsChannelService(List.of(twilioGateway, vonageGateway), properties);
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
}
