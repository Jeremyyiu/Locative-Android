package io.locative.app.service;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.locative.app.R;
import io.locative.app.beacon.BeaconItem;
import io.locative.app.geo.GeofenceErrorMessages;
import io.locative.app.model.Geofences;
import io.locative.app.notification.NotificationManager;
import io.locative.app.utils.Constants;

public class LocativeService extends Service implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String EXTRA_REQUEST_IDS = "requestId";
    public static final String EXTRA_GEOFENCE = "geofence";
    public static final String EXTRA_ACTION = "action";
    private static final String TAG = "GEO";

    private final List<Geofence> mGeofenceListsToAdd = new ArrayList<>();
    private final List<BeaconItem> mBeaconsToAdd = new ArrayList<>();

    private final List<String> mGeofenceListsToRemove = new ArrayList<>();
    private final List<BeaconItem> mBeaconsToRemove = new ArrayList<>();

    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    private Action mAction;
    private PendingIntent mGeofencePendingIntent;

    public enum Action implements Serializable {
        ADD,
        REMOVE
    }

    @Override
    @SuppressWarnings("unchecked")
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            return super.onStartCommand(intent, flags, startId);
        }

        Log.d(TAG, "Geofencing service started");

        mAction = (Action) intent.getSerializableExtra(EXTRA_ACTION);

        switch (mAction) {
            case ADD:
                ArrayList<Geofences.Geofence> geofences = (ArrayList<Geofences.Geofence>) intent.getSerializableExtra(EXTRA_GEOFENCE);
                for (Geofences.Geofence newGeofence : geofences) {
                    if (newGeofence.isBeacon()) {
                        mBeaconsToAdd.add(newGeofence.asBeacon());
                        continue;
                    }
                    Geofence googleGeofence = newGeofence.toGeofence();
                    if (googleGeofence != null) {
                        Log.d(Constants.LOG, "Adding Geofence: " + googleGeofence);
                        mGeofenceListsToAdd.add(googleGeofence);
                    }
                }
                break;
            case REMOVE:
                mGeofenceListsToRemove = Arrays.asList(intent.getStringArrayExtra(EXTRA_REQUEST_IDS));
                break;
        }
        // Kick off the request to build GoogleApiClient.
        buildGoogleApiClient();
        mGoogleApiClient.connect();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Geofencing service destroyed");
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "Location client connected");

        switch (mAction) {
            case ADD:
                Log.d(TAG, "Location client adds geofence");
                if (mGeofenceListsToAdd.size() > 0) {
                    GeofencingRequest request = getGeofencingRequest(mGeofenceListsToAdd);
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        new NotificationManager(this).showNotification(getResources().getString(R.string.app_name), getResources().getString( R.string.error_permission_refused));
                        return;
                    }
                    PendingResult<Status> result = LocationServices.GeofencingApi.addGeofences(mGoogleApiClient, request, getGeofencePendingIntent());
                    result.setResultCallback(new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status status) {
                            if (status.isSuccess()) {
                                Log.d(TAG, "Geofences added " + mGeofenceListsToAdd);
                            } else {
                                // Get the status code for the error and log it using a user-friendly message.
                                String errorMessage = GeofenceErrorMessages.getErrorString(LocativeService.this, status.getStatusCode());
                                Log.e(TAG, errorMessage);
                            }
                        }
                    });
                }
                break;
            case REMOVE:
                Log.d(TAG, "Location client removes geofence");
                if (mGeofenceListsToRemove.size() > 0) {
                    PendingResult<Status> result = LocationServices.GeofencingApi.removeGeofences(mGoogleApiClient, mGeofenceListsToRemove);
                    result.setResultCallback(new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status status) {
                            if (status.isSuccess()) {
                                Log.d(TAG, "Geofences removed " + mGeofenceListsToRemove);
                            } else {
                                // Get the status code for the error and log it using a user-friendly message.
                                String errorMessage = GeofenceErrorMessages.getErrorString(LocativeService.this, status.getStatusCode());
                                Log.e(TAG, errorMessage);
                            }
                        }
                    });
                }
                break;
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason.
        Log.i(TAG, "Connection suspended");

        // onConnected() will be called again automatically when the service reconnects
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent == null) {
            Intent intent = new Intent(this, ReceiveTransitionsIntentService.class);
            // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
            // calling addGeofences() and removeGeofences().
            mGeofencePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return mGeofencePendingIntent;
    }

    private static GeofencingRequest getGeofencingRequest(List<Geofence> geofenceList) {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        // The INITIAL_TRIGGER_ENTER flag indicates that geofencing service should trigger a
        // GEOFENCE_TRANSITION_ENTER notification when the geofence is added and if the device
        // is already inside that geofence.
        builder.setInitialTrigger(0);
        builder.addGeofences(geofenceList);
        return builder.build();
    }
}