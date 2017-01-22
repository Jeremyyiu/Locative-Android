package io.locative.app.view;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.widget.NestedScrollView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.schuetz.mapareas.MapAreaManager;
import com.schuetz.mapareas.MapAreaManager.CircleManagerListener;
import com.schuetz.mapareas.MapAreaMeasure;
import com.schuetz.mapareas.MapAreaWrapper;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import butterknife.BindView;
import butterknife.OnClick;
import info.hoang8f.android.segmented.SegmentedGroup;
import io.locative.app.LocativeApplication;
import io.locative.app.R;
import io.locative.app.beacon.BeaconItem;
import io.locative.app.geo.LocativeGeocoder;
import io.locative.app.geo.LocativeLocationManager;
import io.locative.app.map.WorkaroundMapFragment;
import io.locative.app.model.Geofences;
import io.locative.app.persistent.EditableItem;
import io.locative.app.persistent.GeofenceProvider;
import io.locative.app.utils.Constants;
import io.locative.app.utils.GeocodeHandler;
import io.locative.app.utils.UrlValidator;

public class AddEditGeofenceActivity extends BaseActivity implements OnMapReadyCallback {

    public enum Type implements Serializable {
        GEOFENCE,
        BEACON
    }

    public static final String EDITABLE_ITEM = "editable_item";

    public static final int DEFAULT_RADIUS_METERS = 50;
    public static final int MAX_RADIUS_METERS = 500;

    @BindView(R.id.address_button)
    Button mLocationButton;

    @BindView(R.id.customLocationId)
    EditText mCustomId;

    @BindView(R.id.trigger_enter)
    Switch mTriggerEnter;

    @BindView(R.id.trigger_exit)
    Switch mTriggerExit;

    @BindView(R.id.enter_method_button)
    Button mEnterMethodButton;

    @BindView(R.id.enter_url_text)
    EditText mEnterUrl;

    @BindView(R.id.exit_method_button)
    Button mExitMethodButton;

    @BindView(R.id.exit_url_text)
    EditText mExitUrl;

    @BindView(R.id.radius_slider)
    SeekBar mRadiusSlider;

    @BindView(R.id.radius_label)
    TextView mRadiusLabel;

    @BindView(R.id.basic_auth_switch)
    Switch mBasicAuthSwitch;

    @BindView(R.id.basic_auth_username)
    EditText mBasicAuthUsername;

    @BindView(R.id.basic_auth_password)
    EditText mBasicAuthPassword;

    @BindView(R.id.scrollView)
    NestedScrollView mScrollView;

    @BindView(R.id.button_toggle_group)
    SegmentedGroup mButtonToggleGroup;

    @BindView(R.id.button_toggle_geofence)
    RadioButton mButtonToggleGeofence;

    @BindView(R.id.button_toggle_beacon)
    RadioButton mButtonToggleBeacon;

    @BindView(R.id.geofence_settings)
    LinearLayout mGeofenceSettings;

    public EditableItem mEditableItem;

    private LocativeLocationManager mLocativeLocationManager = null;
    private MapAreaManager mCircleManager = null;
    private MapAreaWrapper mCircle = null;
    public ProgressDialog mProgressDialog = null;

    private GeocodeHandler mGeocoderHandler = null;
    private boolean mAddressIsDirty = true;
    private boolean mGeocodeAndSave = false;
    private boolean mSaved = false;
    private Constants.HttpMethod mEnterMethod = Constants.HttpMethod.POST;
    private Constants.HttpMethod mExitMethod = Constants.HttpMethod.POST;
    private GoogleMap mMap = null;

    private boolean isEditingGeofence() {
        return mEditableItem != null && mEditableItem instanceof Geofences.Geofence;
    }

    private boolean isEditingBeacon() {
        return mEditableItem != null && mEditableItem instanceof BeaconItem;
    }

    private boolean isNewGeofence() {
        return !isEditingGeofence() && mButtonToggleGeofence.isChecked();
    }

    private boolean isNewBeacon() {
        return !isEditingBeacon() && mButtonToggleBeacon.isChecked();
    }

