package one.formwork.channel.sms.provider;

import java.util.UUID;
import one.formwork.channel.sms.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageBirdSmsGatewayWireMockTest {
    private final UUID tenantId = UUID.randomUUID();

    private MessageBirdSmsGateway gateway;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() throws Exception {
        SmsChannelProperties.MessageBirdProperties config = new SmsChannelProperties.MessageBirdProperties();
        config.setAccessKey("mb-key");
        config.setOriginator("TestApp");
        gateway = new MessageBirdSmsGateway(config);
        var field = MessageBirdSmsGateway.class.getDeclaredField("webClient");
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
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("id", "mb-msg-1")));

        SmsResult result = gateway.send(new SmsMessage("+49151", "Test", tenantId));
        assertTrue(result.isSuccess());
        assertEquals("mb-msg-1", result.messageId());
    }

    @Test
    void send_Error_ReturnsFailure() {
        when(webClient.post()).thenThrow(new RuntimeException("timeout"));
        SmsResult result = gateway.send(new SmsMessage("+49151", "Test", tenantId));
        assertFalse(result.isSuccess());
    }

    @Test
    void supports_MessageBird_ReturnsTrue() { assertTrue(gateway.supports("MESSAGEBIRD")); }
    @Test
    void getProviderName_ReturnsMessageBird() { assertEquals("MESSAGEBIRD", gateway.getProviderName()); }
}