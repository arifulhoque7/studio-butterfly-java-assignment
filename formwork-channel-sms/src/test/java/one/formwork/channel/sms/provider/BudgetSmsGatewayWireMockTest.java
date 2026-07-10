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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetSmsGatewayWireMockTest {
    private final UUID tenantId = UUID.randomUUID();

    private BudgetSmsGateway gateway;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() throws Exception {
        SmsChannelProperties.BudgetSmsProperties config = new SmsChannelProperties.BudgetSmsProperties();
        config.setUsername("user");
        config.setPassword("pass");
        config.setOriginator("TestSMS");
        gateway = new BudgetSmsGateway(config);
        var field = BudgetSmsGateway.class.getDeclaredField("webClient");
        field.setAccessible(true);
        field.set(gateway, webClient);
    }

    @SuppressWarnings("unchecked")
    @Test
    void send_OkResponse_ReturnsSuccess() {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString(), any(Object[].class));
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("OK 12345"));

        SmsResult result = gateway.send(new SmsMessage("+49151", "Hallo", tenantId));
        assertTrue(result.isSuccess());
    }

    @SuppressWarnings("unchecked")
    @Test
    void send_ErrorResponse_ReturnsFailure() {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString(), any(Object[].class));
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("ERR 101 Invalid credentials"));

        SmsResult result = gateway.send(new SmsMessage("+49151", "Hi", tenantId));
        assertFalse(result.isSuccess());
    }

    @SuppressWarnings("unchecked")
    @Test
    void send_Exception_ReturnsFailure() {
        when(webClient.get()).thenThrow(new RuntimeException("timeout"));
        SmsResult result = gateway.send(new SmsMessage("+49151", "Hi", tenantId));
        assertFalse(result.isSuccess());
    }

    @Test
    void supports_BudgetSms_ReturnsTrue() { assertTrue(gateway.supports("BUDGET_SMS")); }
    @Test
    void getProviderName_ReturnsBudgetSms() { assertEquals("BUDGET_SMS", gateway.getProviderName()); }
}