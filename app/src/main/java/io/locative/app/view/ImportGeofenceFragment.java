package io.locative.app.view;

import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ListView;

import java.util.*;

import io.locative.app.R;
import io.locative.app.model.Geofences;
import io.locative.app.network.LocativeApiWrapper;
import io.locative.app.network.LocativeNetworkingAdapter;

public class ImportGeofenceFragment extends ListFragment {

    private static final String IMAGE_KEY = "image";
    private static final String TITLE_KEY = "title";
    private static final String SUBTITLE_KEY = "subtitle";

    private static final String IMAGE_VAL_DEFAULT = "0";

    // Keys used in Hashmap
    private static final String[] FROM = {
            IMAGE_KEY,
            TITLE_KEY,
            SUBTITLE_KEY
    };

    // Ids of views in listview_layout
    private static final int[] TO = {
            R.id.image,
            R.id.title,
            R.id.subtitle
    };
    private List<Geofences.Geofence> mFences;
    private OnGeofenceSelection mListener;

    public void refresh() {
        List<HashMap<String, String>> aList = new ArrayList<>();
        for (Geofences.Geofence geofence : mFences) {
            HashMap<String, String> hm = new HashMap<>();
            hm.put(IMAGE_KEY, IMAGE_VAL_DEFAULT);
            hm.put(TITLE_KEY, geofence.subtitle);
            hm.put(SUBTITLE_KEY, geofence.longitude + ", " + geofence.latitude + (!geofence.title.equals(LocativeApiWrapper.UNNAMED_FENCE) ? (" - " + geofence.title): ""));
            aList.add(hm);
        }
        if (getActivity() != null && getActivity().getBaseContext() != null) {
            GeofencesAdapter adapter = new GeofencesAdapter(getActivity().getBaseContext(), aList, R.layout.geofence_row, FROM, TO);
            setListAdapter(adapter);
        }
    }

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnGeofenceSelection) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass().getSimpleName() + " must implement OnGeofenceSelection");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        GeofencesActivity ga = (GeofencesActivity) getActivity();

        ga.mLocativeNetworkingWrapper.getGeofences(ga.mSessionManager.getSessionId(), new LocativeNetworkingAdapter() {

            @Override
            public void onGetGeoFencesFinished(List<Geofences.Geofence> fences) {
                mFences = fences;
                refresh();
            }
        });
    }

    public interface OnGeofenceSelection {
        void onFragmentInteraction(Geofences.Geofence fence);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (mListener != null) {
            Geofences.Geofence item = mFences.get(position);
            mListener.onFragmentInteraction(item);
        }
    }
}
