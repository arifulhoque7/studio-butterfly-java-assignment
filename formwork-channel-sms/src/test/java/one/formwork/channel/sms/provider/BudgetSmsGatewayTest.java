package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.SmsChannelProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BudgetSmsGatewayTest {

    private BudgetSmsGateway createGateway() {
        SmsChannelProperties.BudgetSmsProperties config = new SmsChannelProperties.BudgetSmsProperties();
        config.setUsername("testuser");
        config.setPassword("testpass");
        config.setOriginator("TestApp");
        return new BudgetSmsGateway(config);
    }

    @Test
    void supports_BudgetSms_ReturnsTrue() {
        assertTrue(createGateway().supports("BUDGET_SMS"));
    }

    @Test
    void supports_BudgetSmsCaseInsensitive_ReturnsTrue() {
        assertTrue(createGateway().supports("budget_sms"));
    }

    @Test
    void supports_OtherProvider_ReturnsFalse() {
        assertFalse(createGateway().supports("TWILIO"));
    }

    @Test
    void getProviderName_ReturnsExpected() {
        assertEquals("BUDGET_SMS", createGateway().getProviderName());
    }
}
