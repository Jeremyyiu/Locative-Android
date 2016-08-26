package io.locative.app.view;


import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.locative.app.model.Geofences;
import io.locative.app.network.LocativeApiWrapper;
import io.locative.app.network.LocativeNetworkingAdapter;
import io.locative.app.persistent.Storage;

/**
 * Created by Jasper De Vrient on 25/08/2016.
 */
public class GeofenceSync {
    private final LocativeApiWrapper api;
    private final String sessionId;
    private final long lastSync;
    private final List<Geofences.Geofence> localGeofences;
    private final Context context;

    public interface SyncHandler {
        void onSyncCompleted();
        void onSyncFailed();
    }

    public GeofenceSync(final LocativeApiWrapper api, final String sessionId, final long lastSync, final List<Geofences.Geofence> localGeofences, final Context context) {
        this.api = api;
        this.sessionId = sessionId;
        this.localGeofences = localGeofences;
        this.lastSync = lastSync;
        this.context = context;
    }

    public void startSync(final SyncHandler syncHandler) {
        api.getSync(sessionId, lastSync, new LocativeNetworkingAdapter() {
            @Override
            public void onSyncReceived(final List<Geofences.Geofence> onlineGeofences, final List<String> deletedGeofences) {
                final Map<Geofences.Geofence, SyncAction> actions = new HashMap<>();
                final Map<String, Geofences.Geofence> local = new HashMap<>();
                final Map<String, Geofences.Geofence> server = new HashMap<>();
                for (Geofences.Geofence geofence:localGeofences)
                    local.put(geofence.subtitle, geofence);
                for (Geofences.Geofence geofence:onlineGeofences)
                    server.put(geofence.id, geofence);
                for (String locationId : deletedGeofences)
                    if (local.containsKey(locationId))
                        actions.put(local.get(locationId), SyncAction.DELETELOCAL);
                for (Geofences.Geofence serverGeofence: onlineGeofences)
                    if (local.containsKey(serverGeofence.id)) {
                        final Geofences.Geofence localGeofence = local.get(serverGeofence.id);
                        if (localGeofence.lastChanged < serverGeofence.lastChanged)
                            actions.put(serverGeofence, SyncAction.UPDATELOCAL);
                        else if (localGeofence.lastChanged > serverGeofence.lastChanged)
                            actions.put(localGeofence, SyncAction.UPDATESERVER);
                    } else
                        actions.put(serverGeofence, SyncAction.ADDLOCAL);
                for (Geofences.Geofence localGeofence : localGeofences)
                    if (!server.containsKey(localGeofence.subtitle))
                        actions.put(localGeofence, SyncAction.ADDSERVER);
                takeActions(actions, local, server, syncHandler);
            }
        });
    }

