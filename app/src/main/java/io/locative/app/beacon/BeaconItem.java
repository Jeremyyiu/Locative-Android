package io.locative.app.beacon;

import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.Region;

import java.util.UUID;

import io.locative.app.model.Geofences;

public class BeaconItem {
    private Geofences.Geofence mGeofence;
    private String mUuid;
    private int mMajor;
    private int mMinor;

    public BeaconItem(Geofences.Geofence geofence, String uuid, int major, int minor) {
        mGeofence = geofence;
        mUuid = uuid;
        mMajor = major;
        mMinor = minor;
    }

    public Geofences.Geofence getGeofence() {
        return mGeofence;
    }

    public String getUuid() {
        return mUuid;
    }

    public int getMajor() {
        return mMajor;
    }

    public int getMinor() {
        return mMinor;
    }

    protected Region toRegion() {
        return new Region(mUuid, Identifier.fromUuid(UUID.fromString(mUuid)), Identifier.fromInt(mMajor), Identifier.fromInt(mMinor));
    }
}
