package one.formwork.channel.sms.provider;

import java.util.UUID;
import one.formwork.channel.sms.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AwsSnsSmsGatewayExtraTest {
    private final UUID tenantId = UUID.randomUUID();

    private AwsSnsSmsGateway gateway;

    @BeforeEach
    void setUp() {
        SmsChannelProperties.AwsSnsProperties config = new SmsChannelProperties.AwsSnsProperties();
        config.setRegion("eu-central-1");
        config.setSenderId("TestApp");
        gateway = new AwsSnsSmsGateway(config);
    }

    @Test
    void send_NoAwsCredentials_ReturnsConfigError() {
        // When AWS env vars not set, should return CONFIG_ERROR
        // This test works in CI where AWS_ACCESS_KEY_ID is not set
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        if (accessKey == null) {
            SmsResult result = gateway.send(new SmsMessage("+49151", "Test", tenantId));
            assertFalse(result.isSuccess());
            assertEquals("AWS_SNS", result.provider());
            assertEquals("CONFIG_ERROR", result.errorCode());
        }
    }

    @Test
    void supports_AwsSns_ReturnsTrue() { assertTrue(gateway.supports("AWS_SNS")); }
    @Test
    void supports_Other_ReturnsFalse() { assertFalse(gateway.supports("TWILIO")); }
    @Test
    void getProviderName_ReturnsAwsSns() { assertEquals("AWS_SNS", gateway.getProviderName()); }
}