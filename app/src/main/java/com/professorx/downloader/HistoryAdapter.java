package com.professorx.downloader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private List<DownloadItem> items;

    public HistoryAdapter(List<DownloadItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DownloadItem item = items.get(position);
        holder.tvTitle.setText(item.title);
        holder.tvDate.setText(item.date);
        holder.chipPlatform.setText(item.platform);

        // Platform chip color
        if (item.platform.equals("YouTube")) {
            holder.chipPlatform.setChipBackgroundColorResource(R.color.youtube_red);
        } else {
            holder.chipPlatform.setChipBackgroundColorResource(R.color.insta_pink);
        }

        // Long press to copy URL
        holder.itemView.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) v.getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("url", item.url));
            Toast.makeText(v.getContext(), "Link copy ho gaya!", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate;
        Chip chipPlatform;

        VH(View v) {
            super(v);
            tvTitle      = v.findViewById(R.id.tv_title);
            tvDate       = v.findViewById(R.id.tv_date);
            chipPlatform = v.findViewById(R.id.chip_platform);
        }
    }
}
