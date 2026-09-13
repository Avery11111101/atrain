package com.avery.atrain.update;

public record ReleaseInfo(
        String tagName,
        String name,
        String body,
        String htmlUrl,
        String downloadUrl,
        boolean isPrerelease,
        String publishedAt
) {}