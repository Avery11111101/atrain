package com.avery.atrain.update;

public record ReleaseInfo(
        String tagName,
        String name,
        String body,
        boolean isPrerelease,
        String downloadUrl
) {}