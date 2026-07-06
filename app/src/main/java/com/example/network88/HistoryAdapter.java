package com.example.network88;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.network88.data.Measurement;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Renders the list of stored {@link Measurement}s. */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<Measurement> items;
    private final DateFormat dateFormat =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

    public HistoryAdapter(@NonNull List<Measurement> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_measurement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Measurement m = items.get(position);
        holder.date.setText(dateFormat.format(new Date(m.getTimestamp())));
        holder.download.setText(String.format(Locale.US, "%.1f Mbps", m.getDownloadMbps()));
        holder.upload.setText(String.format(Locale.US, "%.1f Mbps", m.getUploadMbps()));
        holder.ping.setText(m.getPingMs() >= 0
                ? String.format(Locale.US, "%.0f ms", m.getPingMs())
                : "—");
        holder.ip.setText(String.format(Locale.US, "IP %s · Mask %s",
                emptyToDash(m.getIpAddress()), emptyToDash(m.getSubnetMask())));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String emptyToDash(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView date;
        final TextView download;
        final TextView upload;
        final TextView ping;
        final TextView ip;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.itemDate);
            download = itemView.findViewById(R.id.itemDownload);
            upload = itemView.findViewById(R.id.itemUpload);
            ping = itemView.findViewById(R.id.itemPing);
            ip = itemView.findViewById(R.id.itemIp);
        }
    }
}
