package com.example.network88.speedtest;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;

/**
 * Runs a download test followed by an upload test <b>sequentially</b> on a
 * background thread and reports progress / results on the main thread.
 *
 * <p>Why sequential: running download and upload at the same time makes them
 * compete for the same link, so both figures come out wrong. Each phase now runs
 * on its own {@link SpeedTestSocket} and the next phase only starts once the
 * previous one has actually completed (or errored) — coordinated with a
 * {@link CountDownLatch} instead of a fixed {@code sleep()}.</p>
 *
 * <p>The reported rate is taken from {@code onCompletion} (the final, accurate
 * figure) rather than the last {@code onProgress} snapshot.</p>
 */
public class SpeedTestManager {

    public enum Phase {DOWNLOAD, UPLOAD}

    public interface Listener {
        /** Live progress for the current phase (0-100%) and instantaneous rate in Mbit/s. */
        void onProgress(Phase phase, float percent, double mbps);

        /** Both phases finished successfully. */
        void onFinished(double downloadMbps, double uploadMbps);

        /** A phase failed; the run is aborted. */
        void onError(Phase phase, String message);
    }

    // Public test targets. Both use plain HTTP on the same host: the previous
    // https://speed.hetzner.de endpoint was unreachable on real networks, and
    // jspeedtest's HTTPS handling is unreliable. tele2 is jspeedtest's canonical
    // test server and serves both a download file and an upload sink over HTTP.
    private static final String DOWNLOAD_URL = "http://speedtest.tele2.net/100MB.zip";
    private static final String UPLOAD_URL = "http://speedtest.tele2.net/upload.php";
    private static final int DOWNLOAD_DURATION_MS = 8000;
    private static final int UPLOAD_DURATION_MS = 8000;
    private static final int UPLOAD_SIZE_BYTES = 10_000_000;
    // Safety net so a stalled socket can never hang the run forever.
    private static final long PHASE_TIMEOUT_MS = 30_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Result of a single phase: either a rate (Mbit/s) or an error message. */
    private static final class PhaseResult {
        double mbps;
        String error;
    }

    public void start(@NonNull final Listener listener) {
        executor.execute(() -> {
            PhaseResult download = runPhase(Phase.DOWNLOAD, listener);
            if (download.error != null) {
                postError(listener, Phase.DOWNLOAD, download.error);
                return;
            }
            PhaseResult upload = runPhase(Phase.UPLOAD, listener);
            if (upload.error != null) {
                postError(listener, Phase.UPLOAD, upload.error);
                return;
            }
            final double dl = download.mbps;
            final double ul = upload.mbps;
            mainHandler.post(() -> listener.onFinished(dl, ul));
        });
    }

    private PhaseResult runPhase(final Phase phase, final Listener listener) {
        final PhaseResult result = new PhaseResult();
        final CountDownLatch latch = new CountDownLatch(1);
        final SpeedTestSocket socket = new SpeedTestSocket();
        // Guard against the listener firing more than once (progress + completion race).
        final AtomicReference<Boolean> done = new AtomicReference<>(false);

        socket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                if (done.compareAndSet(false, true)) {
                    result.mbps = toMbps(report);
                    latch.countDown();
                }
            }

            @Override
            public void onError(SpeedTestError error, String message) {
                if (done.compareAndSet(false, true)) {
                    result.error = describe(error, message);
                    latch.countDown();
                }
            }

            @Override
            public void onProgress(float percent, SpeedTestReport report) {
                postProgress(listener, phase, percent, toMbps(report));
            }
        });

        try {
            if (phase == Phase.DOWNLOAD) {
                socket.startFixedDownload(DOWNLOAD_URL, DOWNLOAD_DURATION_MS);
            } else {
                socket.startFixedUpload(UPLOAD_URL, UPLOAD_SIZE_BYTES, UPLOAD_DURATION_MS);
            }
            if (!latch.await(PHASE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                socket.forceStopTask();
                if (done.compareAndSet(false, true)) {
                    result.error = "Test timed out";
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.error = "Test interrupted";
        } finally {
            socket.closeSocket();
        }
        return result;
    }

    private void postProgress(final Listener listener, final Phase phase,
                              final float percent, final double mbps) {
        mainHandler.post(() -> listener.onProgress(phase, percent, mbps));
    }

    private void postError(final Listener listener, final Phase phase, final String message) {
        mainHandler.post(() -> listener.onError(phase, message));
    }

    /** Cancels any in-flight work; call from the owning component's onDestroy. */
    public void shutdown() {
        executor.shutdownNow();
    }

    private static double toMbps(SpeedTestReport report) {
        // getTransferRateBit() is bits/s as BigDecimal; convert to Mbit/s.
        return report.getTransferRateBit()
                .divide(BigDecimal.valueOf(1_000_000L), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static String describe(SpeedTestError error, String message) {
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return error != null ? error.name() : "Unknown error";
    }
}
