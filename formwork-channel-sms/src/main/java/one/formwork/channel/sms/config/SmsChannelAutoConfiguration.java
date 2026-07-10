package one.formwork.channel.sms.config;

import one.formwork.channel.sms.api.*;
import one.formwork.channel.sms.provider.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import one.formwork.base.tenant.config.TenantBaseAutoConfiguration;

@AutoConfiguration(after = TenantBaseAutoConfiguration.class)
@ConditionalOnProperty(prefix = "formwork.sms-channel", name = "provider")
@EnableConfigurationProperties(SmsChannelProperties.class)

public class SmsChannelAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "formwork.sms-channel", name = "provider", havingValue = "TWILIO")
    public SmsGateway twilioGateway(SmsChannelProperties props) {
        return new TwilioSmsGateway(props.getTwilio());
    }

    @Bean
    @ConditionalOnProperty(prefix = "formwork.sms-channel", name = "provider", havingValue = "VONAGE")
    public SmsGateway vonageGateway(SmsChannelProperties props) {
        return new VonageSmsGateway(props.getVonage());
    }

    @Bean
    @ConditionalOnProperty(prefix = "formwork.sms-channel", name = "provider", havingValue = "AWS_SNS")
    public SmsGateway awsSnsGateway(SmsChannelProperties props) {
        return new AwsSnsSmsGateway(props.getAwsSns());
    }

    @Bean
    @ConditionalOnProperty(prefix = "formwork.sms-channel", name = "provider", havingValue = "BUDGET_SMS")
    public SmsGateway budgetSmsGateway(SmsChannelProperties props) {
        return new BudgetSmsGateway(props.getBudgetSms());
    }

    @Bean
    @ConditionalOnProperty(prefix = "formwork.sms-channel", name = "provider", havingValue = "MESSAGEBIRD")
    public SmsGateway messageBirdGateway(SmsChannelProperties props) {
        return new MessageBirdSmsGateway(props.getMessagebird());
    }

}
