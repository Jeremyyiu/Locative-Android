package io.locative.app.network;


import java.util.List;

import io.locative.app.model.Fencelog;
import io.locative.app.model.Geofences;

// TODO clean this up, bad design

public interface LocativeNetworkingCallback {

    void onLoginFinished(boolean success, String sessionId);

    void onSignupFinished(boolean success, boolean userAlreadyExisting);

    void onCheckSessionFinished(boolean sessionValid);

    void onDispatchFencelogFinished(boolean success);

    void onGetGeoFencesFinished(List<Geofences.Geofence> fences);

    void onGetFencelogsFinished(List<Fencelog> fencelogs);

    void onSyncReceived(List<Geofences.Geofence> geofences, List<String> deleted);

    void onStoredGeofence();
}