    private void takeActions(final Map<Geofences.Geofence, SyncAction> actions,
                             final Map<String, Geofences.Geofence> local,
                             final Map<String, Geofences.Geofence> server,
                             final SyncHandler synchandler) {
        Log.i("SYNC", "TAKE actions");
        DeferredTask task = DeferredTask.IDENTITY;
        DeferredTask endOfChain = task;
        for (final Map.Entry<Geofences.Geofence, SyncAction> action : actions.entrySet()) {
            switch (action.getValue()) {
                case ADDLOCAL:
                    endOfChain = endOfChain.then(new DeferredWork() {
                        @Override
                        public void doWork(DeferredTaskCallback callback) {
                            Storage.INSTANCE.insertOrUpdateFence(action.getKey(), context);
                            callback.done();
                        }
                    });
                    break;
                case ADDSERVER:
                    final Geofences.Geofence geofence = action.getKey();
                    endOfChain = endOfChain.then(new DeferredWork() {
                        @Override
                        public void doWork(final DeferredTaskCallback callback) {
                            api.addGeofence(sessionId, geofence, new LocativeNetworkingAdapter() {
                                @Override
                                public void onStoredGeofence() {
                                    callback.done(new DeferredWork() {
                                        @Override
                                        public void doWork(final DeferredTaskCallback callback) {
                                            api.getGeofences(sessionId, new LocativeNetworkingAdapter() {
                                                @Override
                                                public void onGetGeoFencesFinished(List<Geofences.Geofence> fences) {
                                                    for (Geofences.Geofence serverFence :
                                                            fences) {
                                                        if (serverFence.subtitle.equals(geofence.subtitle)) {
                                                            Storage.INSTANCE.insertOrUpdateFence(serverFence, context);
                                                            break;
                                                        }
                                                    }
                                                    callback.done();
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                    });
                    break;
                case DELETELOCAL:
                    endOfChain = endOfChain.then(new DeferredWork() {
                        @Override
                        public void doWork(DeferredTaskCallback callback) {
                            Storage.INSTANCE.deleteFence(action.getKey(), context);
                            callback.done();
                        }
                    });
                    break;
                case UPDATELOCAL:
                    endOfChain = endOfChain.then(new DeferredWork() {
                        @Override
                        public void doWork(DeferredTaskCallback callback) {
                            Storage.INSTANCE.insertOrUpdateFence(action.getKey(), context);
                            callback.done();
                        }
                    });
                    break;
                case UPDATESERVER:
                    endOfChain = endOfChain.then(new DeferredWork() {
                        @Override
                        public void doWork(final DeferredTaskCallback callback) {
                            api.updateGeofence(sessionId, action.getKey(), new LocativeNetworkingAdapter() {
                                @Override
                                public void onStoredGeofence() {
                                    callback.done();
                                }
                            });
                        }
                    });
                    break;
            }
        }
        endOfChain.then(new DeferredWork() {
            @Override
            public void doWork(DeferredTaskCallback callback) {
                synchandler.onSyncCompleted();
                Toast.makeText(context, "Sync done.", Toast.LENGTH_LONG).show();
                Log.i("SYNC", "done");
                callback.done();
            }
        }).error(new DeferredWork() {
            @Override
            public void doWork(DeferredTaskCallback callback) {
                Toast.makeText(context, "Sync failed.", Toast.LENGTH_LONG).show();
                synchandler.onSyncFailed();
                Log.i("SYNC", "error");
                callback.done();
            }
        });
        task.start();
    }

    private static class DeferredTask {
        private final DeferredWork work;
        private DeferredTask after = null;
        private DeferredTask err = null;
        private DeferredTask parent;
        public static final DeferredTask IDENTITY = new DeferredTask(new DeferredWork() {
            @Override
            public void doWork(DeferredTaskCallback callback) {
                callback.done();
            }
        });
        private class Callback implements GeofenceSync.DeferredTaskCallback {
            private final DeferredTask task;

            public Callback(DeferredTask task) {
                this.task = task;
            }
            @Override
            public void done(DeferredWork moreWork) {
                try {
                    Log.i("DEFERRED", "Done work");
                    if (moreWork != null) {
                        moreWork.doWork(this);
                    } else {
                        if (task.after != null) {
                            task.after.start();
                        }
                    }
                } catch (Exception ex) {
                    error();
                }
            }

            @Override
            public void done() {
                done(null);
            }

            @Override
            public void error() {
                Log.i("DEFERRED", "Done work");
                if (task.err != null) {
                    task.err.start();
                }
            }
        }
        public DeferredTask(final DeferredWork work) {
           this(work, null);
        }

        public DeferredTask(final DeferredWork work, final DeferredTask parent) {
            this.work = work;
            this.parent = parent;
        }

        public void start() {
            work.doWork(new Callback(this));
        }

        public DeferredTask then(DeferredWork work) {
            after = new DeferredTask(work);
            after.parent = this;
            return after;
        }

        public DeferredTask error(DeferredWork work) {
            err = new DeferredTask(work);
            err.parent = this;
            DeferredTask prev = parent;
            while (prev != null && prev.err != null) {
                prev.err = err;
                prev = prev.parent;
            }
            return err;
        }
    }

    private interface DeferredWork {
        void doWork(DeferredTaskCallback callback);
    }

    private interface DeferredTaskCallback {
        void done(DeferredWork moreWork);
        void done();
        void error();
    }
}
