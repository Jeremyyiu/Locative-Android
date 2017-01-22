package io.locative.app.service;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

import javax.inject.Inject;

import io.locative.app.LocativeApplication;
import io.locative.app.R;
import io.locative.app.model.Geofences;
import io.locative.app.network.LocativeApiWrapper;
import io.locative.app.network.RequestManager;
import io.locative.app.network.SessionManager;
import io.locative.app.notification.NotificationManager;
import io.locative.app.persistent.GeofenceProvider;
import io.locative.app.persistent.Storage;
import io.locative.app.utils.Preferences;

public class ReceiveTransitionsIntentService extends IntentService {

    public static final String TRANSITION_INTENT_SERVICE = "ReceiveTransitionsIntentService";

    private final String TAG = "TRANSITION";

    @Inject
    LocativeApiWrapper mLocativeNetworkingWrapper;

    @Inject
    SessionManager mSessionManager;

    @Inject
    RequestManager mRequestManager;

    @Inject
    SharedPreferences mPreferences;

    @Inject
    NotificationManager mNotificationManager;

    @Inject
    TriggerManager mTriggerManager;

    @Inject
    Storage mStorage;

    public ReceiveTransitionsIntentService() {
        super(TRANSITION_INTENT_SERVICE);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ((LocativeApplication) getApplication()).inject(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "Geofencing event occured");
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Location Services error: " + geofencingEvent.getErrorCode());
            return;
        }
        Log.i(TAG, "Location Services geofencingEvent: " + geofencingEvent);

        int transitionType = geofencingEvent.getGeofenceTransition();

        List<Geofence> triggeredGeofences = geofencingEvent.getTriggeringGeofences();

        for (Geofence geofence : triggeredGeofences) {
            Log.d(TAG, "onHandle:" + geofence.getRequestId());
            processGeofence(geofence, transitionType);
        }
    }

    private void processGeofence(final Geofence geofence, final int transitionType) {

        ContentResolver resolver = this.getContentResolver();
        Uri uri = Uri.parse("content://" + getString(R.string.authority) + "/geofences");
        String query =  "custom_id = ?";
        String[] params = new String[]{geofence.getRequestId()};

        Cursor cursor = resolver.query(uri, null, query, params, null);
        if (cursor == null || cursor.getCount() == 0) {
            return; // TODO: Handle errors here
        }

        cursor.moveToFirst();
        Geofences.Geofence fence = GeofenceProvider.fromCursor(cursor);
        cursor.close();

        boolean hasRelevantUrl = mPreferences.getString(Preferences.HTTP_URL, "").length() > 0;
        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ||
                transitionType == Geofence.GEOFENCE_TRANSITION_DWELL) {

            if (fence.enterUrl != null && fence.enterUrl.length() > 0) {
                hasRelevantUrl = true;
            }

            // If trigger threshold and we're not dwelling: bail out
            if (mPreferences.getBoolean(Preferences.TRIGGER_THRESHOLD_ENABLED, false)) {
                if (transitionType != Geofence.GEOFENCE_TRANSITION_DWELL) {
                    this.stopService(new Intent(this, TransitionService.class));
                    return;
                }
            }

            if (fence.currentlyEntered == 1) {
                this.stopService(new Intent(this, TransitionService.class));
                return;
            }

            fence.currentlyEntered = 1;
            mStorage.insertOrUpdateFence(fence);

            this.stopService(new Intent(this, TransitionService.class));

            mTriggerManager.triggerTransition(fence, transitionType, hasRelevantUrl);

        } else if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {

            if (fence.exitUrl != null && fence.exitUrl.length() > 0) {
                hasRelevantUrl = true;
            }

            // If trigger threshold and we haven't entered the location yet: bail out
            if (mPreferences.getBoolean(Preferences.TRIGGER_THRESHOLD_ENABLED, false)) {
                if (fence.currentlyEntered != 1) {
                    return;
                }
                // Start exit service
                Intent service = new Intent(this, TransitionService.class);
                service.putExtra(TransitionService.EXTRA_GEOFENCE, fence);
                service.putExtra(TransitionService.EXTRA_TRANSITION_TYPE, transitionType);
                service.putExtra(TransitionService.EXTRA_HAS_RELEVANT_URL, hasRelevantUrl);
                this.startService(service);
                return;
            }

            fence.currentlyEntered = 0;
            mStorage.insertOrUpdateFence(fence);

            mTriggerManager.triggerTransition(fence, transitionType, hasRelevantUrl);
        }
    }

    private LocativeApplication getApp() {
        return (LocativeApplication) getApplication();
    }

    private void removeGeofences(List<String> requestIds) {
        Intent intent = new Intent(getApplicationContext(), GeofencingService.class);

        String[] ids = new String[0];
        intent.putExtra(GeofencingService.EXTRA_REQUEST_IDS, requestIds.toArray(ids));
        intent.putExtra(GeofencingService.EXTRA_ACTION, GeofencingService.Action.REMOVE);

        startService(intent);
    }
}