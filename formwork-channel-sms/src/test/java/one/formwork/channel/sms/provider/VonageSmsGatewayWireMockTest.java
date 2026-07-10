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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VonageSmsGatewayWireMockTest {
    private final UUID tenantId = UUID.randomUUID();

    private VonageSmsGateway gateway;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() throws Exception {
        SmsChannelProperties.VonageProperties config = new SmsChannelProperties.VonageProperties();
        config.setApiKey("vk");
        config.setApiSecret("vs");
        config.setFromNumber("+4930123456");
        gateway = new VonageSmsGateway(config);
        var field = VonageSmsGateway.class.getDeclaredField("webClient");
        field.setAccessible(true);
        field.set(gateway, webClient);
    }

    @Test
    void send_Success_ReturnsResult() {
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(
                Map.of("messages", List.of(Map.of("status", "0", "message-id", "VM-1")))));

        SmsResult result = gateway.send(new SmsMessage("+49151", "Hallo", tenantId));
        assertTrue(result.isSuccess());
        assertEquals("VM-1", result.messageId());
    }

    @Test
    void send_ApiError_ReturnsFailure() {
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(
                Map.of("messages", List.of(Map.of("status", "4", "error-text", "Invalid credentials")))));

        SmsResult result = gateway.send(new SmsMessage("+49151", "Hi", tenantId));
        assertFalse(result.isSuccess());
    }

    @Test
    void send_EmptyResponse_ReturnsFailure() {
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of()));

        SmsResult result = gateway.send(new SmsMessage("+49151", "Hi", tenantId));
        assertFalse(result.isSuccess());
        assertEquals("EMPTY_RESPONSE", result.errorCode());
    }

    @Test
    void send_HttpError_ReturnsFailure() {
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(Map.class)).thenReturn(
                Mono.error(WebClientResponseException.create(500, "ISE", null, null, null)));

        SmsResult result = gateway.send(new SmsMessage("+49151", "Hi", tenantId));
        assertFalse(result.isSuccess());
    }

    @Test
    void supports_Vonage_ReturnsTrue() { assertTrue(gateway.supports("VONAGE")); }
    @Test
    void getProviderName_ReturnsVonage() { assertEquals("VONAGE", gateway.getProviderName()); }
}