package com.talkanything.testevidence.agent.http;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncEvidenceReporterTest {
    @Test
    void dropsSecondEventWhenBoundedQueueIsFullWithoutBlockingCaller() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(1, 1, events -> {
            started.countDown();
            try { release.await(1, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
        reporter.report(HttpEvidence.sample("span-1"));
        assertTrue(started.await(1, TimeUnit.SECONDS));
        reporter.report(HttpEvidence.sample("span-2"));
        reporter.report(HttpEvidence.sample("span-3"));

        assertTrue(reporter.droppedCount() >= 1);
        release.countDown();
        reporter.close();
    }

    @Test
    void sinkFailureDoesNotEscapeReport() {
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(1, 1, events -> { throw new IllegalStateException("offline"); });

        reporter.report(HttpEvidence.sample("span-failed"));

        assertFalse(reporter.isClosed());
        reporter.close();
    }
}
