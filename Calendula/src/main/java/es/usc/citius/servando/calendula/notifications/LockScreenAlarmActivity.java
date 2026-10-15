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

package es.usc.citius.servando.calendula.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.NotificationSoundPolicy;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.SecureWindowPolicy;
import pl.droidsonroids.gif.GifDrawable;

/**
 * Alarm activity that plays a ringtone over the lock screen when
 * the user has enabled insistent notifications
 *
 * @author angelpinheiro
 */
public class LockScreenAlarmActivity extends AppCompatActivity {

    private static final String TAG = "LockScreenAlarmAct";

    public static final String STOP_SIGNAL = "finish_lock_screen_alarm_activity";

    // vibration pattern
    public static long[] VIB_PATTERN = new long[]{
            1000, // 1s delay
            200,  // vibration
            100,  // pause
            300,  // vibration
            400,  // pause
            200,  // vibration
            100,  // pause
            300,  // vibration
            400,  // pause
            200,  // vibration
            100,  // pause
            300,  // vibration
            2000  // pause
    };

    private ImageView anim;
    private Handler h = new Handler();
    private Vibrator vibrator;
    private MediaPlayer mediaPlayer;
    private ImageButton confirmButton;
    private View confirmButtonBackground;
    private Intent target = null;

    private BroadcastReceiver finishReceiver;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            stopPlayingAlarm();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            startPlayingAlarm();
            return true;
        }
        // Do not consume Back, keyboard navigation or accessibility input.
        // Only the two intended volume shortcuts belong to this alarm.
        return super.onKeyDown(keyCode, event);
    }

    /** Consistent with CalendulaActivity, including an explicit opt-out. */
    static void applyScreenshotProtection(Window window, boolean protect) {
        if (protect && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This activity intentionally extends AppCompatActivity rather than
        // CalendulaActivity, so it must opt in to the same privacy policy
        // before its medication reminder view is inflated.
        applyScreenshotProtection(getWindow(), SecureWindowPolicy.isProtected());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                stopPlayingAlarm();
                animateAndFinish();
            }
        });
        setupForVisibilityOverLockScreen();
        setContentView(R.layout.activity_lock_screen_alarm);
        anim = (ImageView) findViewById(R.id.anim_image);

        if (getIntent() != null) {
            try {
                target = getIntent().getParcelableExtra("target");
            } catch (android.os.BadParcelableException | ClassCastException invalidTarget) {
                // An old/corrupt serialized target must not crash a visible
                // medication alarm or launch an unrelated clinical screen.
                target = null;
                LogUtil.w(TAG, "Ignoring invalid alarm review target");
            }
            LogUtil.d(TAG, "Review target available: " + (target != null));
        }

        registerFinishReceiver();

        confirmButtonBackground = findViewById(R.id.confirm_button_bg);
        confirmButton = (ImageButton) findViewById(R.id.confirm_btn);
        confirmButton.setImageDrawable(IconUtils.icon(this, CommunityMaterial.Icon.cmd_alarm_off, R.color.white, 70, 10));
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animateAndFinish();
            }
        });
        startPlayingAlarm();
        startAlarmAnimation();
    }

    @Override
    protected void onDestroy() {
        // Scheduled animations must not launch a stale clinical Intent after
        // the activity has already been dismissed or the patient has changed.
        h.removeCallbacksAndMessages(null);
        stopPlayingAlarm();
        unregisterFinishReceiver();
        super.onDestroy();
    }

    /**
     * Registers a broadcast receiver that will finish the activity on STOP_SIGNAL. The objective
     * is to stop the alarm after the user has confirmed or cancelled an intake, for example by
     * clicking a notification action.
     */
    private void registerFinishReceiver() {
        finishReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent intent) {
                String action = intent.getAction();
                if (STOP_SIGNAL.equals(action)) {
                    finish();
                }
            }
        };
        IntentFilter filter = new IntentFilter(STOP_SIGNAL);
        // The platform-only not-exported flag is unavailable before API 33.
        // AndroidX enforces the equivalent restriction on Android 6-12, so
        // another app cannot broadcast a fake STOP_SIGNAL to dismiss a
        // medication alarm on legacy devices.
        ContextCompat.registerReceiver(this, finishReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterFinishReceiver() {
        if (finishReceiver != null) {
            unregisterReceiver(finishReceiver);
        }
    }

    /**
     * Sets an animated drawable as background
     */
    private void startAlarmAnimation() {
        try {
            anim.setImageDrawable(new GifDrawable(getResources(), R.drawable.animated_clock));
        } catch (Exception e) {
            LogUtil.e(TAG, "An error occurred loading animation", e);
        }
    }

    /**
     * Configure media player and vibration service
     */
    private void setupMediaPlayer() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        Uri uri = getAlarmUri();
        if (uri == null) {
            return;
        }
        MediaPlayer candidate = null;
        try {
            candidate = new MediaPlayer();
            candidate.setAudioStreamType(AudioManager.STREAM_ALARM);
            candidate.setVolume(1, 1);
            candidate.setScreenOnWhilePlaying(true);
            candidate.setDataSource(this, uri);
            candidate.prepare();
            mediaPlayer = candidate;
        } catch (Exception unavailableSound) {
            // A removed media file or permission-revoked ringtone must not
            // discard the on-screen alarm and its independent vibration.
            LogUtil.w(TAG, "Alarm sound unavailable; retaining visible reminder");
            if (candidate != null) {
                try {
                    candidate.release();
                } catch (RuntimeException ignored) {
                    // This player has already failed to initialize.
                }
            }
            mediaPlayer = null;
        }
    }

    /**
     * Start vibrating and playing sound
     */
    private void startPlayingAlarm() {
        LogUtil.d(TAG, "startPlayingAlarm() called");
        if (mediaPlayer == null) {
            setupMediaPlayer();
        }
        if (mediaPlayer != null) {
            try {
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
            } catch (RuntimeException failedPlayer) {
                LogUtil.w(TAG, "Cannot start alarm sound; retaining visible reminder");
                releaseMediaPlayer();
            }
        }
        if (vibrator != null) {
            try {
                vibrator.vibrate(VIB_PATTERN, 0);
            } catch (RuntimeException unavailableVibration) {
                LogUtil.w(TAG, "Alarm vibration unavailable; retaining visible reminder");
            }
        }
    }

    /**
     * Stop the media player and cancel vibration
     */
    private void stopPlayingAlarm() {
        LogUtil.d(TAG, "stopPlayingAlarm() called");
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (RuntimeException ignored) {
                // A removed or unavailable service must not crash cleanup.
            }
        }
        releaseMediaPlayer();
    }

    private void releaseMediaPlayer() {
        MediaPlayer playing = mediaPlayer;
        mediaPlayer = null;
        if (playing != null) {
            try {
                if (playing.isPlaying()) {
                    playing.stop();
                }
            } catch (RuntimeException invalidState) {
                // It may never have entered a playable state.
            } finally {
                try {
                    playing.release();
                } catch (RuntimeException ignored) {
                    // Already failed or released.
                }
            }
        }
    }

    /**
     * Get the user notification ringtone from preferences, or the system default if undefined
     *
     * @return A ringtone for intake insistent alarms
     */
    private Uri getAlarmUri() {
        return configuredAlarmTone();
    }

    /** The alarm tone is a local resource or a previously selected media URI. */
    static Uri configuredAlarmTone() {
        String saved = null;
        try {
            saved = PreferenceUtils.getString(PreferenceKeys.SETTINGS_INSISTENT_NOTIFICATION_TONE, null);
        } catch (ClassCastException malformed) {
            // A mismatched legacy preference cannot cancel a medication alarm.
        }
        Uri system = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        Uri fallback = system != null ? system : Settings.System.DEFAULT_ALARM_ALERT_URI;
        return NotificationSoundPolicy.safeNotificationTone(saved, fallback);
    }

    /**
     * Enable activity display over the lock screen
     */
    private void setupForVisibilityOverLockScreen() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.alarm_activity_bg_color));
    }

    /**
     * Play some animations and finish activity
     */
    private void animateAndFinish() {
        stopPlayingAlarm();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                // hide confirm button
                confirmButton.animate().scaleXBy(-1f);
                confirmButton.animate().scaleYBy(-1f);
                // translate the animated image out of the screen
                anim.animate().alpha(0).translationYBy(-anim.getHeight() * 3);
            }
        }, 100);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                // expand confirm button background
                confirmButtonBackground.animate().alpha(0).scaleXBy(10);
                confirmButtonBackground.animate().alpha(0).scaleYBy(10);
            }
        }, 300);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                LogUtil.d(TAG, "Target " + (target != null));
                if (target != null) {
                    startActivity(target);
                }
                finish();
            }
        }, 600);
    }
}
