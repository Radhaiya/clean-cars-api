package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Terminal-state fixer for trials (Razorpay handles paid subscriptions via
 * webhooks — its lifecycle events are the source of truth for money, so this job
 * only ever touches rows the webhooks never revisit: a local-only trial has no
 * Razorpay event stream).
 *
 * <p>A {@code TRIALING} row whose {@code endDate} has passed stays "live" forever
 * otherwise — {@code CurrentSubscriptionResponse} keeps reporting
 * {@code active=true, onTrial=true} and every limit/gate keeps granting trial
 * privileges. The job flips those rows {@code EXPIRED} (terminal; re-subscribing
 * stays allowed since EXPIRED is not a subscribe blocker).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.trial-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionExpiryJob {

    private final SubscriptionRepository subscriptions;

    /** Nightly, just after UTC midnight (a UTC end-date lapses when the UTC day does). */
    @Scheduled(cron = "${app.jobs.trial-expiry.cron:0 5 0 * * *}")
    @Transactional
    public void expireLapsedTrials() {
        LocalDate now = LocalDate.now();
        List<Subscription> lapsed = subscriptions
                .findByStatusAndEndDateBefore(SubscriptionStatus.TRIALING, now);
        for (Subscription sub : lapsed) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptions.save(sub);
        }
        if (!lapsed.isEmpty()) {
            log.info("Trial expiry job: {} lapsed trial row(s) -> EXPIRED {}", lapsed.size(),
                    lapsed.stream().map(s -> s.getId() + "(org " + s.getOrgId() + ")").toList());
        }
    }
}
