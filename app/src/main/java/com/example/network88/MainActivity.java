package com.example.network88;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.example.network88.data.HistoryRepository;
import com.example.network88.data.Measurement;
import com.example.network88.databinding.ActivityMainBinding;
import com.example.network88.speedtest.SpeedTestManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "speed_test_results";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PING_HOST = "google.com";

    private ActivityMainBinding binding;
    private SpeedTestManager speedTestManager;
    private HistoryRepository historyRepository;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        speedTestManager = new SpeedTestManager();
        historyRepository = new HistoryRepository(this);
        createNotificationChannel();

        binding.runButton.setOnClickListener(v -> startTest());
        binding.historyButton.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        restoreLastResult();

        if (!isOnline()) {
            Toast.makeText(this, R.string.error_offline, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // History may have been cleared while we were away; keep the screen in sync.
        restoreLastResult();
    }

    /** Shows the most recent stored measurement so the screen isn't empty on launch. */
    private void restoreLastResult() {
        List<Measurement> history = historyRepository.load();
        if (history.isEmpty()) {
            return;
        }
        Measurement last = history.get(0);
        binding.textDownloadValue.setText(formatSpeed(last.getDownloadMbps()));
        binding.textUploadValue.setText(formatSpeed(last.getUploadMbps()));
        binding.textPingValue.setText(last.getPingMs() >= 0
                ? String.format(Locale.US, "%.0f %s", last.getPingMs(), getString(R.string.unit_ms))
                : getString(R.string.value_placeholder));
        binding.textIpValue.setText(last.getIpAddress().isEmpty()
                ? getString(R.string.value_placeholder) : last.getIpAddress());
        binding.textMaskValue.setText(last.getSubnetMask().isEmpty()
                ? getString(R.string.value_placeholder) : last.getSubnetMask());
        binding.textStatus.setText(getString(R.string.status_last_result,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(last.getTimestamp()))));
        binding.runButton.setText(R.string.run_test_again);
    }

    private void startTest() {
        if (!isOnline()) {
            Toast.makeText(this, R.string.error_offline, Toast.LENGTH_LONG).show();
            return;
        }
        setTestingUi(true);
        binding.textStatus.setText(R.string.status_download);

        speedTestManager.start(new SpeedTestManager.Listener() {
            @Override
            public void onProgress(SpeedTestManager.Phase phase, float percent, double mbps) {
                if (phase == SpeedTestManager.Phase.DOWNLOAD) {
                    binding.textStatus.setText(R.string.status_download);
                    binding.textDownloadValue.setText(formatSpeed(mbps));
                } else {
                    binding.textStatus.setText(R.string.status_upload);
                    binding.textUploadValue.setText(formatSpeed(mbps));
                }
            }

            @Override
            public void onFinished(double downloadMbps, double uploadMbps) {
                binding.textDownloadValue.setText(formatSpeed(downloadMbps));
                binding.textUploadValue.setText(formatSpeed(uploadMbps));
                finishTest(downloadMbps, uploadMbps);
            }

            @Override
            public void onError(SpeedTestManager.Phase phase, String message) {
                setTestingUi(false);
                binding.textStatus.setText(R.string.status_idle);
                Toast.makeText(MainActivity.this,
                        getString(R.string.error_test, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Gathers ping / IP / mask off the UI thread, then updates UI, saves history and notifies. */
    private void finishTest(double downloadMbps, double uploadMbps) {
        final String ip = getIpAddress();
        final String mask = getSubnetMask();
        backgroundExecutor.execute(() -> {
            final double pingMs = ping(PING_HOST);
            final long timestamp = System.currentTimeMillis();
            final Measurement measurement = new Measurement(
                    timestamp, downloadMbps, uploadMbps, pingMs, ip, mask);
            historyRepository.add(measurement);

            runOnUiThread(() -> {
                binding.textPingValue.setText(pingMs >= 0
                        ? String.format(Locale.US, "%.0f %s", pingMs, getString(R.string.unit_ms))
                        : getString(R.string.value_placeholder));
                binding.textIpValue.setText(ip.isEmpty() ? getString(R.string.value_placeholder) : ip);
                binding.textMaskValue.setText(mask.isEmpty() ? getString(R.string.value_placeholder) : mask);
                binding.textStatus.setText(R.string.status_done);
                binding.runButton.setText(R.string.run_test_again);
                setTestingUi(false);
                sendNotification(downloadMbps, uploadMbps);
            });
        });
    }

    private void setTestingUi(boolean testing) {
        binding.progressBar.setVisibility(testing ? TextView.VISIBLE : TextView.INVISIBLE);
        binding.runButton.setEnabled(!testing);
    }

    private String formatSpeed(double mbps) {
        return String.format(Locale.US, "%.1f", mbps);
    }

    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public String getIpAddress() {
        WifiManager wifiMgr = (WifiManager)
                getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiMgr == null || wifiMgr.getConnectionInfo() == null) {
            return "";
        }
        int ip = wifiMgr.getConnectionInfo().getIpAddress();
        return ip == 0 ? "" : Formatter.formatIpAddress(ip);
    }

    public String getSubnetMask() {
        WifiManager wifiMgr = (WifiManager)
                getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiMgr == null) {
            return "";
        }
        DhcpInfo dhcp = wifiMgr.getDhcpInfo();
        if (dhcp == null || dhcp.netmask == 0) {
            return "";
        }
        return intToIp(dhcp.netmask);
    }

    /**
     * Pings {@code host} once and returns the round-trip time in milliseconds,
     * or -1 if it could not be determined. Parses the {@code time=<x> ms} token
     * from the ping output instead of relying on fixed buffer offsets.
     */
    public double ping(String host) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 5 " + host);
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            process.waitFor();
            Matcher matcher = Pattern.compile("time[=<]([0-9.]+)").matcher(output);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        } catch (IOException | InterruptedException | NumberFormatException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return -1;
    }

    private static String intToIp(int address) {
        // Bitwise mask each octet; DHCP stores the address little-endian.
        return String.format(Locale.US, "%d.%d.%d.%d",
                (address & 0xff),
                (address >> 8 & 0xff),
                (address >> 16 & 0xff),
                (address >> 24 & 0xff));
    }

    private void createNotificationChannel() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.notif_channel_desc));
        manager.createNotificationChannel(channel);
    }

    private void sendNotification(double downloadMbps, double uploadMbps) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        String text = String.format(Locale.US, "Download: %.1f Mbps  •  Upload: %.1f Mbps",
                downloadMbps, uploadMbps);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(text);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speedTestManager != null) {
            speedTestManager.shutdown();
        }
        backgroundExecutor.shutdownNow();
        binding = null;
    }
}
