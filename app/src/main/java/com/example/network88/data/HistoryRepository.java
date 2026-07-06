package com.example.network88.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists {@link Measurement}s to a JSON file in the app's private storage
 * ({@code filesDir/measurements.json}). The file holds a single JSON array,
 * newest entry first. All reads/writes are synchronized so the repository can be
 * shared safely between the UI thread and background test threads.
 */
public class HistoryRepository {

    private static final String TAG = "HistoryRepository";
    private static final String FILE_NAME = "measurements.json";
    /** Cap the stored history so the file cannot grow unbounded. */
    private static final int MAX_ENTRIES = 100;

    private final File file;

    public HistoryRepository(@NonNull Context context) {
        this.file = new File(context.getFilesDir(), FILE_NAME);
    }

    /** Returns all stored measurements, newest first. Never null. */
    public synchronized List<Measurement> load() {
        List<Measurement> result = new ArrayList<>();
        if (!file.exists()) {
            return result;
        }
        try {
            byte[] bytes = readAll(file);
            String content = new String(bytes, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                return result;
            }
            JSONArray array = new JSONArray(content);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) {
                    result.add(Measurement.fromJson(obj));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read history file", e);
        }
        return result;
    }

    /** Prepends a measurement to the history and persists it. */
    public synchronized void add(@NonNull Measurement measurement) {
        List<Measurement> current = load();
        current.add(0, measurement);
        while (current.size() > MAX_ENTRIES) {
            current.remove(current.size() - 1);
        }
        save(current);
    }

    /** Removes every stored measurement. */
    public synchronized void clear() {
        save(new ArrayList<>());
    }

    private void save(@NonNull List<Measurement> measurements) {
        JSONArray array = new JSONArray();
        try {
            for (Measurement m : measurements) {
                array.put(m.toJson());
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize history", e);
            return;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Failed to write history file", e);
        }
    }

    private static byte[] readAll(File file) throws Exception {
        int length = (int) file.length();
        byte[] bytes = new byte[length];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < length && (read = in.read(bytes, offset, length - offset)) > 0) {
                offset += read;
            }
        }
        return bytes;
    }
}
