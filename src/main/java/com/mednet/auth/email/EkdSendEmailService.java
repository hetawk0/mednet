package com.mednet.auth.email;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class EkdSendEmailService {

    private static final Logger log = LoggerFactory.getLogger(EkdSendEmailService.class);
    private static final String DEFAULT_API_URL = "https://es.ekddigital.com/api/v1";
    private static final String DEFAULT_FROM = "support@ekddigital.com";
    private static final String DEFAULT_USER_AGENT = "MedNet/1.0";
    private final RestClient client;
    private final String apiKey;
    private final String from;

    public EkdSendEmailService(
            @Value("${EKDSEND_API_URL:https://es.ekddigital.com/api/v1}") String apiUrl,
            @Value("${EKDSEND_API_KEY:}") String apiKey,
            @Value("${FROM_EMAIL:${EKDSEND_FROM:support@ekddigital.com}}") String from) {
        String configuredApiUrl = apiUrl == null || apiUrl.isBlank() ? DEFAULT_API_URL : apiUrl.trim();
        this.client = RestClient.builder()
                .baseUrl(configuredApiUrl.replaceAll("/+$", ""))
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null || from.isBlank() ? DEFAULT_FROM : from.trim();
    }

    public boolean send(String to, String subject, String html, String text) {
        if (apiKey.isBlank()) {
            log.warn("EKDSend email is not configured; message was not sent");
            return false;
        }
        try {
            EkdSendResponse response = client.post()
                    .uri("/send")
                    .header("x-api-key", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "type", "email",
                            "to", to,
                            "from", from,
                            "subject", subject,
                            "html", html,
                            "body", html,
                            "text", text))
                    .retrieve()
                    .body(EkdSendResponse.class);
            if (response != null && Boolean.FALSE.equals(response.success())) {
                log.warn("EKDSend did not accept the email request");
                return false;
            }
            return true;
        } catch (RestClientResponseException exception) {
            log.warn("EKDSend rejected an email request with HTTP {}", exception.getStatusCode().value());
            return false;
        } catch (RestClientException exception) {
            log.warn("EKDSend email request failed ({})", exception.getClass().getSimpleName());
            return false;
        }
    }

    private record EkdSendResponse(Boolean success, String messageId, String id, String status) {
    }
}
