package one.formwork.channel.sms.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "formwork.sms-channel")
public class SmsChannelProperties {
    private String provider = "TWILIO";
    private TwilioProperties twilio = new TwilioProperties();
    private VonageProperties vonage = new VonageProperties();
    private AwsSnsProperties awsSns = new AwsSnsProperties();
    private BudgetSmsProperties budgetSms = new BudgetSmsProperties();
    private MessageBirdProperties messagebird = new MessageBirdProperties();
    private RetryProperties retry = new RetryProperties();

    public String getProvider() { return provider; }
    public void setProvider(String p) { this.provider = p; }
    public TwilioProperties getTwilio() { return twilio; }
    public void setTwilio(TwilioProperties t) { this.twilio = t; }
    public VonageProperties getVonage() { return vonage; }
    public void setVonage(VonageProperties v) { this.vonage = v; }
    public AwsSnsProperties getAwsSns() { return awsSns; }
    public void setAwsSns(AwsSnsProperties a) { this.awsSns = a; }
    public BudgetSmsProperties getBudgetSms() { return budgetSms; }
    public void setBudgetSms(BudgetSmsProperties b) { this.budgetSms = b; }
    public MessageBirdProperties getMessagebird() { return messagebird; }
    public void setMessagebird(MessageBirdProperties m) { this.messagebird = m; }
    public RetryProperties getRetry() { return retry; }
    public void setRetry(RetryProperties r) { this.retry = r; }

    public static class TwilioProperties {
        private String accountSid; private String authToken; private String fromNumber;
        public String getAccountSid() { return accountSid; } public void setAccountSid(String s) { this.accountSid = s; }
        public String getAuthToken() { return authToken; } public void setAuthToken(String s) { this.authToken = s; }
        public String getFromNumber() { return fromNumber; } public void setFromNumber(String s) { this.fromNumber = s; }
    }
    public static class VonageProperties {
        private String apiKey; private String apiSecret; private String fromNumber;
        public String getApiKey() { return apiKey; } public void setApiKey(String s) { this.apiKey = s; }
        public String getApiSecret() { return apiSecret; } public void setApiSecret(String s) { this.apiSecret = s; }
        public String getFromNumber() { return fromNumber; } public void setFromNumber(String s) { this.fromNumber = s; }
    }
    public static class AwsSnsProperties {
        private String region = "eu-central-1"; private String senderId;
        public String getRegion() { return region; } public void setRegion(String s) { this.region = s; }
        public String getSenderId() { return senderId; } public void setSenderId(String s) { this.senderId = s; }
    }
    public static class BudgetSmsProperties {
        private String username; private String password; private String originator;
        public String getUsername() { return username; } public void setUsername(String s) { this.username = s; }
        public String getPassword() { return password; } public void setPassword(String s) { this.password = s; }
        public String getOriginator() { return originator; } public void setOriginator(String s) { this.originator = s; }
    }
    public static class MessageBirdProperties {
        private String accessKey; private String originator;
        public String getAccessKey() { return accessKey; } public void setAccessKey(String s) { this.accessKey = s; }
        public String getOriginator() { return originator; } public void setOriginator(String s) { this.originator = s; }
    }
    public static class RetryProperties {
        private int maxAttempts = 3; private String backoff = "5s";
        public int getMaxAttempts() { return maxAttempts; } public void setMaxAttempts(int m) { this.maxAttempts = m; }
        public String getBackoff() { return backoff; } public void setBackoff(String b) { this.backoff = b; }
    }
}
