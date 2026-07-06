package com.example.network88;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.network88.data.HistoryRepository;
import com.example.network88.data.Measurement;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** Displays the persisted measurement history and lets the user clear it. */
public class HistoryActivity extends AppCompatActivity {

    private static final int MENU_CLEAR = 1;

    private HistoryRepository repository;
    private RecyclerView recyclerView;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        repository = new HistoryRepository(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(0, MENU_CLEAR, 0, R.string.clear_history)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_CLEAR) {
                repository.clear();
                render();
                Snackbar.make(recyclerView, R.string.history_cleared, Snackbar.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        recyclerView = findViewById(R.id.recyclerHistory);
        emptyView = findViewById(R.id.textEmpty);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        render();
    }

    private void render() {
        List<Measurement> items = repository.load();
        recyclerView.setAdapter(new HistoryAdapter(items));
        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? TextView.VISIBLE : TextView.GONE);
        recyclerView.setVisibility(empty ? RecyclerView.GONE : RecyclerView.VISIBLE);
    }
}
