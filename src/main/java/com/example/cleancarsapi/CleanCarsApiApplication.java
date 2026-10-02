package com.example.cleancarsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import java.util.TimeZone;

@SpringBootApplication
public class CleanCarsApiApplication {

    /**
     * The app runs in UTC, always: every {@code LocalDateTime} in memory means UTC
     * wall time and every DB timestamp is stored UTC (MySQL TIMESTAMP columns
     * normalize via the driver). Responses are converted to the org's timezone
     * ({@code organizations.timezone}) at the JSON boundary — see
     * {@code TimezoneJacksonConfig}. A static block (not {@code main}) so tests
     * booting the context get the same guarantee.
     */
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CleanCarsApiApplication.class);
        // No implicit profile: running without one must fail, never fall back to local.
        app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> {
            if (event.getEnvironment().getActiveProfiles().length == 0) {
                throw new IllegalStateException(
                        "No Spring profile active. Set SPRING_PROFILES_ACTIVE (local | stage | prod).");
            }
        });
        app.run(args);
    }
}
