package io.locative.app.network;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.json.JSONArray;
import org.json.JSONObject;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeParseException;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.locative.app.model.EventType;
import io.locative.app.model.Fencelog;
import io.locative.app.model.Geofences;
import io.locative.app.persistent.GeofenceProvider;
import io.locative.app.utils.AeSimpleSHA1;
import io.locative.app.utils.Constants;
import io.locative.app.view.AddEditGeofenceActivity;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

@Singleton
public class LocativeApiWrapper {
    public static final String UNNAMED_FENCE = "";

    @Inject
    LocativeApiService mService;
    @Inject JsonParser mParser;
    private final GsonToGeofenceConverter GEOFENCE_CONVERTER = new GsonToGeofenceConverter();
    private final GsonToFencelogConverter FENCELOG_CONVERTER = new GsonToFencelogConverter();

    public void doLogin(String username, String password, final LocativeNetworkingCallback callback) {
        mService.login(username, password, Constants.API_ORIGIN, new Callback<String>() {

            @Override
            public void success(String string, Response response) {
                Log.d(Constants.LOG, "Login Success: " + string);
                String sessionId = null;
                try {
                    JSONObject json = new JSONObject(string);
                    sessionId = json.getString("success");
                } catch (Exception e) {
                    Log.e(Constants.LOG, e.getMessage(), e);
                } finally {
                    callback.onLoginFinished(sessionId != null && sessionId.length() > 0, sessionId);
                }
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d(Constants.LOG, "Login Error: " + error);
                callback.onLoginFinished(false, null);
            }
        });
    }

