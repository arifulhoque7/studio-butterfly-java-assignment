package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class TwilioSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsGateway.class);
    private static final String TWILIO_API_URL = "https://api.twilio.com/2010-04-01";

    private final WebClient webClient;
    private final SmsChannelProperties.TwilioProperties config;

    public TwilioSmsGateway(SmsChannelProperties.TwilioProperties config) {
        this.config = config;
        String credentials = Base64.getEncoder().encodeToString(
                (config.getAccountSid() + ":" + config.getAuthToken()).getBytes(StandardCharsets.UTF_8));
        this.webClient = WebClient.builder()
                .baseUrl(TWILIO_API_URL)
                .defaultHeader("Authorization", "Basic " + credentials)
                .build();
    }

    @Override
    public SmsResult send(SmsMessage message) {
        try {
            String formBody = "To=" + encode(message.to())
                    + "&From=" + encode(config.getFromNumber())
                    + "&Body=" + encode(message.body());

            Map<?, ?> response = webClient.post()
                    .uri("/Accounts/{sid}/Messages.json", config.getAccountSid())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(formBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String sid = response != null ? String.valueOf(response.get("sid")) : null;
            int segments = 1;
            if (response != null && response.get("num_segments") != null) {
                segments = Integer.parseInt(String.valueOf(response.get("num_segments")));
            }

            log.info("Twilio SMS sent: sid={}, to={}", sid, message.to());
            return SmsResult.success(sid, "TWILIO", segments);
        } catch (WebClientResponseException e) {
            log.error("Twilio API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return SmsResult.failure("TWILIO", String.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Twilio SMS send failed: {}", e.getMessage(), e);
            return SmsResult.failure("TWILIO", "SEND_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean supports(String providerType) {
        return "TWILIO".equalsIgnoreCase(providerType);
    }

    @Override
    public String getProviderName() {
        return "TWILIO";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
