/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.pinlock;

import android.os.SystemClock;

import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * In-process PIN unlock state. Elapsed realtime is independent of wall-clock
 * changes, timezone updates and daylight-saving transitions.
 */
public final class UnlockStateManager {
    private static final String DEFAULT_UNLOCK_DURATION_SECONDS = "120";
    private static final String TAG = "UnlockStateManager";
    private static final UnlockStateManager INSTANCE = new UnlockStateManager();

    // -1 means locked. A monotonic timestamp is never persisted across boots.
    private volatile long unlockedAtElapsedMs = -1L;

    private UnlockStateManager() {
    }

    public static UnlockStateManager getInstance() {
        return INSTANCE;
    }

    /** Called only after authenticating the PIN or approved biometric. */
    public void unlock() {
        unlockedAtElapsedMs = SystemClock.elapsedRealtime();
    }

    /** Immediately revokes access to the protected patient information. */
    public void lock() {
        unlockedAtElapsedMs = -1L;
    }

    /**
     * Invalid, unsupported or tampered timeouts deny access instead of
     * crashing, defaulting to an unlimited window or silently lengthening it.
     */
    static int parseAllowedTimeoutSeconds(String raw) {
        if (raw == null) {
            return -1;
        }
        String selected = raw.trim();
        switch (selected) {
            case "30":
                return 30;
            case "60":
                return 60;
            case "120":
                return 120;
            case "300":
                return 300;
            default:
                return -1;
        }
    }

    /** Pure monotonic-time policy for local JVM and instrumentation tests. */
    static boolean withinUnlockWindow(long startMs, long nowMs, int durationSeconds) {
        if (startMs < 0 || nowMs < startMs || durationSeconds <= 0 || durationSeconds > 300) {
            return false;
        }
        // Subtraction after ordering handles epoch crossing and avoids a
        // future-date bypass if elapsed realtime resets after device restart.
        return nowMs - startMs <= durationSeconds * 1000L;
    }

    /**
     * Check whether a completed authentication is still valid.
     * An invalid preference, clock reset or elapsed timeout locks immediately.
     */
    public boolean isUnlocked() {
        final long start = unlockedAtElapsedMs;
        if (start < 0L) {
            return false;
        }
        final String stored;
        try {
            stored = PreferenceUtils.getString(
                    PreferenceKeys.UNLOCK_PIN_TIMEOUT, DEFAULT_UNLOCK_DURATION_SECONDS);
        } catch (ClassCastException incompatible) {
            // An old/foreign backup may have put an Integer or Boolean where
            // the PIN selector stores a String. Never crash or keep a prior
            // authenticated session unlocked after this validation failure.
            unlockedAtElapsedMs = -1L;
            LogUtil.w(TAG, "PIN unlock invalidated by incompatible timeout setting");
            return false;
        }
        final int seconds = parseAllowedTimeoutSeconds(stored);
        final long now = SystemClock.elapsedRealtime();
        if (withinUnlockWindow(start, now, seconds)) {
            return true;
        }
        unlockedAtElapsedMs = -1L;
        LogUtil.d(TAG, "PIN unlock expired or invalidated");
        return false;
    }
}
