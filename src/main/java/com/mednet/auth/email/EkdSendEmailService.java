package com.mednet.auth.email;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class EkdSendEmailService {

    private static final Logger log = LoggerFactory.getLogger(EkdSendEmailService.class);
    private final RestClient client;
    private final String apiKey;
    private final String from;

    public EkdSendEmailService(
            @Value("${EKDSEND_API_URL:}") String apiUrl,
            @Value("${EKDSEND_API_KEY:}") String apiKey,
            @Value("${FROM_EMAIL:${EKDSEND_FROM:}}") String from) {
        this.client = RestClient.builder()
                .baseUrl(apiUrl == null ? "" : apiUrl.replaceAll("/$", ""))
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null ? "" : from.trim();
    }

    public boolean send(String to, String subject, String html, String text) {
        if (apiKey.isBlank() || from.isBlank()) {
            log.warn("EKDSend email is not configured; message was not sent");
            return false;
        }
        try {
            client.post()
                    .uri("/send")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "type", "email",
                            "to", to,
                            "from", from,
                            "subject", subject,
                            "body", html,
                            "text", text))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            log.warn("EKDSend rejected an email request: {}", exception.getMessage());
            return false;
        }
    }
}
