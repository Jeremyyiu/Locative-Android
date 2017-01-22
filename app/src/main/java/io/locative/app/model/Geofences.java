package io.locative.app.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.location.Geofence;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.locative.app.beacon.BeaconItem;
import javax.inject.Inject;

import io.locative.app.LocativeApplication;
import io.locative.app.persistent.EditableItem;
import io.locative.app.persistent.GeofenceProvider;
import io.locative.app.utils.Constants;
import io.locative.app.utils.Preferences;

public class Geofences {
    /**
     * An array of sample (dummy) items.
     */
    public static List<Geofence> ITEMS = new ArrayList<Geofence>();

    /**
     * A map of sample (dummy) items, by ID.
     */
    public static Map<String, Geofence> ITEM_MAP = new HashMap<String, Geofence>();

    public void clear() {
        ITEMS.clear();
    }

    public void addItem(Geofence item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.uuid, item);
    }

    public static class Geofence extends EditableItem {
        public String uuid;
        public String customId;
        public String name;
        public int triggers;
        public double latitude;
        public double longitude;
        public int radiusMeters;
        public int httpAuth;
        public String httpUsername;
        public String httpPassword;
        public int enterMethod;
        public String enterUrl;
        public int exitMethod;
        public String exitUrl;
        public int currentlyEntered;

        public String getRelevantId() {
            if (customId != null && customId.length() > 0) {
               return customId;
            }
            if (name != null && name.length() > 0) {
                return name;
            }
            return uuid;
        }

        public Geofence(
                String uuid,
                String customId,
                String name,
                int triggers,
                double latitude,
                double longitude,
                int radiusMeters,
                int httpAuth,
                String httpUsername,
                String httpPassword,
                int enterMethod,
                String enterUrl,
                int exitMethod,
                String exitUrl,
                int currentlyEntered
        ) {
            this.uuid = (uuid == null) ? UUID.randomUUID().toString() : uuid;
            this.customId = customId;
            this.name = name;
            this.triggers = triggers;
            this.latitude = latitude;
            this.longitude = longitude;
            this.radiusMeters = radiusMeters;
            this.httpAuth = httpAuth;
            this.httpUsername = httpUsername;
            this.httpPassword = httpPassword;
            this.enterMethod = enterMethod;
            this.enterUrl = enterUrl;
            this.exitMethod = exitMethod;
            this.exitUrl = exitUrl;
            this.currentlyEntered = currentlyEntered;
        }

        public boolean hasAuthentication() {
            if (this.httpUsername == null || this.httpPassword == null) {
                return false;
            }
            return this.httpAuth == 1 && (this.httpUsername.length() > 0 && this.httpPassword.length() > 0);
        }

        @Override
        public String toString() {
            return getRelevantId();
        }

        public com.google.android.gms.location.Geofence toGeofence() {
            // Build a new Geofence object
            int transition = 0;
            boolean both = (
                    ((triggers & GeofenceProvider.TRIGGER_ON_ENTER) == GeofenceProvider.TRIGGER_ON_ENTER) &&
                            ((triggers & GeofenceProvider.TRIGGER_ON_EXIT) == GeofenceProvider.TRIGGER_ON_EXIT)
            );
            if (both) {
                Log.d(Constants.LOG, "ID: " + this.uuid + " trigger on BOTH");
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_DWELL;
            } else if (((triggers & GeofenceProvider.TRIGGER_ON_ENTER) == GeofenceProvider.TRIGGER_ON_ENTER)) {
                Log.d(Constants.LOG, "ID: " + this.uuid + " trigger on ENTER");
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_DWELL;
            } else if (((triggers & GeofenceProvider.TRIGGER_ON_EXIT) == GeofenceProvider.TRIGGER_ON_EXIT)) {
                Log.d(Constants.LOG, "ID: " + this.uuid + " trigger on EXIT");
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_DWELL;
            }
            Log.d(Constants.LOG, "Transition: " + transition);

            if (transition == 0) {
                return null;
            }

            // Bail out if no radius is set, this does not solve the root casue but will at least fix
            // https://fabric.io/locative/android/apps/io.locative.app/issues/57989893ffcdc042508b6137
            // and
            // https://fabric.io/locative/android/apps/io.locative.app/issues/578195cbffcdc04250759c47
            if (radiusMeters == 0.0) {
                return null;
            }

            SharedPreferences preferences =
                    PreferenceManager.getDefaultSharedPreferences(LocativeApplication.getApplication().getApplicationContext());

            return new com.google.android.gms.location.Geofence.Builder()
                    .setRequestId(this.customId)
                    .setTransitionTypes(transition)
                    .setCircularRegion(
                            this.latitude,
                            this.longitude,
                            this.radiusMeters)
                    .setExpirationDuration(com.google.android.gms.location.Geofence.NEVER_EXPIRE)
                    .setLoiteringDelay(
                    preferences.getBoolean(Preferences.TRIGGER_THRESHOLD_ENABLED, false) ?
                            preferences.getInt(Preferences.TRIGGER_THRESHOLD_VALUE, Preferences.TRIGGER_THRESHOLD_VALUE_DEFAULT) :
                            0
                    ).build();
        }

        @NonNull
        @Override
        public String getDatabaseId() {
            return customId;
        }
    }
}
