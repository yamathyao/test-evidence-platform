package com.talkanything.testevidence.agent.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncEvidenceReporterTest {
    @Test
    void dropsSecondEventWhenBoundedQueueIsFullWithoutBlockingCaller() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(1, 1, (events, diagnostics) -> {
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
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(1, 1, (events, diagnostics) -> { throw new IllegalStateException("offline"); });

        reporter.report(HttpEvidence.sample("span-failed"));

        assertFalse(reporter.isClosed());
        reporter.close();
    }

    @Test
    void includesRunDiagnosticAfterAFailedDelivery() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        List<DeliveryDiagnostic> diagnostics = Collections.synchronizedList(new ArrayList<DeliveryDiagnostic>());
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1, (events, snapshot) -> {
            if (attempts.getAndIncrement() == 0) {
                failed.countDown();
                throw new IllegalStateException("offline");
            }
            diagnostics.addAll(snapshot);
            delivered.countDown();
        });

        reporter.report(HttpEvidence.sample("span-failed"));
        assertTrue(failed.await(1, TimeUnit.SECONDS));
        reporter.report(HttpEvidence.sample("span-delivered"));
        assertTrue(delivered.await(1, TimeUnit.SECONDS));

        assertEquals(1, diagnostics.size());
        DeliveryDiagnostic diagnostic = diagnostics.get(0);
        assertEquals("00000000-0000-0000-0000-000000000001", diagnostic.runId());
        assertEquals("test", diagnostic.serviceName());
        assertEquals(1, diagnostic.droppedEvidenceCount());
        assertEquals(1, diagnostic.deliveryFailureCount());
        assertEquals("IllegalStateException", diagnostic.lastFailure());
        reporter.close();
    }
}
