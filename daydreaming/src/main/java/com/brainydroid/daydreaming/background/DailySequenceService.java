package com.brainydroid.daydreaming.background;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.brainydroid.daydreaming.R;
import com.brainydroid.daydreaming.db.SequencesStorage;
import com.brainydroid.daydreaming.network.SntpClient;
import com.brainydroid.daydreaming.network.SntpClientCallback;
import com.brainydroid.daydreaming.sequence.Sequence;
import com.brainydroid.daydreaming.sequence.SequenceBuilder;
import com.brainydroid.daydreaming.ui.sequences.PageActivity;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Calendar;

import roboguice.service.RoboService;

/**
 * Create and populate a {@link Sequence}, then notify it to the user.
 *
 * @author Sébastien Lerique
 * @author Vincent Adam
 * @see Sequence
 * @see ProbeSchedulerService
 * @see SyncService
 */
public class DailySequenceService extends RoboService {

    private static String TAG = "DailySequenceService";

    public static String CANCEL_PENDING_POLLS = "cancelPendingSequences";
    public static String SEQUENCE_TYPE = "sequenceType";

    @Inject NotificationManager notificationManager;
    @Inject SequencesStorage sequencesStorage;
    @Inject SequenceBuilder sequenceBuilder;
    @Inject SharedPreferences sharedPreferences;
    @Inject SntpClient sntpClient;
    @Inject Sequence sequence;
    @Inject StatusManager statusManager;

    String sequenceType;

    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d(TAG, "DailySequenceService started");

        super.onStartCommand(intent, flags, startId);

        sequenceType = intent.getStringExtra(DailySequenceService.SEQUENCE_TYPE);
        if (sequenceType == null) {
            Log.d(TAG, "Sequence type not found in intent, found null");
            throw new NullPointerException();
        }

        if (intent.getBooleanExtra(CANCEL_PENDING_POLLS, false)) {
            Logger.v(TAG, "Started to cancel pending sequences of type {}", sequenceType);
            cancelPendingSequences();
            // No need to reschedule:
            // This is run from statusManager, directly to cancel pending stuff,
            // and doesn't interfere with scheduling. If other classes do interfere
            // (e.g. clearing parameters), they relaunch scheduler services.
        } else {
            Logger.v(TAG, "Started to create and notify a sequence of type {}", sequenceType);

            // If the questions haven't been downloaded (which is probably
            // because the json was malformed), only reschedule (which will
            // re-download the questions; hopefully they will have been fixed)
            // and don't show any sequence.
            if (statusManager.areParametersUpdated() && statusManager.wereBEQAnsweredOnTime()) {
                // Populate and notify the sequence
                populateSequence();
                notifySequence();
            }

            // Always reschedule
            startSchedulerService();
        }