    LocativeLocationManager.LocationResult locationResult = new LocativeLocationManager.LocationResult() {
        @Override
        public void gotLocation(Location location) {
            //LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            if (mMap != null) {
                zoomToLocation(location);
            }
            setCircleToLocation(location);
            doReverseGeocoding(location);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((LocativeApplication) getApplication()).getComponent().inject(this);

        // Set editable item in case we're editing a Geofence / Beacon
        mEditableItem = (EditableItem) getIntent().getSerializableExtra(EDITABLE_ITEM);
        Log.d(Constants.LOG, "mEditableItem: " + mEditableItem);

        // Toggle button actions
        mButtonToggleGeofence.setChecked(true);
        mButtonToggleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (radioGroup.getCheckedRadioButtonId() == mButtonToggleGeofence.getId()) {
                    Log.d(Constants.LOG, "Geofence editing selected");
                    mGeofenceSettings.setVisibility(View.VISIBLE);
                   getSupportFragmentManager().beginTransaction().show(
                           getSupportFragmentManager().findFragmentById(R.id.map)
                   ).commit();
                } else if (radioGroup.getCheckedRadioButtonId() == mButtonToggleBeacon.getId()) {
                    Log.d(Constants.LOG, "Beacon editing selected");
                    mGeofenceSettings.setVisibility(View.GONE);
                    getSupportFragmentManager().beginTransaction().hide(
                            getSupportFragmentManager().findFragmentById(R.id.map)
                    ).commit();
                }
            }
        });

        if (mEditableItem != null) {
            mButtonToggleGroup.setVisibility(View.GONE);
        } else {
            mButtonToggleGroup.setVisibility(View.VISIBLE);
        }

        mRadiusSlider.setMax(MAX_RADIUS_METERS);
        mRadiusSlider.setProgress(DEFAULT_RADIUS_METERS);
        mRadiusSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int radiusMeters, boolean fromUser) {
                if (mCircle != null) {
                    updateRadius();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        WorkaroundMapFragment mapFragment = (WorkaroundMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mapFragment.setListener(new WorkaroundMapFragment.OnTouchListener() {
            @Override
            public void onTouch() {
                mScrollView.requestDisallowInterceptTouchEvent(true);
            }
        });

        mGeocoderHandler = new GeocodeHandler(this);
        updateRadius();
    }


    @SuppressWarnings("unsed")
    @OnClick({R.id.address_button, R.id.enter_method_button, R.id.exit_method_button})
    public void onButtonClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.address_button:
                final EditText addressTextField = new EditText(view.getContext());
                new AlertDialog.Builder(view.getContext())
                        .setMessage("Enter Address manually:")
                        .setView(addressTextField)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                doGeocodingAndPositionCircle(addressTextField.getText().toString());
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        })
                        .show();
                break;
            case R.id.enter_method_button:
                selectMethodForTriggerType(Constants.TriggerType.ARRIVAL);
                break;
            case R.id.exit_method_button:
                selectMethodForTriggerType(Constants.TriggerType.DEPARTURE);
                break;
        }


    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setAllGesturesEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // TODO request permission if not given
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            }
        } else {
            // we have permission since requested on app install
            mMap.setMyLocationEnabled(true);
        }
        mMap.getUiSettings().setZoomControlsEnabled(false);

        Cursor cursor = null;
        if (isEditingGeofence()) {
            ContentResolver resolver = this.getContentResolver();
            cursor = resolver.query(Uri.parse("content://" + getString(R.string.authority) + "/geofences"), null, "custom_id = ?", new String[]{String.valueOf(mEditableItem.getDatabaseId())}, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                mLocationButton.setText(cursor.getString(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_NAME)));
                mRadiusSlider.setProgress(cursor.getInt(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_RADIUS)));
                mCustomId.setText(cursor.getString(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_CUSTOMID)));

                // Triggers
                int triggers = cursor.getInt(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_TRIGGER));
                mTriggerEnter.setChecked(((triggers & GeofenceProvider.TRIGGER_ON_ENTER) == GeofenceProvider.TRIGGER_ON_ENTER));
                mTriggerExit.setChecked(((triggers & GeofenceProvider.TRIGGER_ON_EXIT) == GeofenceProvider.TRIGGER_ON_EXIT));

                int enterMethod = cursor.getInt(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_ENTER_METHOD));
                mEnterMethodButton.setText(enterMethod == 0 ? "POST" : "GET");
                mEnterMethod = (Constants.HttpMethod.POST.ordinal() == enterMethod) ? Constants.HttpMethod.POST : Constants.HttpMethod.GET;
                mEnterUrl.setText(cursor.getString(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_ENTER_URL)));

                int exitMethod = cursor.getInt(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_EXIT_METHOD));
                mExitMethodButton.setText(exitMethod == 0 ? "POST" : "GET");
                mExitMethod = (Constants.HttpMethod.POST.ordinal() == enterMethod) ? Constants.HttpMethod.POST : Constants.HttpMethod.GET;
                mExitUrl.setText(cursor.getString(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_EXIT_URL)));

                mBasicAuthSwitch.setChecked(cursor.getInt(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_HTTP_AUTH)) != 0);
                mBasicAuthUsername.setText(cursor.getString(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_HTTP_USERNAME)));
                mBasicAuthPassword.setText(cursor.getString(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_HTTP_PASSWORD)));
            }
        } else if (isEditingBeacon()) {
            // todo: implement editing beacon
        }


        mLocativeLocationManager = new LocativeLocationManager();
        if (!isEditingGeofence()) {
            mLocativeLocationManager.getLocation(this, locationResult);
        }
        Location location;
        if (mMap.isMyLocationEnabled() && mMap.getMyLocation() != null && !isEditingGeofence()) {
            location = mMap.getMyLocation();
            mMap.getMyLocation();
        } else if (cursor != null) {
            location = new Location("location");
            location.setLatitude(cursor.getDouble(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_LATITUDE)));
            location.setLongitude(cursor.getDouble(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_LONGITUDE)));
        } else {
            // New York
            location = new Location("custom");
            location.setLatitude(40.7127);
            location.setLongitude(74.0059);
        }

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 16));

        setupCircleManager();
        if (isEditingGeofence()) {
            mButtonToggleGroup.setVisibility(View.GONE);
            setCircleToLocation(location);
            int radiusMeters = cursor.getInt(cursor.getColumnIndex(GeofenceProvider.Geofence.KEY_RADIUS));
            mRadiusSlider.setProgress(radiusMeters);
        }

        updateRadius();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case LocativeLocationManager.MY_PERMISSIONS_REQUEST:
                mLocativeLocationManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_add_edit_geofence;
    }

    @Override
    protected String getToolbarTitle() {
        // need to replace by add or edit
        return "Add Geofence";
    }

    @Override
    protected int getMenuResourceId() {
        return R.menu.add_edit_geofence;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_save) {

            // Bail out ealy if webhook urls are borked
            if (!validateUrls()) {
                return false;
            }

            // Save Geofence / Add new one
            if (!mAddressIsDirty) {
                this.save(true);
                return true;
            }

            if (mCircle == null) {
                return false;
            }

            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setTitle(R.string.loading);
            mProgressDialog.setMessage(getResources().getString(R.string.geocoding_progress_message));
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.show();

            mGeocodeAndSave = true;
            doReverseGeocodingOfCircleLocation();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean validateUrls() {
        // Check for valid 'Enter' URL, or bail out.
        if (mEnterUrl.getText().length() > 0 &&
                !new UrlValidator(mEnterUrl.getText().toString()).validate()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.save_error_title)
                    .setMessage(R.string.save_error_invalid_enter_url)
                    .setNeutralButton(R.string.ok, null)
                    .show();
            return false;
        }

        // Check for valid 'Exit' URL, or bail out.
        if (mExitUrl.getText().length() > 0 &&
                !new UrlValidator(mExitUrl.getText().toString()).validate()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.save_error_title)
                    .setMessage(R.string.save_error_invalid_exit_url)
                    .setNeutralButton(R.string.ok, null)
                    .show();
            return false;
        }
        return true;
    }

    public void save(boolean finish) {

        if (!validateUrls()) {
            return;
        }

        Log.i(Constants.LOG, "Saved #1: " + mSaved);

        if (mSaved) {
            return;
        }

        mSaved = true;
        ContentResolver resolver = this.getContentResolver();
        ContentValues values = new ContentValues();

        String custom_id = mCustomId.getText().toString();
        if (isEditingGeofence()) {
            Cursor existingCursor = resolver.query(Uri.parse("content://" + getString(R.string.authority) + "/geofences"), null, "custom_id = ?", new String[]{String.valueOf(mEditableItem.getDatabaseId())}, null);
            if (existingCursor != null && existingCursor.getCount() > 0) {
                existingCursor.moveToFirst();
                if (custom_id.length() == 0) {
                    custom_id = existingCursor.getString(existingCursor.getColumnIndex(GeofenceProvider.Geofence.KEY_CUSTOMID));
                }
            }
        } else if (isEditingBeacon()) {
            // todo: implement beacon editing save
        }

        if (custom_id.length() == 0 && !isEditingGeofence()) {
            custom_id = new UUID(new Random().nextLong(), new Random().nextLong()).toString();
        }

        int triggers = 0;
        if (mTriggerEnter.isChecked()) {
            triggers |= GeofenceProvider.TRIGGER_ON_ENTER;
        }
        if (mTriggerExit.isChecked()) {
            triggers |= GeofenceProvider.TRIGGER_ON_EXIT;
        }

        Log.d(Constants.LOG, "Triggers: " + triggers);

        values.put(GeofenceProvider.Geofence.KEY_NAME, mLocationButton.getText().toString());
        values.put(GeofenceProvider.Geofence.KEY_RADIUS, mCircle.getRadius()); // in meters
        values.put(GeofenceProvider.Geofence.KEY_CUSTOMID, custom_id);
        values.put(GeofenceProvider.Geofence.KEY_ENTER_METHOD, this.methodForTriggerType(Constants.TriggerType.ARRIVAL).ordinal());
        values.put(GeofenceProvider.Geofence.KEY_ENTER_URL, mEnterUrl.getText().toString());
        values.put(GeofenceProvider.Geofence.KEY_TRIGGER, triggers);
        values.put(GeofenceProvider.Geofence.KEY_EXIT_METHOD, this.methodForTriggerType(Constants.TriggerType.DEPARTURE).ordinal());
        values.put(GeofenceProvider.Geofence.KEY_EXIT_URL, mExitUrl.getText().toString());
        values.put(GeofenceProvider.Geofence.KEY_HTTP_AUTH, mBasicAuthSwitch.isChecked() ? 1 : 0);
        values.put(GeofenceProvider.Geofence.KEY_HTTP_USERNAME, mBasicAuthUsername.getText().toString());
        values.put(GeofenceProvider.Geofence.KEY_HTTP_PASSWORD, mBasicAuthPassword.getText().toString());
        values.put(GeofenceProvider.Geofence.KEY_LATITUDE, mCircle.getCenter().latitude);
        values.put(GeofenceProvider.Geofence.KEY_LONGITUDE, mCircle.getCenter().longitude);

        if (isEditingGeofence()) {
            resolver.update(Uri.parse("content://" + getString(R.string.authority) + "/geofences"), values, "custom_id = ?", new String[]{String.valueOf(mEditableItem.getDatabaseId())});
        } else {
            resolver.insert(Uri.parse("content://" + getString(R.string.authority) + "/geofences"), values);
        }

        if (finish) {
            this.finish();
            Log.i(Constants.LOG, "Finished!");
        }

        mSaved = false;
        Log.i(Constants.LOG, "Saved #2: " + (mSaved ? "true" : "false"));

    }

    // Zoom to Location
    private void zoomToLocation(Location location) {
        LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 16));
    }

    // Setup Circle
    private void setCircleToLocation(Location location) {
        // Set Circle
        if (mMap != null && mCircleManager != null && mCircle == null) {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            mCircle = new MapAreaWrapper(mMap, position, 100, 5.0f, 0xffff0000, 0x33ff0000, 1, 1000);
            mCircleManager.add(mCircle);
            mRadiusSlider.setProgress(DEFAULT_RADIUS_METERS);
        }
    }

    // Radius Circle
    private void setupCircleManager() {
        mCircleManager = new MapAreaManager(mMap,

                4, Color.RED, Color.HSVToColor(70, new float[]{1, 1, 200}), //styling

                -1,//custom drawables for move and resize icons

                0.5f, 0.5f, //sets anchor point of move / resize drawable in the middle

                new MapAreaMeasure(100, MapAreaMeasure.Unit.pixels), //circles will start with 100 pixels (independent of zoom level)

                new CircleManagerListener() { //listener for all circle events

                    @Override
                    public void onCreateCircle(MapAreaWrapper draggableCircle) {

                    }

                    @Override
                    public void onMoveCircleEnd(MapAreaWrapper draggableCircle) {
                        Log.d(Constants.LOG, "onMoveCircleEnd");
                        doReverseGeocodingOfCircleLocation();
                    }

                    @Override
                    public void onMoveCircleStart(MapAreaWrapper draggableCircle) {
                        mAddressIsDirty = true;
                    }

                });
    }

    // Reverse Geocoder
    private void doReverseGeocodingOfCircleLocation() {
        if (mCircle != null) {
            Location location = new Location("Geofence");
            location.setLongitude(mCircle.getCenter().longitude);
            location.setLatitude(mCircle.getCenter().latitude);
            doReverseGeocoding(location);
        }
    }

    private void doGeocodingAndPositionCircle(String addr) {
        if (mCircle != null) {
            Address address = new LocativeGeocoder().getLatLongFromAddress(addr, this);
            if (address != null) {
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                updateAddressField(address);

                mCircle.setCenter(latLng);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
            } else {
                new AlertDialog.Builder(this)
                        .setMessage("No location found. Please refine your query.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .show();
            }
        }
    }

    private void updateAddressField(Address address) {
        // Format the first line of address (if available), city, and country name.
        String addressText = String.format("%s, %s, %s",
                address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "",
                address.getLocality(),
                address.getCountryName());
        // Update address field on UI.
        Message.obtain(mGeocoderHandler, GeocodeHandler.UPDATE_ADDRESS, addressText).sendToTarget();
    }

    private void doReverseGeocoding(Location location) {
        // Since the geocoding API is synchronous and may take a while.  You don't want to lock
        // up the UI thread.  Invoking reverse geocoding in an AsyncTask.
        Log.d(Constants.LOG, "doReverseGeocoding for location: " + location);
        (new ReverseGeocodingTask(this)).execute(location);
    }

    private class ReverseGeocodingTask extends AsyncTask<Location, Void, Void> {
        Context mContext;

        public ReverseGeocodingTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Void doInBackground(Location... params) {
            Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());

            Location loc = params[0];
            List<Address> addresses = null;
            try {
                addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
            } catch (IOException e) {
                e.printStackTrace();
                // Update address field with the exception.
//                Message.obtain(mGeocoderHandler, UPDATE_ADDRESS, e.toString()).sendToTarget();
                Log.e(Constants.LOG, "Error when Reverse-Geocoding: " + e.toString());
            }
            if (addresses != null && addresses.size() > 0) {
                updateAddressField(addresses.get(0));
            }
            mAddressIsDirty = false;
            if (mGeocodeAndSave) {
                mGeocodeAndSave = false;
                Message.obtain(mGeocoderHandler, GeocodeHandler.SAVE_AND_FINISH, null).sendToTarget();
            }

            return null;
        }
    }

    private void selectMethodForTriggerType(final Constants.TriggerType t) {

        new AlertDialog.Builder(this)
                .setMessage("Choose HTTP Method")
                .setPositiveButton("POST", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (t == Constants.TriggerType.ARRIVAL) {
                            mEnterMethodButton.setText("POST");
                            mEnterMethod = Constants.HttpMethod.POST;
                        } else {
                            mExitMethodButton.setText("POST");
                            mExitMethod = Constants.HttpMethod.POST;
                        }
                        dialog.dismiss();
                    }
                })
                .setNeutralButton("GET", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (t == Constants.TriggerType.ARRIVAL) {
                            mEnterMethodButton.setText("GET");
                            mEnterMethod = Constants.HttpMethod.GET;
                        } else {
                            mExitMethodButton.setText("GET");
                            mExitMethod = Constants.HttpMethod.GET;
                        }
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private Constants.HttpMethod methodForTriggerType(Constants.TriggerType t) {
        if (t == Constants.TriggerType.ARRIVAL) {
            return mEnterMethod;
        }
        return mExitMethod;
    }


    private void updateRadius() {
        int radiusMeters = mRadiusSlider.getProgress();
        mRadiusLabel.setText(String.format(getResources().getConfiguration().locale, "%d m", radiusMeters));
        if (mCircle != null) {
            mCircle.setRadius(radiusMeters);
        }
    }

}
