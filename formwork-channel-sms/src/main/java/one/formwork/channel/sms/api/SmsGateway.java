package one.formwork.channel.sms.api;

public interface SmsGateway {
    SmsResult send(SmsMessage message);
    boolean supports(String providerType);
    String getProviderName();
}
