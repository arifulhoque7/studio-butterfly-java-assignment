package one.formwork.channel.sms.api;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmsChannelPropertiesTest {

    @Test
    void provider_Default_IsTwilio() {
        SmsChannelProperties props = new SmsChannelProperties();
        assertEquals("TWILIO", props.getProvider());
    }

    @Test
    void provider_SetCustom_ReturnsCustom() {
        SmsChannelProperties props = new SmsChannelProperties();
        props.setProvider("VONAGE");
        assertEquals("VONAGE", props.getProvider());
    }

    @Test
    void nestedProperties_NotNull_ByDefault() {
        SmsChannelProperties props = new SmsChannelProperties();
        assertNotNull(props.getTwilio());
        assertNotNull(props.getVonage());
        assertNotNull(props.getAwsSns());
        assertNotNull(props.getBudgetSms());
        assertNotNull(props.getMessagebird());
        assertNotNull(props.getRetry());
    }

    @Test
    void setNestedProperties_AllNested_RoundtripCorrectly() {
        SmsChannelProperties props = new SmsChannelProperties();
        SmsChannelProperties.TwilioProperties twilio = new SmsChannelProperties.TwilioProperties();
        SmsChannelProperties.VonageProperties vonage = new SmsChannelProperties.VonageProperties();
        SmsChannelProperties.AwsSnsProperties awsSns = new SmsChannelProperties.AwsSnsProperties();
        SmsChannelProperties.BudgetSmsProperties budget = new SmsChannelProperties.BudgetSmsProperties();
        SmsChannelProperties.MessageBirdProperties mb = new SmsChannelProperties.MessageBirdProperties();
        SmsChannelProperties.RetryProperties retry = new SmsChannelProperties.RetryProperties();

        props.setTwilio(twilio);
        props.setVonage(vonage);
        props.setAwsSns(awsSns);
        props.setBudgetSms(budget);
        props.setMessagebird(mb);
        props.setRetry(retry);

        assertSame(twilio, props.getTwilio());
        assertSame(vonage, props.getVonage());
        assertSame(awsSns, props.getAwsSns());
        assertSame(budget, props.getBudgetSms());
        assertSame(mb, props.getMessagebird());
        assertSame(retry, props.getRetry());
    }

    @Nested
    class TwilioPropertiesTest {

        @Test
        void twilio_Setters_RoundtripCorrectly() {
            SmsChannelProperties.TwilioProperties twilio = new SmsChannelProperties.TwilioProperties();
            twilio.setAccountSid("AC123");
            twilio.setAuthToken("tok456");
            twilio.setFromNumber("+15551234567");

            assertEquals("AC123", twilio.getAccountSid());
            assertEquals("tok456", twilio.getAuthToken());
            assertEquals("+15551234567", twilio.getFromNumber());
        }

        @Test
        void twilio_DefaultValues_AreNull() {
            SmsChannelProperties.TwilioProperties twilio = new SmsChannelProperties.TwilioProperties();
            assertNull(twilio.getAccountSid());
            assertNull(twilio.getAuthToken());
            assertNull(twilio.getFromNumber());
        }
    }

    @Nested
    class VonagePropertiesTest {

        @Test
        void vonage_Setters_RoundtripCorrectly() {
            SmsChannelProperties.VonageProperties vonage = new SmsChannelProperties.VonageProperties();
            vonage.setApiKey("key-abc");
            vonage.setApiSecret("sec-xyz");
            vonage.setFromNumber("+49123456789");

            assertEquals("key-abc", vonage.getApiKey());
            assertEquals("sec-xyz", vonage.getApiSecret());
            assertEquals("+49123456789", vonage.getFromNumber());
        }
    }

    @Nested
    class AwsSnsPropertiesTest {

        @Test
        void awsSns_DefaultRegion_IsEuCentral1() {
            SmsChannelProperties.AwsSnsProperties aws = new SmsChannelProperties.AwsSnsProperties();
            assertEquals("eu-central-1", aws.getRegion());
        }

        @Test
        void awsSns_CustomRegion_IsStored() {
            SmsChannelProperties.AwsSnsProperties aws = new SmsChannelProperties.AwsSnsProperties();
            aws.setRegion("us-east-1");
            aws.setSenderId("MySender");

            assertEquals("us-east-1", aws.getRegion());
            assertEquals("MySender", aws.getSenderId());
        }
    }

    @Nested
    class BudgetSmsPropertiesTest {

        @Test
        void budgetSms_Setters_RoundtripCorrectly() {
            SmsChannelProperties.BudgetSmsProperties budget = new SmsChannelProperties.BudgetSmsProperties();
            budget.setUsername("user1");
            budget.setPassword("pass1");
            budget.setOriginator("MyApp");

            assertEquals("user1", budget.getUsername());
            assertEquals("pass1", budget.getPassword());
            assertEquals("MyApp", budget.getOriginator());
        }
    }

    @Nested
    class MessageBirdPropertiesTest {

        @Test
        void messageBird_Setters_RoundtripCorrectly() {
            SmsChannelProperties.MessageBirdProperties mb = new SmsChannelProperties.MessageBirdProperties();
            mb.setAccessKey("access-key-123");
            mb.setOriginator("FormworkApp");

            assertEquals("access-key-123", mb.getAccessKey());
            assertEquals("FormworkApp", mb.getOriginator());
        }
    }

    @Nested
    class RetryPropertiesTest {

        @Test
        void retry_Defaults_AreCorrect() {
            SmsChannelProperties.RetryProperties retry = new SmsChannelProperties.RetryProperties();
            assertEquals(3, retry.getMaxAttempts());
            assertEquals("5s", retry.getBackoff());
        }

        @Test
        void retry_CustomValues_AreApplied() {
            SmsChannelProperties.RetryProperties retry = new SmsChannelProperties.RetryProperties();
            retry.setMaxAttempts(5);
            retry.setBackoff("10s");
            assertEquals(5, retry.getMaxAttempts());
            assertEquals("10s", retry.getBackoff());
        }
    }
}
