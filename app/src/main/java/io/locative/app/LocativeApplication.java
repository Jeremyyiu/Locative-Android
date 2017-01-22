package io.locative.app;

import android.app.Application;

import com.crashlytics.android.Crashlytics;
import com.facebook.stetho.Stetho;
import com.jakewharton.threetenabp.AndroidThreeTen;

import io.fabric.sdk.android.Fabric;
import io.locative.app.modules.AppModule;
import io.locative.app.modules.NetworkingModule;
import io.locative.app.modules.PersistencyModule;
import io.locative.app.notification.NotificationManager;
import io.locative.app.service.BeaconService;
import io.locative.app.service.ReceiveTransitionsIntentService;
import io.locative.app.service.TransitionService;
import io.locative.app.service.TriggerManager;

public class LocativeApplication extends Application {

    private static LocativeApplication mInstance;
    private LocativeComponent mComponent;

    @Override public void onCreate() {
        super.onCreate();
        mInstance = this;

        if (BuildConfig.USE_CRASHLYTICS) {
            Fabric.with(this, new Crashlytics());
        }

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this);
        }

        mComponent = DaggerLocativeComponent.builder()
                .appModule(new AppModule(this))
                .networkingModule(new NetworkingModule())
                .persistencyModule(new PersistencyModule(this))
                .build();
        AndroidThreeTen.init(this);
    }

    public void inject(ReceiveTransitionsIntentService object) {
        mComponent.inject(object);
    }

    public void inject(TriggerManager object) {
        mComponent.inject(object);
    }

    public void inject(TransitionService object) {
        mComponent.inject(object);
    }

    public void inject(NotificationManager object) {
        mComponent.inject(object);
    }

    public void inject(BeaconService object) {
        mComponent.inject(object);
    }

    public LocativeComponent getComponent() {
        return mComponent;
    }

    public static LocativeApplication getApplication() {
        return mInstance;
    }
}