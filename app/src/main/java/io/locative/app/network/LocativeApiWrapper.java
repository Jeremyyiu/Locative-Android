package io.locative.app.network;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.threeten.bp.Instant;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeParseException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.locative.app.model.Account;
import io.locative.app.model.EventType;
import io.locative.app.model.Fencelog;
import io.locative.app.model.Geofences;
import io.locative.app.model.Notification;
import io.locative.app.network.callback.CheckSessionCallback;
import io.locative.app.network.callback.GetAccountCallback;
import io.locative.app.persistent.GeofenceProvider;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

@Singleton
public class LocativeApiWrapper {
    private static final String TIMEOUT = "timeout";
    private static final String CONNECT_TIMED_OUT = "connect timed out";

    @Inject
    LocativeApiWrapper() {
    }

    @Inject
    LocativeApiService mService;

    @Inject
    JsonParser mParser;

    private final GsonToGeofenceConverter GEOFENCE_CONVERTER = new GsonToGeofenceConverter();
    private final GsonToFencelogConverter FENCELOG_CONVERTER = new GsonToFencelogConverter();
    private final GsonToNotificationConverter NOTIFICATION_CONVERTER = new GsonToNotificationConverter();

    public void doCheckSession(String sessionId, final CheckSessionCallback callback) {
        mService.checkSession(sessionId, new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                callback.onFinished(
                        (response.getStatus() == 200)
                );
            }

            @Override
            public void failure(RetrofitError error) {
                if (error.getCause().getMessage().equals(TIMEOUT) ||
                        error.getCause().getMessage().equals(CONNECT_TIMED_OUT)) {
                    // we've timed out, so we assume the session is still valid and will
                    // retry again later, bailing out here would lead to terrible UX
                    // as users would get logged it without no good reason
                    callback.onFinished(true);
                    return;
                }
                callback.onFinished(false);
            }
        });
    }

    public void doGetAccount(String sessionId, final GetAccountCallback callback) {
        mService.getAccount(sessionId, new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                if (response.getStatus() != 200) {
                    callback.onFailure();
                    return;
                }
                JsonObject account = mParser.parse(s).getAsJsonObject();
                callback.onSuccess(
                        new Account(
                                account.get("username").getAsString(),
                                account.get("email").getAsString(),
                                account.get("avatar").getAsString()
                        )
                );
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onFailure();
            }
        });
    }

    public void doDispatchFencelog(String sessionId, Fencelog fencelog, final LocativeNetworkingCallback callback) {
        mService.dispatchFencelog(
                sessionId,
                fencelog.longitude,
                fencelog.latitude,
                fencelog.locationId,
                fencelog.httpUrl,
                fencelog.httpMethod,
                fencelog.httpResponseCode,
                fencelog.eventType.getEventName(),
                fencelog.fenceType,
                fencelog.origin,
                new Callback<String>() {
                    @Override
                    public void success(String s, Response response) {
                        if (callback != null) {
                            callback.onDispatchFencelogFinished(true);
                        }
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        if (callback != null) {
                            callback.onDispatchFencelogFinished(false);
                        }
                    }
                });
    }

    public void getGeofences(String sessionId, final LocativeNetworkingCallback callback) {
        mService.getGeofences(sessionId, new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                JsonElement json = mParser.parse(s);
                callback.onGetGeoFencesFinished(GEOFENCE_CONVERTER.makeList(json));
            }

            @Override
            public void failure(RetrofitError error) {
                // TODO: Implement error handling here
            }
        });
    }

    public void getFenceLogs(String sessionId, int limit, final LocativeNetworkingCallback callback) {
        mService.getFencelogs(sessionId, limit, new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                callback.onGetFencelogsFinished(FENCELOG_CONVERTER.makeList(mParser.parse(s)));
            }

            @Override
            public void failure(RetrofitError error) {
                // TODO: Implement error handling here
            }
        });
    }

    public void getNotifications(String sessionId, final LocativeNetworkingCallback callback) {
        mService.getNotifications(sessionId, new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                callback.onGetNotificationsFinished(NOTIFICATION_CONVERTER.makeList(mParser.parse(s)));
            }

            @Override
            public void failure(RetrofitError error) {
                // TODO: Implement error handling here
            }
        });
    }

    private class GsonToGeofenceConverter {
        private static final String JSONKEY_ENABLED = "enabled",
                JSONKEY_LOCATIONID = "locationId",
                JSONKEY_UUID = "uuid",
                JSONKEY_LOCATION = "location",
                JSONKEY_BASICAUTH = "basicAuth",
                JSONKEY_TRIGGERONLEAVE = "triggerOnLeave",
                JSONKEY_TRIGGERONARRIVAL = "triggerOnArrival",
                JSONKEY_LAT = "lat",
                JSONKEY_LONG = "lon",
                JSONKEY_RADIUS = "radius",
                JSONKEY_USERNAME = "username",
                JSONKEY_PASSWORD = "password",
                JSONKEY_METHOD = "method",
                JSONKEY_URL = "url",
                JSONKEY_GEOFENCES = "geofences";

        public List<Geofences.Geofence> makeList(JsonElement json) {
            return this.getGeofences(json.getAsJsonObject().getAsJsonArray(JSONKEY_GEOFENCES));
        }

        @NonNull
        private List<Geofences.Geofence> getGeofences(JsonArray geofencesJson) {
            List<Geofences.Geofence> geofences = new ArrayList<>(geofencesJson.size());
            for (JsonElement geofenceJson : geofencesJson)
                geofences.add(makeGeofence(geofenceJson.getAsJsonObject()));
            return geofences;
        }

        private Geofences.Geofence makeGeofence(JsonObject geofenceJson) {
            String locationUUID = geofenceJson.get(JSONKEY_UUID).getAsString();
            String locationId = geofenceJson.get(JSONKEY_LOCATIONID).getAsString();
            JsonObject location = geofenceJson.getAsJsonObject(JSONKEY_LOCATION);
            JsonObject basicAuth = geofenceJson.getAsJsonObject(JSONKEY_BASICAUTH);
            JsonObject triggerOnLeave = geofenceJson.getAsJsonObject(JSONKEY_TRIGGERONLEAVE);
            JsonObject triggerOnArrival = geofenceJson.getAsJsonObject(JSONKEY_TRIGGERONARRIVAL);
            int triggers = createTrigger(triggerOnLeave, triggerOnArrival);
            double lat = location.get(JSONKEY_LAT).getAsDouble();
            double lon = location.get(JSONKEY_LONG).getAsDouble();
            int radius = location.get(JSONKEY_RADIUS).getAsInt();
            String addressLine = Double.toString(lat) + ", " + Double.toString(lon);
            return new Geofences.Geofence(
                    locationUUID,
                    locationId,
                    addressLine,
                    triggers,
                    lat,
                    lon,
                    radius,
                    basicAuth.get(JSONKEY_ENABLED).getAsBoolean() ? 1 : 0,
                    basicAuth.get(JSONKEY_USERNAME).getAsString(),
                    basicAuth.get(JSONKEY_PASSWORD).getAsString(),
                    triggerOnArrival.get(JSONKEY_METHOD).getAsInt(),
                    triggerOnArrival.get(JSONKEY_URL).getAsString(),
                    triggerOnLeave.get(JSONKEY_METHOD).getAsInt(),
                    triggerOnLeave.get(JSONKEY_URL).getAsString(),
                    0 // Currently Entered
            );
        }

        private int createTrigger(JsonObject triggerOnLeave, JsonObject triggerOnArrival) {
            int trigger = 0;
            if (triggerOnLeave.get(JSONKEY_ENABLED).getAsBoolean())
                trigger |= GeofenceProvider.TRIGGER_ON_EXIT;
            if (triggerOnArrival.get(JSONKEY_ENABLED).getAsBoolean())
                trigger |= GeofenceProvider.TRIGGER_ON_ENTER;
            return trigger;
        }
    }

    private class GsonToFencelogConverter {
        private static final String JSONKEY_FENCELOGS = "fencelogs",
                JSONKEY_ORIGIN = "origin",
                JSONKEY_CREATEDAT = "created_at",
                JSONKEY_TYPE = "fenceType",
                JSONKEY_EVENT = "eventType",
                JSONKEY_HTTPMETHOD = "httpMethod",
                JSONKEY_HTTPURL = "httpUrl",
                JSONKEY_LOCATIONID = "locationId";
        private final SimpleDateFormat FORMATTER = new SimpleDateFormat();

        public List<Fencelog> makeList(JsonElement element) {
            return this.getFencelogs(element.getAsJsonObject().getAsJsonArray(JSONKEY_FENCELOGS));
        }

        private List<Fencelog> getFencelogs(JsonArray logsJson) {
            List<Fencelog> logs = new ArrayList<Fencelog>(logsJson.size());
            for (JsonElement logJson : logsJson)
                logs.add(buildFenceLog(logJson.getAsJsonObject()));
            return logs;
        }

        private Fencelog buildFenceLog(JsonObject fenceJson) {
            Fencelog fence = new Fencelog();
            fence.origin = fenceJson.get(JSONKEY_ORIGIN).getAsString();
            fence.locationId = fenceJson.get(JSONKEY_LOCATIONID).getAsString();
            fence.httpMethod = fenceJson.get(JSONKEY_HTTPMETHOD).getAsString();
            fence.eventType = fenceJson.get(JSONKEY_EVENT).getAsString().equals(EventType.ENTER.getEventName()) ? EventType.ENTER : EventType.EXIT;
            fence.httpUrl = fenceJson.get(JSONKEY_HTTPURL).getAsString();
            fence.fenceType = fenceJson.get(JSONKEY_TYPE).getAsString();
            try {
                String dateString = fenceJson.get(JSONKEY_CREATEDAT).getAsString();
                ZonedDateTime zdt = ZonedDateTime.parse(dateString);
                zdt = zdt.withZoneSameInstant(ZonedDateTime.now().getZone());
                fence.createdAt = zdt.toLocalDateTime();
            } catch (DateTimeParseException dtpe) {
                Log.d(getClass().getName(), dtpe.getParsedString());
            }
            return fence;
        }
    }

    private class GsonToNotificationConverter {
        private static final String
                JSONKEY_NOTIFICATIONS = "notifications",
                JSONKEY_MESSAGE = "message",
                JSONKEY_CREATEDAT = "created_at";

        public List<Notification> makeList(JsonElement element) {
            return this.getNotifications(element.getAsJsonObject().getAsJsonArray(JSONKEY_NOTIFICATIONS));
        }

        private List<Notification> getNotifications(JsonArray notificationJson) {
            List<Notification> logs = new ArrayList<Notification>(notificationJson.size());
            for (JsonElement notiJson : notificationJson)
                logs.add(buildNotification(notiJson.getAsJsonObject()));
            return logs;
        }

        private Notification buildNotification(JsonObject notificationJson) {
            Notification notification = new Notification();
            notification.message = notificationJson.get(JSONKEY_MESSAGE).getAsString();
            notification.timestamp = Instant.parse(notificationJson.get(JSONKEY_CREATEDAT).getAsString()).toEpochMilli();
            return notification;
        }
    }
}
