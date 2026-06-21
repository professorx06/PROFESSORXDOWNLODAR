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
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText inputLink;
    private MaterialButton btnDownload;
    private LinearLayout layoutHome, layoutHistory;
    private RecyclerView recyclerHistory;
    private TextView tvStatus, tvEmpty;
    private LinearProgressIndicator progressBar;
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
        prefs = getSharedPreferences("px_prefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("dark_mode", true);
        applyTheme(isDarkMode);
        setContentView(R.layout.activity_main);

        try { YoutubeDL.getInstance().init(getApplication()); }
        catch (Exception e) { e.printStackTrace(); }

        initViews();
        loadHistory();
        setupListeners();

        if (getIntent().getAction() != null &&
            getIntent().getAction().equals(android.content.Intent.ACTION_SEND)) {
            String sharedText = getIntent().getStringExtra(android.content.Intent.EXTRA_TEXT);
            if (sharedText != null) inputLink.setText(sharedText.trim());
        }
    }

    private void initViews() {
        inputLink       = findViewById(R.id.input_link);
        btnDownload     = findViewById(R.id.btn_download);
        layoutHome      = findViewById(R.id.layout_home);
        layoutHistory   = findViewById(R.id.layout_history);
        recyclerHistory = findViewById(R.id.recycler_history);
        tvStatus        = findViewById(R.id.tv_status);
        tvEmpty         = findViewById(R.id.tv_empty);
        progressBar     = findViewById(R.id.progress_bar);
        bottomNav       = findViewById(R.id.bottom_nav);

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
        if (url.isEmpty()) { showStatus("Link daalna bhool gaye! 🤦"); return; }
        if (!isValidUrl(url)) { showStatus("Sirf YouTube ya Instagram link daalein"); return; }

        setDownloading(true);
        showStatus("Download shuru ho raha hai...");

        executor.execute(() -> {
            try {
                File dir = new File(getExternalFilesDir(null), "ProfessorX");
                if (!dir.exists()) dir.mkdirs();

                YoutubeDLRequest req = new YoutubeDLRequest(url);
                req.addOption("-o", dir.getAbsolutePath() + "/%(title)s.%(ext)s");
                req.addOption("--no-playlist");

                if (url.contains("youtube.com") || url.contains("youtu.be")) {
                    req.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");
                    req.addOption("--merge-output-format", "mp4");
                } else {
                    req.addOption("-f", "best");
                }

                YoutubeDL.getInstance().execute(req,null,(progress, eta, line) -> {
                    mainHandler.post(() -> {
                        progressBar.setProgress((int) progress);
                        showStatus("Downloading: " + (int) progress + "%");
                    });
                    return null;
                });

                String platform = url.contains("instagram") ? "Instagram" : "YouTube";
                saveToHistory("Downloaded Video", url, platform);

                mainHandler.post(() -> {
                    setDownloading(false);
                    showStatus("✅ Download complete! Files app mein check karein");
                    inputLink.setText("");
                    loadHistory();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    setDownloading(false);
                    showStatus("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    private boolean isValidUrl(String url) {
        return url.contains("youtube.com") || url.contains("youtu.be") ||
               url.contains("instagram.com");
    }

    private void saveToHistory(String title, String url, String platform) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("history", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("title", title);
            obj.put("url", url);
            obj.put("platform", platform);
            obj.put("date", new SimpleDateFormat("dd MMM yyyy, hh:mm a",
                Locale.getDefault()).format(new Date()));
            JSONArray newArr = new JSONArray();
            newArr.put(obj);
            for (int i = 0; i < Math.min(arr.length(), 49); i++) newArr.put(arr.get(i));
            prefs.edit().putString("history", newArr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadHistory() {
        historyList.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString("history", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                historyList.add(new DownloadItem(
                    o.getString("title"),
                    o.getString("url"),
                    o.getString("platform"),
                    o.getString("date")
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }
        if (historyAdapter != null) historyAdapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (tvEmpty != null)
            tvEmpty.setVisibility(historyList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String msg) {
        tvStatus.setText(msg);
        tvStatus.setVisibility(View.VISIBLE);
    }

    private void setDownloading(boolean d) {
        btnDownload.setEnabled(!d);
        progressBar.setVisibility(d ? View.VISIBLE : View.GONE);
        if (!d) progressBar.setProgress(0);
        btnDownload.setText(d ? "Downloading..." : "Download");
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        prefs.edit().putBoolean("dark_mode", isDarkMode).apply();
        applyTheme(isDarkMode);
        recreate();
    }

    private void applyTheme(boolean dark) {
        AppCompatDelegate.setDefaultNightMode(
            dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
            }
