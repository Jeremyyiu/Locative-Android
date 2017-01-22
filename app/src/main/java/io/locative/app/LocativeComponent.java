package io.locative.app;

import javax.inject.Singleton;

import dagger.Component;
import io.locative.app.modules.AppModule;
import io.locative.app.modules.NetworkingModule;
import io.locative.app.modules.PersistencyModule;
import io.locative.app.network.LocativeApiWrapper;
import io.locative.app.notification.NotificationManager;
import io.locative.app.service.BeaconService;
import io.locative.app.service.ReceiveTransitionsIntentService;
import io.locative.app.network.SessionManager;
import io.locative.app.service.TransitionService;
import io.locative.app.service.TriggerManager;
import io.locative.app.view.AddEditGeofenceActivity;
import io.locative.app.view.BaseActivity;
import io.locative.app.view.GeofencesActivity;
import io.locative.app.view.SettingsActivity;

@Singleton
@Component(modules = {AppModule.class, NetworkingModule.class, PersistencyModule.class})
public interface LocativeComponent {
    void inject(LocativeApiWrapper object);
    void inject(ReceiveTransitionsIntentService object);
    void inject(GeofencesActivity object);
    void inject(SettingsActivity object);
    void inject(BaseActivity object);
    void inject(AddEditGeofenceActivity object);
    void inject(SessionManager object);
    void inject(TriggerManager object);
    void inject(TransitionService object);
    void inject(NotificationManager object);
    void inject(BeaconService object);
}
