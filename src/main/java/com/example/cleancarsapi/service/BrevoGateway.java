package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.BrevoProperties;
import com.example.cleancarsapi.exception.BrevoApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin Brevo transactional-email wrapper ({@code POST /v3/smtp/email}). Holds no
 * state; failures surface as {@link BrevoApiException} (502).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(BrevoProperties.class)
public class BrevoGateway {

    private static final String API_BASE = "https://api.brevo.com/v3";

    private static final String VERIFICATION_TEMPLATE = loadTemplate("email/verification-code.html");

    private final BrevoProperties props;

    /** Send the account-verification OTP email. */
    public void sendVerificationCode(String toEmail, String toName, String code, int validMinutes) {
        if (!StringUtils.hasText(props.apiKey()) || !StringUtils.hasText(props.senderEmail())) {
            throw new BrevoApiException("Brevo is not configured (app.brevo.*)");
        }
        String html = VERIFICATION_TEMPLATE
                .replace("{{NAME}}", HtmlUtils.htmlEscape(StringUtils.hasText(toName) ? toName : "there"))
                .replace("{{CODE}}", code)
                .replace("{{MINUTES}}", String.valueOf(validMinutes));
        Map<String, Object> body = Map.of(
                "sender", Map.of("email", props.senderEmail(),
                        "name", StringUtils.hasText(props.senderName()) ? props.senderName() : "MyGarageOne"),
                "to", List.of(Map.of("email", toEmail, "name", toName == null ? toEmail : toName)),
                "subject", "Your MyGarageOne verification code",
                "htmlContent", html);
        try {
            client().post().uri("/smtp/email").body(body).retrieve().toBodilessEntity();
            log.info("Brevo verification email sent to {}", mask(toEmail));
        } catch (RestClientException e) {
            throw new BrevoApiException("Could not send verification email (Brevo): " + e.getMessage(), e);
        }
    }

    private static String loadTemplate(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing email template " + path, e);
        }
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(API_BASE)
                .defaultHeader("api-key", props.apiKey())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    /** Never log a full address. */
    private String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
