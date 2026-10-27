package dev.chill.patchwork.midnightlib;

/** The one lock every MidnightLib config load and write goes through (see MidnightConfigMixin). */
public final class ConfigLock {
    public static final Object LOCK = new Object();

    private ConfigLock() {}
}
