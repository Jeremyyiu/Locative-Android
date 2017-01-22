package io.locative.app.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;

import javax.inject.Inject;

import io.locative.app.LocativeApplication;
import io.locative.app.LocativeComponent;
import io.locative.app.beacon.BeaconController;
import io.locative.app.beacon.BeaconItem;
import io.locative.app.model.Geofences;
import io.locative.app.utils.Constants;

public class BeaconService extends Service {
    public enum Action implements Serializable {
        ADD,
        REMOVE
    }

    public static final String EXTRA_BEACONS = "beacons";
    public static final String EXTRA_ACTION = "action";

    @Inject
    BeaconController mBeaconController;

    public BeaconService() {
        ((LocativeApplication)getApplicationContext()).getComponent().inject(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            return super.onStartCommand(null, flags, startId);
        }

        Log.d(Constants.LOG, "BeaconService started");

        final Action action = (Action) intent.getSerializableExtra(EXTRA_ACTION);
        final ArrayList<Geofences.Geofence> geofences = (ArrayList<Geofences.Geofence>) intent.getSerializableExtra(EXTRA_BEACONS);
        for (Geofences.Geofence newGeofence : geofences) {
//            if (newGeofence.isBeacon()) {
//                BeaconItem beacon = newGeofence.asBeacon();
//                if (beacon == null) {
//                    Log.e(Constants.LOG, "Could not start/stop ranging for beacon as it's null: " + newGeofence.toString());
//                    continue;
//                }
//                if (action == Action.ADD) {
//                    mBeaconController.startRanging(beacon);
//                    Log.d(Constants.LOG, "Started ranging for " + beacon.toString());
//                } else {
//                    mBeaconController.stopRanging(beacon);
//                    Log.d(Constants.LOG, "Stopped ranging for " + beacon.getUuid());
//                }
//            }
        }

        return super.onStartCommand(intent, flags, startId);
    }
}
