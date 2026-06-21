package com.professorx.downloader;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText inputLink;
    private MaterialButton btnDownload;
    private LinearLayout layoutHome, layoutHistory;
    private RecyclerView recyclerHistory;
    private TextView tvStatus, tvEmpty;
    private ProgressBar progressBar;
    private BottomNavigationView bottomNav;
    private HistoryAdapter historyAdapter;
    private List<DownloadItem> historyList = new ArrayList<>();
    private SharedPreferences prefs;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDarkMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("professor_x_prefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("dark_mode", true);
        applyTheme(isDarkMode);

        setContentView(R.layout.activity_main);

        // Initialize yt-dlp
        try {
            YoutubeDL.getInstance().init(getApplication());
        } catch (Exception e) {
            e.printStackTrace();
        }

        initViews();
        loadHistory();
        setupListeners();
    }

    private void initViews() {
        inputLink     = findViewById(R.id.input_link);
        btnDownload   = findViewById(R.id.btn_download);
        layoutHome    = findViewById(R.id.layout_home);
        layoutHistory = findViewById(R.id.layout_history);
        recyclerHistory = findViewById(R.id.recycler_history);
        tvStatus      = findViewById(R.id.tv_status);
        tvEmpty       = findViewById(R.id.tv_empty);
        progressBar   = findViewById(R.id.progress_bar);
        bottomNav     = findViewById(R.id.bottom_nav);

        historyAdapter = new HistoryAdapter(historyList);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerHistory.setAdapter(historyAdapter);
    }

    private void setupListeners() {
        btnDownload.setOnClickListener(v -> startDownload());

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                layoutHome.setVisibility(View.VISIBLE);
                layoutHistory.setVisibility(View.GONE);
            } else if (id == R.id.nav_history) {
                layoutHome.setVisibility(View.GONE);
                layoutHistory.setVisibility(View.VISIBLE);
                updateEmptyView();
            } else if (id == R.id.nav_theme) {
                toggleTheme();
            }
            return true;
        });
    }

    private void startDownload() {
        String url = inputLink.getText().toString().trim();

        if (url.isEmpty()) {
            showStatus("Link daalna bhool gaye! 🤦", false);
            return;
        }

        if (!isValidUrl(url)) {
            showStatus("Sirf YouTube ya Instagram ka link daalein", false);
            return;
        }

        setDownloading(true);
        showStatus("Download shuru ho raha hai...", true);

        executor.execute(() -> {
            try {
                File downloadDir = new File(getExternalFilesDir(null), "ProfessorX");
                if (!downloadDir.exists()) downloadDir.mkdirs();

                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("-o", downloadDir.getAbsolutePath() + "/%(title)s.%(ext)s");
                request.addOption("--no-playlist");

                // Best quality video + audio
                if (url.contains("youtube.com") || url.contains("youtu.be")) {
                    request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");
                    request.addOption("--merge-output-format", "mp4");
                } else {
                    request.addOption("-f", "best");
                }

                mainHandler.post(() -> showStatus("Video process ho rahi hai...", true));

                YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, (progress, etaInSeconds, line) -> {
                    mainHandler.post(() -> {
                        progressBar.setProgress((int) progress);
                        showStatus("Downloading: " + (int) progress + "%", true);
                    });
                });

                // Save to history
                String title = extractTitle(response.getOut());
                String platform = url.contains("instagram") ? "Instagram" : "YouTube";
                saveToHistory(title, url, platform);

                mainHandler.post(() -> {
                    setDownloading(false);
                    showStatus("✅ Download complete! Gallery check karein", true);
                    inputLink.setText("");
                    loadHistory();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    setDownloading(false);
                    showStatus("❌ Error: " + e.getMessage(), false);
                });
            }
        });
    }

    private boolean isValidUrl(String url) {
        return url.contains("youtube.com") || url.contains("youtu.be") ||
               url.contains("instagram.com");
    }

    private String extractTitle(String output) {
        if (output != null && !output.isEmpty()) {
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.contains("[download]") && line.contains("Destination")) {
                    String[] parts = line.split("/");
                    if (parts.length > 0) return parts[parts.length - 1];
                }
            }
        }
        return "Downloaded Video";
    }

    private void saveToHistory(String title, String url, String platform) {
        try {
            String historyJson = prefs.getString("history", "[]");
            JSONArray array = new JSONArray(historyJson);

            JSONObject item = new JSONObject();
            item.put("title", title);
            item.put("url", url);
            item.put("platform", platform);
            item.put("date", new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date()));

            // Latest first
            JSONArray newArray = new JSONArray();
            newArray.put(item);
            for (int i = 0; i < Math.min(array.length(), 49); i++) {
                newArray.put(array.get(i));
            }

            prefs.edit().putString("history", newArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadHistory() {
        historyList.clear();
        try {
            String historyJson = prefs.getString("history", "[]");
            JSONArray array = new JSONArray(historyJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                historyList.add(new DownloadItem(
                    obj.getString("title"),
                    obj.getString("url"),
                    obj.getString("platform"),
                    obj.getString("date")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (historyAdapter != null) historyAdapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(historyList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showStatus(String msg, boolean show) {
        tvStatus.setText(msg);
        tvStatus.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setDownloading(boolean downloading) {
        btnDownload.setEnabled(!downloading);
        progressBar.setVisibility(downloading ? View.VISIBLE : View.GONE);
        if (!downloading) progressBar.setProgress(0);
        btnDownload.setText(downloading ? "Downloading..." : "Download");
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        prefs.edit().putBoolean("dark_mode", isDarkMode).apply();
        applyTheme(isDarkMode);
        recreate();
    }

    private void applyTheme(boolean dark) {
        AppCompatDelegate.setDefaultNightMode(
            dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
}
