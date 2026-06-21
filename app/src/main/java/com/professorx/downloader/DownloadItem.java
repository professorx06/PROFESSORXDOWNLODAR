package com.professorx.downloader;

public class DownloadItem {
    public String title;
    public String url;
    public String platform;
    public String date;

    public DownloadItem(String title, String url, String platform, String date) {
        this.title    = title;
        this.url      = url;
        this.platform = platform;
        this.date     = date;
    }
}
