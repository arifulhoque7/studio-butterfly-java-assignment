package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.SmsChannelProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VonageSmsGatewayTest {

    private VonageSmsGateway createGateway() {
        SmsChannelProperties.VonageProperties config = new SmsChannelProperties.VonageProperties();
        config.setApiKey("test_key");
        config.setApiSecret("test_secret");
        config.setFromNumber("+4915100000000");
        return new VonageSmsGateway(config);
    }

    @Test
    void supports_Vonage_ReturnsTrue() {
        assertTrue(createGateway().supports("VONAGE"));
    }

    @Test
    void supports_VonageCaseInsensitive_ReturnsTrue() {
        assertTrue(createGateway().supports("vonage"));
    }

    @Test
    void supports_OtherProvider_ReturnsFalse() {
        assertFalse(createGateway().supports("TWILIO"));
    }

    @Test
    void getProviderName_ReturnsExpected() {
        assertEquals("VONAGE", createGateway().getProviderName());
    }
}
