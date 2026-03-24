package com.fourtwo.hookintent.data;

public class ReleaseInfo {

    private final String tagName;
    private final String name;
    private final String body;
    private final String downloadUrl;
    private final String publishedAt;
    private final boolean prerelease;
    private final boolean draft;

    public ReleaseInfo(String tagName,
                       String name,
                       String body,
                       String downloadUrl,
                       String publishedAt,
                       boolean prerelease,
                       boolean draft) {
        this.tagName = tagName == null ? "" : tagName;
        this.name = name == null ? "" : name;
        this.body = body == null ? "" : body;
        this.downloadUrl = downloadUrl == null ? "" : downloadUrl;
        this.publishedAt = publishedAt == null ? "" : publishedAt;
        this.prerelease = prerelease;
        this.draft = draft;
    }

    public String getTagName() {
        return tagName;
    }

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public boolean isPrerelease() {
        return prerelease;
    }

    public boolean isDraft() {
        return draft;
    }

    public String getDisplayVersion() {
        if (!name.isEmpty()) {
            return name;
        }
        return tagName;
    }
}