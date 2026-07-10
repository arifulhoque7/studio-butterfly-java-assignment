package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

public class VonageSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(VonageSmsGateway.class);
    private static final String VONAGE_API_URL = "https://rest.nexmo.com";

    private final WebClient webClient;
    private final SmsChannelProperties.VonageProperties config;

    public VonageSmsGateway(SmsChannelProperties.VonageProperties config) {
        this.config = config;
        this.webClient = WebClient.builder()
                .baseUrl(VONAGE_API_URL)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public SmsResult send(SmsMessage message) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "api_key", config.getApiKey(),
                    "api_secret", config.getApiSecret(),
                    "from", config.getFromNumber(),
                    "to", message.to(),
                    "text", message.body()
            );

            Map<?, ?> response = webClient.post()
                    .uri("/sms/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("messages")) {
                List<Map<String, Object>> messages = (List<Map<String, Object>>) response.get("messages");
                if (!messages.isEmpty()) {
                    Map<String, Object> first = messages.getFirst();
                    String status = String.valueOf(first.get("status"));
                    if ("0".equals(status)) {
                        String messageId = String.valueOf(first.get("message-id"));
                        log.info("Vonage SMS sent: messageId={}, to={}", messageId, message.to());
                        return SmsResult.success(messageId, "VONAGE", messages.size());
                    } else {
                        String errorText = String.valueOf(first.get("error-text"));
                        log.error("Vonage API error: status={}, error={}", status, errorText);
                        return SmsResult.failure("VONAGE", status, errorText);
                    }
                }
            }

            return SmsResult.failure("VONAGE", "EMPTY_RESPONSE", "No messages in response");
        } catch (WebClientResponseException e) {
            log.error("Vonage API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return SmsResult.failure("VONAGE", String.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Vonage SMS send failed: {}", e.getMessage(), e);
            return SmsResult.failure("VONAGE", "SEND_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean supports(String providerType) {
        return "VONAGE".equalsIgnoreCase(providerType);
    }

    @Override
    public String getProviderName() {
        return "VONAGE";
    }
}