        stopSelf();
        return START_REDELIVER_INTENT;
    }

    @Override
    public synchronized IBinder onBind(Intent intent) {
        // Don't allow binding
        return null;
    }

    /**
     * Create the {@link PageActivity} {@link Intent}.
     *
     * @return An {@link Intent} to launch our {@link Sequence}
     */
    private synchronized Intent createSequenceIntent() {
        Logger.d(TAG, "Creating sequence Intent - type: {}", sequenceType);

        Intent intent = new Intent(this, PageActivity.class);

        // Set the id of the sequence to start
        intent.putExtra(PageActivity.EXTRA_SEQUENCE_ID, sequence.getId());

        // Create a new task. The rest is defined in the App manifest.
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * Notify our sequence to the user.
     */
    private synchronized void notifySequence() {
        Logger.d(TAG, "Notifying sequence of type {}", sequenceType);

        // Create the PendingIntent
        Intent intent = createSequenceIntent();
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_CANCEL_CURRENT |
                PendingIntent.FLAG_ONE_SHOT);

        int flags = 0;

        // Should we flash the LED?
        if (sharedPreferences.getBoolean("notification_blink_key", true)) {
            Logger.v(TAG, "Activating lights");
            flags |= Notification.DEFAULT_LIGHTS;
        }

        // Should we vibrate?
        if (sharedPreferences.getBoolean("notification_vibrator_key", true)) {
            Logger.v(TAG, "Activating vibration");
            flags |= Notification.DEFAULT_VIBRATE;
        }

        // Should we beep?
        if (sharedPreferences.getBoolean("notification_sound_key", true)) {
            Logger.v(TAG, "Activating sound");
            flags |= Notification.DEFAULT_SOUND;
        }

        // Create our notification
        Notification notification = new NotificationCompat.Builder(this)
        .setTicker(getString(sequence.getIdTicker()))
        .setContentTitle(getString(sequence.getIdTitle()))
        .setContentText(getString(sequence.getIdText()))
        .setContentIntent(contentIntent)
        .setSmallIcon(R.drawable.ic_stat_notify_small_daydreaming)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setDefaults(flags)
        .build();

        // How to beep?
        if (sharedPreferences.getBoolean("notification_sound_key", true)) {
            Logger.v(TAG, "Adding custom sound");
            notification.sound = Uri.parse("android.resource://" + "com.brainydroid.daydreaming" + "/" + R.raw.notification);
        }

        // And send it to the system
        notificationManager.cancel(TAG, sequence.getId());
        notificationManager.notify(TAG, sequence.getId(), notification);
    }

    /**
     * Fill our {@link Sequence} with questions.
     */
    private synchronized void populateSequence() {
        Logger.d(TAG, "Populating sequence with sequence of type {}", sequenceType);

        // Pick from already created sequences of type sequenceType that were never shown to the
        // user, if there are any
        ArrayList<Sequence> pendingSequences = sequencesStorage.getPendingSequences(
                sequenceType);

        if (pendingSequences != null) {
            Logger.d(TAG, "Reusing previously pending sequence of type {}", sequenceType);
            sequence = pendingSequences.get(0);
        } else {
            Logger.d(TAG, "Creating new sequence of type {}",sequenceType);
            sequence = sequenceBuilder.buildSave(sequenceType);
        }

        // Update the sequence's status
        Logger.d(TAG, "Setting sequence status and timestamp, and saving");
        sequence.retainSaves();
        sequence.setNotificationSystemTimestamp(
                Calendar.getInstance().getTimeInMillis());
        sequence.setStatus(Sequence.STATUS_PENDING);
        sequence.flushSaves();

        // Get a timestamp for the sequence
        SntpClientCallback sntpCallback = new SntpClientCallback() {

            private final String TAG = "SntpClientCallback";

            @Override
            public void onTimeReceived(SntpClient sntpClient) {
                if (sntpClient != null) {
                    sequence.setNotificationNtpTimestamp(sntpClient.getNow());
                    Logger.i(TAG, "Received and saved NTP time for " +
                            "sequence notification");
                } else {
                    Logger.e(TAG, "Received successful NTP request but " +
                            "sntpClient is null");
                }
            }

        };

        Logger.i(TAG, "Launching NTP request");
        sntpClient.asyncRequestTime(sntpCallback);
    }

    /**
     * Cancel any pending {@link Sequence}s already notified.
     */
    private synchronized void cancelPendingSequences() {
        Logger.d(TAG, "Cancelling pending sequences of type {}", sequenceType);
        ArrayList<Sequence> pendingSequences = sequencesStorage.getPendingSequences(
                sequenceType);
        if (pendingSequences != null) {
            for (Sequence sequence : pendingSequences) {
                notificationManager.cancel(TAG, sequence.getId());
                sequencesStorage.remove(sequence.getId());
            }
        } else {
            Logger.v(TAG, "No pending sequences of type {} to cancel", sequenceType);
        }
    }

    private void startSchedulerService() {
        Logger.d(TAG, "Starting ProbeSchedulerService");

        // FIXME: if type == MEQ, schedule the right scheduler
        Intent schedulerIntent = new Intent(this, ProbeSchedulerService.class);
        startService(schedulerIntent);
    }

}
