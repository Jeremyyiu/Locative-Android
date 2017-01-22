package io.locative.app.geo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;

import java.util.ArrayList;

import io.locative.app.R;
import io.locative.app.model.Geofences;
import io.locative.app.service.BeaconService;
import io.locative.app.service.GeofencingService;
import io.locative.app.persistent.GeofenceProvider;

public class StartupBroadCastReceiver extends BroadcastReceiver {

    public static final String TAG = "Locative";
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.i(TAG, "Starting Locative service.");

        Uri uri = Uri.parse("content://" + context.getString(R.string.authority) + "/geofences");
        Loader<Cursor> loader = new CursorLoader(context, uri, null, null, null, null);

        loader.registerListener(0, new Loader.OnLoadCompleteListener<Cursor>() {
            @Override
            public void onLoadComplete(Loader<Cursor> loader, Cursor data) {
                ArrayList<Geofences.Geofence> items = new ArrayList<>();
                if (data != null) {
                    while (data.moveToNext()) {
                        Geofences.Geofence item = GeofenceProvider.fromCursor(data);
                        Log.i(TAG, "Found geofence " +item.uuid);
                        items.add(item);
                    }
                }
                final Intent startGeofencingServiceIntent = new Intent(context, GeofencingService.class);
                startGeofencingServiceIntent.putExtra(GeofencingService.EXTRA_ACTION, GeofencingService.Action.ADD);
                startGeofencingServiceIntent.putExtra(GeofencingService.EXTRA_GEOFENCE, items);
                context.startService(startGeofencingServiceIntent);

                final Intent startBeaconServiceIntent = new Intent(context, BeaconService.class);
                startBeaconServiceIntent.putExtra(BeaconService.EXTRA_ACTION, BeaconService.Action.ADD);
                startBeaconServiceIntent.putExtra(BeaconService.EXTRA_BEACONS, items);
                context.startService(startBeaconServiceIntent);
            }
        });

        loader.startLoading();
    }
}