package com.sentinelqa.driver;

import java.util.Arrays;

/** Browsers the grid and the local runner both understand. */
public enum BrowserType {

    CHROME,
    FIREFOX,
    EDGE;

    public static BrowserType from(String raw) {
        return Arrays.stream(values())
                .filter(b -> b.name().equalsIgnoreCase(raw))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported browser '" + raw + "'. Supported: " + Arrays.toString(values())));
    }
}
