package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.SmsChannelProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwilioSmsGatewayTest {

    private TwilioSmsGateway createGateway() {
        SmsChannelProperties.TwilioProperties config = new SmsChannelProperties.TwilioProperties();
        config.setAccountSid("AC_TEST_SID");
        config.setAuthToken("test_auth_token");
        config.setFromNumber("+15005550006");
        return new TwilioSmsGateway(config);
    }

    @Test
    void supports_Twilio_ReturnsTrue() {
        TwilioSmsGateway gateway = createGateway();
        assertTrue(gateway.supports("TWILIO"));
    }

    @Test
    void supports_TwilioCaseInsensitive_ReturnsTrue() {
        TwilioSmsGateway gateway = createGateway();
        assertTrue(gateway.supports("twilio"));
    }

    @Test
    void supports_OtherProvider_ReturnsFalse() {
        TwilioSmsGateway gateway = createGateway();
        assertFalse(gateway.supports("VONAGE"));
    }

    @Test
    void getProviderName_ReturnsExpected() {
        TwilioSmsGateway gateway = createGateway();
        assertEquals("TWILIO", gateway.getProviderName());
    }
}
