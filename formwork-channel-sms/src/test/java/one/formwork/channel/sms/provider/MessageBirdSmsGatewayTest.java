package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.SmsChannelProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageBirdSmsGatewayTest {

    private MessageBirdSmsGateway createGateway() {
        SmsChannelProperties.MessageBirdProperties config = new SmsChannelProperties.MessageBirdProperties();
        config.setAccessKey("test-access-key");
        config.setOriginator("TestApp");
        return new MessageBirdSmsGateway(config);
    }

    @Test
    void supports_MessageBird_ReturnsTrue() {
        assertTrue(createGateway().supports("MESSAGEBIRD"));
    }

    @Test
    void supports_MessageBirdCaseInsensitive_ReturnsTrue() {
        assertTrue(createGateway().supports("messagebird"));
    }

    @Test
    void supports_OtherProvider_ReturnsFalse() {
        assertFalse(createGateway().supports("TWILIO"));
    }

    @Test
    void getProviderName_ReturnsExpected() {
        assertEquals("MESSAGEBIRD", createGateway().getProviderName());
    }
}
