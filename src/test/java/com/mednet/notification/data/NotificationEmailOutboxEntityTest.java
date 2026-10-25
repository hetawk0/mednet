package com.mednet.notification.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class NotificationEmailOutboxEntityTest {

    @Test
    void retriesWithBackoffAndFinishesAfterAcceptedDelivery() {
        Instant now = Instant.now().plusSeconds(1);
        NotificationEmailOutboxEntity item =
                new NotificationEmailOutboxEntity("outbox-id", "notification-id", "account-id");

        assertThat(item.claim(now, now.plusSeconds(120))).isTrue();
        item.markFailed(now, "provider_rejected");
        assertThat(item.getStatus()).isEqualTo("PENDING");
        assertThat(item.getAttempts()).isEqualTo(1);
        assertThat(item.getNextAttemptAt()).isEqualTo(now.plusSeconds(15));
        assertThat(item.claim(now.plusSeconds(14), now.plusSeconds(134))).isFalse();

        Instant retryAt = now.plusSeconds(15);
        assertThat(item.claim(retryAt, retryAt.plusSeconds(120))).isTrue();
        item.markSent(retryAt);
        assertThat(item.getStatus()).isEqualTo("SENT");
        assertThat(item.getSentAt()).isEqualTo(retryAt);
        assertThat(item.claim(retryAt.plusSeconds(1), retryAt.plusSeconds(121))).isFalse();
    }

    @Test
    void reclaimsExpiredDeliveryLeaseAndStopsAfterEightFailures() {
        Instant now = Instant.now().plusSeconds(1);
        NotificationEmailOutboxEntity item =
                new NotificationEmailOutboxEntity("outbox-id", "notification-id", "account-id");
        assertThat(item.claim(now, now.plusSeconds(30))).isTrue();
        assertThat(item.claim(now.plusSeconds(31), now.plusSeconds(151))).isTrue();

        Instant retry = now.plusSeconds(31);
        for (int attempt = 0; attempt < 8; attempt++) {
            item.markFailed(retry, "provider_rejected");
            if (attempt < 7) {
                retry = item.getNextAttemptAt();
                assertThat(item.claim(retry, retry.plusSeconds(120))).isTrue();
            }
        }
        assertThat(item.getStatus()).isEqualTo("FAILED");
        assertThat(item.getAttempts()).isEqualTo(8);
        assertThat(item.getLastErrorCode()).isEqualTo("provider_rejected");
    }
}
