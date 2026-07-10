package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.SmsChannelProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AwsSnsSmsGatewayTest {

    private AwsSnsSmsGateway createGateway() {
        SmsChannelProperties.AwsSnsProperties config = new SmsChannelProperties.AwsSnsProperties();
        config.setRegion("eu-central-1");
        config.setSenderId("TestApp");
        return new AwsSnsSmsGateway(config);
    }

    @Test
    void supports_AwsSns_ReturnsTrue() {
        assertTrue(createGateway().supports("AWS_SNS"));
    }

    @Test
    void supports_AwsSnsCaseInsensitive_ReturnsTrue() {
        assertTrue(createGateway().supports("aws_sns"));
    }

    @Test
    void supports_OtherProvider_ReturnsFalse() {
        assertFalse(createGateway().supports("TWILIO"));
    }

    @Test
    void getProviderName_ReturnsExpected() {
        assertEquals("AWS_SNS", createGateway().getProviderName());
    }
}
