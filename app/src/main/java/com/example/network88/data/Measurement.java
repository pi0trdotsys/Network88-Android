package com.example.network88.data;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A single network measurement result. Immutable value object that knows how to
 * serialize itself to / from JSON so it can be persisted in the history file.
 */
public class Measurement {

    private final long timestamp;      // epoch millis when the test finished
    private final double downloadMbps; // download rate in Mbit/s
    private final double uploadMbps;   // upload rate in Mbit/s
    private final double pingMs;       // round-trip time in ms, or -1 if unavailable
    private final String ipAddress;
    private final String subnetMask;

    public Measurement(long timestamp, double downloadMbps, double uploadMbps,
                       double pingMs, String ipAddress, String subnetMask) {
        this.timestamp = timestamp;
        this.downloadMbps = downloadMbps;
        this.uploadMbps = uploadMbps;
        this.pingMs = pingMs;
        this.ipAddress = ipAddress;
        this.subnetMask = subnetMask;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getDownloadMbps() {
        return downloadMbps;
    }

    public double getUploadMbps() {
        return uploadMbps;
    }

    public double getPingMs() {
        return pingMs;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("timestamp", timestamp);
        json.put("downloadMbps", downloadMbps);
        json.put("uploadMbps", uploadMbps);
        json.put("pingMs", pingMs);
        json.put("ipAddress", ipAddress);
        json.put("subnetMask", subnetMask);
        return json;
    }

    static Measurement fromJson(@NonNull JSONObject json) {
        return new Measurement(
                json.optLong("timestamp", 0L),
                json.optDouble("downloadMbps", 0d),
                json.optDouble("uploadMbps", 0d),
                json.optDouble("pingMs", -1d),
                json.optString("ipAddress", ""),
                json.optString("subnetMask", ""));
    }
}
