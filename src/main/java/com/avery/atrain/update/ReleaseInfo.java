package com.avery.atrain.update;

public record ReleaseInfo(
        String tagName,
        String name,
        String body,
        String htmlUrl,
        String downloadUrl,
        String fileName,
        boolean isPrerelease,
        String publishedAt
) {
    public ReleaseInfo(String tagName, String name, String body, String htmlUrl, String downloadUrl, boolean isPrerelease, String publishedAt) {
        this(tagName, name, body, htmlUrl, downloadUrl, null, isPrerelease, publishedAt);
    }
}