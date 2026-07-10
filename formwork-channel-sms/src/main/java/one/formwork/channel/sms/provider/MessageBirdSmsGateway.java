package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

public class MessageBirdSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(MessageBirdSmsGateway.class);
    private static final String MESSAGEBIRD_API_URL = "https://rest.messagebird.com";

    private final WebClient webClient;
    private final SmsChannelProperties.MessageBirdProperties config;

    public MessageBirdSmsGateway(SmsChannelProperties.MessageBirdProperties config) {
        this.config = config;
        this.webClient = WebClient.builder()
                .baseUrl(MESSAGEBIRD_API_URL)
                .defaultHeader("Authorization", "AccessKey " + config.getAccessKey())
                .build();
    }

    @Override
    public SmsResult send(SmsMessage message) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "originator", config.getOriginator(),
                    "recipients", List.of(message.to()),
                    "body", message.body()
            );

            Map<?, ?> response = webClient.post()
                    .uri("/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String messageId = response != null ? String.valueOf(response.get("id")) : null;
            log.info("MessageBird SMS sent: messageId={}, to={}", messageId, message.to());
            return SmsResult.success(messageId, "MESSAGEBIRD", 1);
        } catch (WebClientResponseException e) {
            log.error("MessageBird API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return SmsResult.failure("MESSAGEBIRD", String.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("MessageBird SMS send failed: {}", e.getMessage(), e);
            return SmsResult.failure("MESSAGEBIRD", "SEND_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean supports(String providerType) {
        return "MESSAGEBIRD".equalsIgnoreCase(providerType);
    }

    @Override
    public String getProviderName() {
        return "MESSAGEBIRD";
    }
}
