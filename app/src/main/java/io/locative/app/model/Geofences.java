package io.locative.app.model;

import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.locative.app.persistent.GeofenceProvider;
import io.locative.app.utils.Constants;

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
        ITEM_MAP.put(item.id, item);
    }

    public static class Geofence implements Serializable {
        public final String id;
        public final String title;
        public final String subtitle;
        public final int triggers;
        public final float latitude;
        public final float longitude;
        public final int radiusMeters;
        public final int httpAuth;
        public final String httpUsername;
        public final String httpPassword;
        public final int enterMethod;
        public final String enterUrl;
        public final int exitMethod;
        public final String exitUrl;
        public final long lastChanged;

        public Geofence(
                String id,
                String title,
                String subtitle,
                int triggers,
                float latitude,
                float longitude,
                int radiusMeters,
                int httpAuth,
                String httpUsername,
                String httpPassword,
                int enterMethod,
                String enterUrl,
                int exitMethod,
                String exitUrl,
                long lastChanged) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
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
            this.lastChanged = lastChanged;
        }

        public Geofence setId(String id) {
            return new Geofence(
                    id, title, subtitle, triggers, latitude, longitude, radiusMeters, httpAuth,
                    httpUsername, httpPassword, enterMethod, enterUrl, exitMethod, exitUrl, lastChanged
            );
        }

        public boolean hasAuthentication() {
            if (this.httpUsername == null || this.httpPassword == null) {
                return false;
            }
            return this.httpAuth == 1 && (this.httpUsername.length() > 0 && this.httpPassword.length() > 0);
        }

        @Override
        public String toString() {
            if (title != null && title.length() > 0) {
                return title;
            }
            if (subtitle != null && subtitle.length() > 0) {
                return subtitle;
            }
            return id;
        }

        public com.google.android.gms.location.Geofence toGeofence() {
            // Build a new Geofence object
            int transition = 0;
            boolean both = (
                    ((triggers & GeofenceProvider.TRIGGER_ON_ENTER) == GeofenceProvider.TRIGGER_ON_ENTER) &&
                            ((triggers & GeofenceProvider.TRIGGER_ON_EXIT) == GeofenceProvider.TRIGGER_ON_EXIT)
            );
            if (both) {
                Log.d(Constants.LOG, "ID: " + this.id + " trigger on BOTH");
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;
            } else if (((triggers & GeofenceProvider.TRIGGER_ON_ENTER) == GeofenceProvider.TRIGGER_ON_ENTER)) {
                Log.d(Constants.LOG, "ID: " + this.id + " trigger on ENTER");
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
            } else if (((triggers & GeofenceProvider.TRIGGER_ON_EXIT) == GeofenceProvider.TRIGGER_ON_EXIT)) {
                Log.d(Constants.LOG, "ID: " + this.id + " trigger on EXIT");
                transition |= com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;
            }
            Log.d(Constants.LOG, "Transition: " + transition);

            if (transition == 0) {
                return null;
            }

            return new com.google.android.gms.location.Geofence.Builder()
                    .setRequestId(this.id)
                    .setTransitionTypes(transition)
                    .setCircularRegion(
                            this.latitude,
                            this.longitude,
                            this.radiusMeters)
                    .setExpirationDuration(com.google.android.gms.location.Geofence.NEVER_EXPIRE)
                    .build();
        }
    }
}
