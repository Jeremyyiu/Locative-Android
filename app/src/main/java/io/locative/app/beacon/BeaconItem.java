package io.locative.app.beacon;

import android.support.annotation.NonNull;

import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.Region;

import java.util.UUID;

import io.locative.app.model.Geofences;
import io.locative.app.persistent.BeaconProvider;
import io.locative.app.persistent.EditableItem;

public class BeaconItem extends EditableItem {
    private int mId;
    private BeaconProvider.BeaconType mType;
    private String mCustomId;
    private int mEnterMethod;
    private String mEnterUrl;
    private int mExitMethod;
    private String mExitUrl;
    private int mHttpAuth;
    private String mHttpUsername;
    private String mHttpPassword;
    private int mTriggers;
    private String mUuid;
    private int mMajor;
    private int mMinor;

    public BeaconItem(
            int id,
            BeaconProvider.BeaconType type,
            String customId,
            int enterMethod,
            String enterUrl,
            int exitMethod,
            String exitUrl,
            int httpAuth,
            String httpUsername,
            String httpPassword,
            int triggers,
            String uuid,
            int major,
            int minor
    ) {
        mId = id;
        mCustomId = customId;
        mEnterMethod = enterMethod;
        mEnterUrl = enterUrl;
        mExitMethod = exitMethod;
        mExitUrl = exitUrl;
        mHttpAuth = httpAuth;
        mHttpUsername = httpUsername;
        mHttpPassword = httpPassword;
        mTriggers = triggers;
        mUuid = uuid;
        mMajor = major;
        mMinor = minor;
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

    @Override
    @NonNull
    public String getDatabaseId() {
        return String.valueOf(mId);
    }
}
