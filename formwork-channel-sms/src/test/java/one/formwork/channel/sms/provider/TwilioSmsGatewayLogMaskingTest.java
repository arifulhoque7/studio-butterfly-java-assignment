package one.formwork.channel.sms.provider;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import one.formwork.channel.sms.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for PII leakage into logs (REVIEW.md Finding 3).
 * RED on the original code (logs {@code to={}} with the full MSISDN);
 * GREEN once the recipient is masked before logging.
 */
@ExtendWith(MockitoExtension.class)
class TwilioSmsGatewayLogMaskingTest {

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private TwilioSmsGateway gateway;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() throws Exception {
        SmsChannelProperties.TwilioProperties config = new SmsChannelProperties.TwilioProperties();
        config.setAccountSid("AC123");
        config.setAuthToken("token");
        config.setFromNumber("+15551234567");
        gateway = new TwilioSmsGateway(config);
        var field = TwilioSmsGateway.class.getDeclaredField("webClient");
        field.setAccessible(true);
        field.set(gateway, webClient);

        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(TwilioSmsGateway.class)).addAppender(appender);
    }

    @Test
    void send_success_doesNotLogRawRecipient() {
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString(), any(Object[].class));
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.just(Map.of("sid", "SM1", "num_segments", "1")));

        gateway.send(new SmsMessage("+4915112345678", "Hi", UUID.randomUUID()));

        boolean leaked = appender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("4915112345678"));
        assertFalse(leaked, "raw recipient number must never appear in logs");
    }
}
