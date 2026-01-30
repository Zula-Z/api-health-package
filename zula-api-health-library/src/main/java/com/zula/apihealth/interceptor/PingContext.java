package com.zula.apihealth.interceptor;

/**
 * Thread-local flag to mark outbound calls that are internal pings.
 * Prevents pings from being counted in normal API call logs.
 */
public final class PingContext {
    private static final ThreadLocal<Boolean> PING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private PingContext() {}

    /** Mark current thread as executing a ping. */
    public static void markPing() { PING.set(Boolean.TRUE); }

    /** Clear the ping marker. */
    public static void clear() { PING.remove(); }

    /** @return true if current thread is running a ping request. */
    public static boolean isPing() { return Boolean.TRUE.equals(PING.get()); }
}
