package one.formwork.channel.sms.provider;

import java.util.UUID;
import one.formwork.channel.sms.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwilioSmsGatewayWireMockTest {
    private final UUID tenantId = UUID.randomUUID();

    private TwilioSmsGateway gateway;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() throws Exception {
        SmsChannelProperties.TwilioProperties config = new SmsChannelProperties.TwilioProperties();
        config.setAccountSid("AC123");
        config.setAuthToken("token");
        config.setFromNumber("+15551234567");
        gateway = new TwilioSmsGateway(config);
        // Inject mock WebClient via field access
        var field = TwilioSmsGateway.class.getDeclaredField("webClient");
        field.setAccessible(true);
        field.set(gateway, webClient);
    }

    @Test
    void send_Success_ReturnsSmsResult() {
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString(), any(Object[].class));
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("sid", "SM123", "num_segments", "2")));

        SmsResult result = gateway.send(new SmsMessage("+4915112345678", "Hello", tenantId));
        assertTrue(result.isSuccess());
        assertEquals("SM123", result.messageId());
        assertEquals(2, result.segmentCount());
    }

    @Test
    void send_ApiError_ReturnsFailure() {
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString(), any(Object[].class));
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(Map.class)).thenReturn(
                Mono.error(WebClientResponseException.create(400, "Bad Request", null, null, null)));

        SmsResult result = gateway.send(new SmsMessage("+49151", "Hi", tenantId));
        assertFalse(result.isSuccess());
        assertEquals("TWILIO", result.provider());
    }

    @Test
    void send_Exception_ReturnsFailure() {
        when(webClient.post()).thenThrow(new RuntimeException("connection refused"));

        SmsResult result = gateway.send(new SmsMessage("+49151", "Hi", tenantId));
        assertFalse(result.isSuccess());
        assertEquals("SEND_ERROR", result.errorCode());
    }

    @Test
    void supports_Twilio_ReturnsTrue() { assertTrue(gateway.supports("TWILIO")); }
    @Test
    void supports_Other_ReturnsFalse() { assertFalse(gateway.supports("VONAGE")); }
    @Test
    void getProviderName_ReturnsTwilio() { assertEquals("TWILIO", gateway.getProviderName()); }
}