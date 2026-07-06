package com.example.network88.speedtest;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

/**
 * Multi-connection speed test built on OkHttp against Cloudflare's global CDN
 * ({@code speed.cloudflare.com}).
 *
 * <p>Why not a single stream: one TCP connection to a distant server cannot
 * saturate a fast link (its throughput is bounded by the window / round-trip
 * time and the server's own rate limiting), so it badly under-reports high-speed
 * connections. Real speed tests open <b>several parallel connections to a nearby
 * server</b>. This runs {@value #DOWNLOAD_STREAMS} concurrent download streams
 * and {@value #UPLOAD_STREAMS} upload streams, sums the bytes transferred, and
 * measures the rate over a steady-state window after discarding an initial
 * warm-up (TCP slow-start), which is where a single-stream test loses accuracy.</p>
 */
public class SpeedTestManager {

    public enum Phase {DOWNLOAD, UPLOAD}

    public interface Listener {
        void onProgress(Phase phase, float percent, double mbps);

        void onFinished(double downloadMbps, double uploadMbps);

        void onError(Phase phase, String message);
    }

    // Cloudflare Anycast endpoints: close to the user via CDN, HTTPS, high capacity.
    // __down caps the byte count (100MB+ returns 403), so each request pulls a
    // bounded chunk and every stream loops requests to stay saturated.
    private static final long CHUNK_BYTES = 50_000_000L;
    private static final String DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=" + CHUNK_BYTES;
    private static final String UPLOAD_URL = "https://speed.cloudflare.com/__up";

    private static final int DOWNLOAD_STREAMS = 6;
    private static final int UPLOAD_STREAMS = 4;
    private static final long WARMUP_MS = 2_000;   // discarded: TCP slow-start ramp
    private static final long MEASURE_MS = 8_000;  // steady-state measurement window
    private static final long SAMPLE_MS = 400;     // progress update cadence
    private static final int BUFFER = 64 * 1024;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private final ExecutorService orchestrator = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void start(@NonNull final Listener listener) {
        orchestrator.execute(() -> {
            try {
                double download = measure(Phase.DOWNLOAD, DOWNLOAD_STREAMS, listener);
                double upload = measure(Phase.UPLOAD, UPLOAD_STREAMS, listener);
                mainHandler.post(() -> listener.onFinished(download, upload));
            } catch (final MeasureException e) {
                mainHandler.post(() -> listener.onError(e.phase, e.getMessage()));
            }
        });
    }

    /** Runs one phase and returns the measured rate in Mbit/s. */
    private double measure(Phase phase, int streams, Listener listener) throws MeasureException {
        final AtomicLong bytes = new AtomicLong(0);
        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicBoolean anyData = new AtomicBoolean(false);
        final List<Call> calls = new ArrayList<>();
        final ExecutorService pool = Executors.newFixedThreadPool(streams);

        for (int i = 0; i < streams; i++) {
            pool.execute(() -> runStream(phase, bytes, running, anyData, calls));
        }

        try {
            final long start = System.nanoTime();
            long windowStartBytes = 0;
            long windowStartNanos = 0;
            boolean windowOpen = false;
            double mbps = 0;

            while (true) {
                sleep(SAMPLE_MS);
                long now = System.nanoTime();
                long elapsedMs = (now - start) / 1_000_000L;

                if (elapsedMs >= WARMUP_MS && !windowOpen) {
                    windowStartBytes = bytes.get();
                    windowStartNanos = now;
                    windowOpen = true;
                }

                if (windowOpen) {
                    double seconds = (now - windowStartNanos) / 1e9;
                    if (seconds > 0) {
                        mbps = (bytes.get() - windowStartBytes) * 8.0 / seconds / 1_000_000.0;
                    }
                }
                float percent = Math.min(100f, elapsedMs * 100f / (WARMUP_MS + MEASURE_MS));
                emitProgress(listener, phase, percent, mbps);

                if (elapsedMs >= WARMUP_MS + MEASURE_MS) {
                    double seconds = (now - windowStartNanos) / 1e9;
                    mbps = seconds > 0
                            ? (bytes.get() - windowStartBytes) * 8.0 / seconds / 1_000_000.0
                            : 0;
                    break;
                }
            }

            running.set(false);
            for (Call call : calls) {
                call.cancel();
            }
            pool.shutdownNow();

            if (!anyData.get()) {
                throw new MeasureException(phase, "Could not reach the test server");
            }
            return round2(mbps);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
            pool.shutdownNow();
            throw new MeasureException(phase, "Test interrupted");
        }
    }

    private void runStream(Phase phase, AtomicLong bytes, AtomicBoolean running,
                           AtomicBoolean anyData, List<Call> calls) {
        // Each request transfers a bounded chunk; loop so the stream stays busy
        // for the whole window regardless of how fast a single chunk finishes.
        while (running.get()) {
            try {
                Request request = phase == Phase.DOWNLOAD
                        ? new Request.Builder().url(DOWNLOAD_URL).build()
                        : new Request.Builder().url(UPLOAD_URL)
                        .post(uploadBody(bytes, running, anyData)).build();

                Call call = client.newCall(request);
                synchronized (calls) {
                    calls.add(call);
                }

                try (Response response = call.execute()) {
                    if (phase == Phase.DOWNLOAD) {
                        ResponseBody body = response.body();
                        if (body == null) {
                            continue;
                        }
                        InputStream in = body.byteStream();
                        byte[] buf = new byte[BUFFER];
                        int n;
                        while (running.get() && (n = in.read(buf)) > 0) {
                            bytes.addAndGet(n);
                            anyData.set(true);
                        }
                    }
                    // For upload the counting happens inside uploadBody while writing.
                }
            } catch (Exception ignored) {
                // Individual request failures are tolerated; a total failure is
                // caught by the anyData check after the window closes. Pause
                // briefly so a persistent error doesn't spin the CPU.
                if (!running.get()) {
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private RequestBody uploadBody(final AtomicLong bytes, final AtomicBoolean running,
                                   final AtomicBoolean anyData) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override
            public long contentLength() {
                return -1; // chunked: we stream until the window closes, then stop cleanly
            }

            @Override
            public void writeTo(@NonNull BufferedSink sink) throws IOException {
                // Bounded per request so the stream loop can start a fresh POST
                // (and survive any server-side upload cap) instead of one endless body.
                byte[] buf = new byte[BUFFER];
                long sent = 0;
                while (running.get() && sent < CHUNK_BYTES) {
                    sink.write(buf, 0, buf.length);
                    sink.flush();
                    bytes.addAndGet(buf.length);
                    anyData.set(true);
                    sent += buf.length;
                }
            }
        };
    }

    private void emitProgress(final Listener listener, final Phase phase,
                              final float percent, final double mbps) {
        mainHandler.post(() -> listener.onProgress(phase, percent, mbps));
    }

    public void shutdown() {
        orchestrator.shutdownNow();
    }

    private static void sleep(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Signals a fatal error for a phase, carrying which phase failed. */
    private static final class MeasureException extends Exception {
        final Phase phase;

        MeasureException(Phase phase, String message) {
            super(message);
            this.phase = phase;
        }
    }
}