    public void doSignup(String username, String password, String email, final LocativeNetworkingCallback callback) {
        String token = null;
        try {
            token = AeSimpleSHA1.SHA1(username + ":" + password + "%" + email);
            Log.d(Constants.LOG, "Token: " + token);
        } catch (Exception e) {
            Log.e(Constants.LOG, "Caught Exception: " + e);
        }

        mService.signup(username, password, email, token, new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                Log.d(Constants.LOG, "Signup Success: " + s);
                callback.onSignupFinished(true, false);
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d(Constants.LOG, "Signup Error: " + error);
                callback.onSignupFinished(false, error.getResponse().getStatus() == 409);
            }
        });
    }

    public void doCheckSession(String sessionId, final LocativeNetworkingCallback callback) {
        mService.checkSession(sessionId, new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                callback.onCheckSessionFinished(true);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onCheckSessionFinished(false);
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
                fencelog.httpResponse,
                fencelog.eventType.apiName,
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
            }
        });
    }

    public void getFenceLogs(String sessionId, final LocativeNetworkingCallback callback) {
        mService.getFencelogs(sessionId, new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                callback.onGetFencelogsFinished(FENCELOG_CONVERTER.makeList(mParser.parse(s)));
            }

            @Override
            public void failure(RetrofitError error) {

            }
        });
    }

    public void getSync(String sessionId, long lastSync, final LocativeNetworkingCallback callback) {
        mService.sync(lastSync, sessionId, new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                final GsonToGeofenceConverter converter = new GsonToGeofenceConverter();
                final JsonElement json = mParser.parse(s);
                final JsonObject object = json.getAsJsonObject();
                if (!object.get("error").getAsBoolean()) {
                    final List<Geofences.Geofence> geofences = converter.getGeofences(object.get("geofences").getAsJsonArray());
                    final List<String> deleted = new ArrayList<String>();
                    for (JsonElement element : object.get("deleted").getAsJsonArray())
                        deleted.add(element.getAsJsonObject().get("locationId").getAsString());
                    callback.onSyncReceived(geofences, deleted);
                }
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e("API", error.getMessage());
            }
        });
    }

    public void addGeofence(String sessionId, Geofences.Geofence geofence, final LocativeNetworkingCallback callback) {
        String json = convertToJson(geofence).toString();
        mService.addGeofence(sessionId, new TypedJsonString(json), new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                final JsonElement json = mParser.parse(s);
                boolean error = json.getAsJsonObject().get("error").getAsBoolean();
                if (!error)
                    callback.onStoredGeofence();
            }

            @Override
            public void failure(RetrofitError error) {

            }
        });
    }

    public void updateGeofence(String sessionId, Geofences.Geofence geofence, final LocativeNetworkingCallback callback) {
        String json = convertToJson(geofence).toString();
        mService.updateGeofence(sessionId, geofence.id, new TypedJsonString(json), new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                final JsonElement json = mParser.parse(s);
                boolean error = json.getAsJsonObject().get("error").getAsBoolean();
                if (!error)
                    callback.onStoredGeofence();
            }

            @Override
            public void failure(RetrofitError error) {

            }
        });
    }

    private boolean isTriggerEnabled(int trigger, int mask) {
       return ( trigger & mask ) == mask;
    }

    public JsonObject convertToJson(Geofences.Geofence geofence) {
        JsonObject object = new JsonObject();
        object.addProperty("locationId", geofence.subtitle);
        JsonObject location = new JsonObject();
        location.addProperty("lon", geofence.longitude);
        location.addProperty("lat", geofence.latitude);
        location.addProperty("radius", geofence.radiusMeters);
        JsonObject basicAuth = new JsonObject();
        basicAuth.addProperty("enabled", geofence.hasAuthentication());
        basicAuth.addProperty("username", geofence.httpUsername);
        basicAuth.addProperty("password", geofence.httpPassword);
        JsonObject triggerOnArrival = new JsonObject();
        triggerOnArrival.addProperty("enabled", isTriggerEnabled(geofence.triggers, GeofenceProvider.TRIGGER_ON_ENTER));
        triggerOnArrival.addProperty("method", geofence.enterMethod);
        triggerOnArrival.addProperty("url", geofence.enterUrl);
        JsonObject triggerOnLeave = new JsonObject();
        triggerOnLeave.addProperty("enabled", isTriggerEnabled(geofence.triggers, GeofenceProvider.TRIGGER_ON_EXIT));
        triggerOnLeave.addProperty("method", geofence.exitMethod);
        triggerOnLeave.addProperty("url", geofence.exitUrl);
        object.add("basicAuth", basicAuth);
        object.add("location", location);
        object.add("triggerOnArrival", triggerOnArrival);
        object.add("triggerOnLeave", triggerOnLeave);
        return object;
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
                JSONKEY_GEOFENCES = "geofences",
                JSONKEY_LAST_UPDATED = "modified_at";

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

            final DateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String locationId = geofenceJson.get(JSONKEY_LOCATIONID).getAsString();
            String subtitle = geofenceJson.get(JSONKEY_UUID).getAsString();
            JsonObject location = geofenceJson.getAsJsonObject(JSONKEY_LOCATION);
            JsonObject basicAuth = geofenceJson.getAsJsonObject(JSONKEY_BASICAUTH);
            JsonObject triggerOnLeave = geofenceJson.getAsJsonObject(JSONKEY_TRIGGERONLEAVE);
            JsonObject triggerOnArrival = geofenceJson.getAsJsonObject(JSONKEY_TRIGGERONARRIVAL);
            int triggers = createTrigger(triggerOnLeave, triggerOnArrival);
            float lat = location.get(JSONKEY_LAT).isJsonNull() ? 0.0f : location.get(JSONKEY_LAT).getAsFloat();
            float lon = location.get(JSONKEY_LONG).isJsonNull() ? 0.0f : location.get(JSONKEY_LONG).getAsFloat();
            int radius = location.get(JSONKEY_RADIUS).isJsonNull() ? 0 : location.get(JSONKEY_RADIUS).getAsInt();
            long time = 0;
            try {
                time = FORMAT.parse(geofenceJson.get(JSONKEY_LAST_UPDATED).getAsString()).getTime();
            } catch (ParseException exception) {

            }
            return new Geofences.Geofence(
                    subtitle,
                    subtitle,
                    locationId,
                    triggers,
                    lat,
                    lon,
                    radius,
                    basicAuth.get(JSONKEY_ENABLED).getAsBoolean() ? 1 : 0,
                    basicAuth.get(JSONKEY_USERNAME) == null || basicAuth.get(JSONKEY_USERNAME).isJsonNull() ? "" : basicAuth.get(JSONKEY_USERNAME).getAsString(),
                    basicAuth.get(JSONKEY_PASSWORD) == null || basicAuth.get(JSONKEY_PASSWORD).isJsonNull() ? "" : basicAuth.get(JSONKEY_PASSWORD).getAsString(),
                    triggerOnArrival.get(JSONKEY_METHOD) == null || triggerOnArrival.get(JSONKEY_METHOD).isJsonNull() || triggerOnArrival.get(JSONKEY_METHOD).getAsString().equals("") ? 0 : triggerOnArrival.get(JSONKEY_METHOD).getAsInt(),
                    triggerOnArrival.get(JSONKEY_URL) == null || triggerOnArrival.get(JSONKEY_URL).isJsonNull() ? "" : triggerOnArrival.get(JSONKEY_URL).getAsString(),
                    triggerOnLeave.get(JSONKEY_METHOD) == null || triggerOnLeave.get(JSONKEY_METHOD).isJsonNull() || triggerOnLeave.get(JSONKEY_METHOD).getAsString().equals("")? 0 : triggerOnLeave.get(JSONKEY_METHOD).getAsInt(),
                    triggerOnLeave.get(JSONKEY_URL) == null || triggerOnLeave.get(JSONKEY_URL).isJsonNull() ? "" : triggerOnLeave.get(JSONKEY_URL).getAsString(),
                    time
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
            JSONKEY_HTTPRESPONSE = "httpResponse",
            JSONKEY_HTTPMETHOD = "httpMethod",
            JSONKEY_HTTPURL = "httpUrl",
            JSONKEY_LOCATIONID = "locationId";
        private final SimpleDateFormat FORMATTER = new SimpleDateFormat();

        public List<Fencelog> makeList(JsonElement element) {
            return this.getFencelogs(element.getAsJsonObject().getAsJsonArray(JSONKEY_FENCELOGS));
        }

        private List<Fencelog> getFencelogs(JsonArray logsJson) {
            List<Fencelog> logs = new ArrayList<Fencelog>(logsJson.size());
            for (JsonElement logJson: logsJson)
                logs.add(buildFenceLog(logJson.getAsJsonObject()));
            return logs;
        }

        private Fencelog buildFenceLog(JsonObject fenceJson) {
            Fencelog fence = new Fencelog();
            fence.origin = fenceJson.get(JSONKEY_ORIGIN).getAsString();
            fence.locationId = fenceJson.get(JSONKEY_LOCATIONID).getAsString();
            fence.httpMethod = fenceJson.get(JSONKEY_HTTPMETHOD).getAsString();
            fence.eventType = fenceJson.get(JSONKEY_EVENT).getAsString().equals(EventType.ENTER.apiName) ? EventType.ENTER : EventType.EXIT;
            fence.httpUrl = fenceJson.get(JSONKEY_HTTPURL).getAsString();
            fence.httpResponse = fenceJson.get(JSONKEY_HTTPRESPONSE).getAsString();
            fence.fenceType = fenceJson.get(JSONKEY_TYPE).getAsString();
            try {
                String dateString = fenceJson.get(JSONKEY_CREATEDAT).getAsString();
                ZonedDateTime zdt =  ZonedDateTime.parse(dateString);
                zdt = zdt.withZoneSameInstant(ZonedDateTime.now().getZone());
                fence.createdAt = zdt.toLocalDateTime();
            } catch(DateTimeParseException dtpe) {
                Log.d(getClass().getName(), dtpe.getParsedString());
            }
            return fence;
        }
    }
}
