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

    // All gateways are registered (not just the global provider's) so tenant-aware routing and
    // failover can resolve a secondary provider at runtime. Gateways whose credentials are absent
    // construct harmlessly and only fail at send time, where failover takes over. Part 3 / Finding 8.

    @Bean
    public SmsGateway twilioGateway(SmsChannelProperties props) {
        return new TwilioSmsGateway(props.getTwilio());
    }

    @Bean
    public SmsGateway vonageGateway(SmsChannelProperties props) {
        return new VonageSmsGateway(props.getVonage());
    }

    @Bean
    public SmsGateway awsSnsGateway(SmsChannelProperties props) {
        return new AwsSnsSmsGateway(props.getAwsSns());
    }

    @Bean
    public SmsGateway budgetSmsGateway(SmsChannelProperties props) {
        return new BudgetSmsGateway(props.getBudgetSms());
    }

    @Bean
    public SmsGateway messageBirdGateway(SmsChannelProperties props) {
        return new MessageBirdSmsGateway(props.getMessagebird());
    }

}
