package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class BudgetSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(BudgetSmsGateway.class);
    private static final String BUDGETSMS_API_URL = "https://api.budgetsms.net/sendsms";

    private final WebClient webClient;
    private final SmsChannelProperties.BudgetSmsProperties config;

    public BudgetSmsGateway(SmsChannelProperties.BudgetSmsProperties config) {
        this.config = config;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public SmsResult send(SmsMessage message) {
        try {
            String response = webClient.get()
                    .uri(BUDGETSMS_API_URL
                            + "?username={user}&password={pass}&from={from}&to={to}&msg={msg}",
                            config.getUsername(),
                            config.getPassword(),
                            config.getOriginator(),
                            message.to(),
                            message.body())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null && response.startsWith("OK")) {
                String messageId = response.length() > 3 ? response.substring(3).trim() : response;
                log.info("BudgetSMS sent: messageId={}, to={}", messageId, message.to());
                return SmsResult.success(messageId, "BUDGET_SMS", 1);
            } else {
                log.error("BudgetSMS error response: {}", response);
                return SmsResult.failure("BUDGET_SMS", "API_ERROR", response);
            }
        } catch (WebClientResponseException e) {
            log.error("BudgetSMS API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return SmsResult.failure("BUDGET_SMS", String.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("BudgetSMS send failed: {}", e.getMessage(), e);
            return SmsResult.failure("BUDGET_SMS", "SEND_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean supports(String providerType) {
        return "BUDGET_SMS".equalsIgnoreCase(providerType);
    }

    @Override
    public String getProviderName() {
        return "BUDGET_SMS";
    }
}
