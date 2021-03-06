package io.locative.app.view;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.google.firebase.iid.FirebaseInstanceId;

import java.util.UUID;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.OnClick;
import io.locative.app.LocativeApplication;
import io.locative.app.R;
import io.locative.app.model.Account;
import io.locative.app.model.EventType;
import io.locative.app.model.Fencelog;
import io.locative.app.model.Geofences;
import io.locative.app.network.RequestManager;
import io.locative.app.utils.Constants;
import io.locative.app.utils.Preferences;

public class SettingsActivity extends BaseActivity {

    private Constants.HttpMethod mHttpMethod = Constants.HttpMethod.POST;

    @BindView(R.id.global_http_url)
    EditText mUrlText;

    @BindView(R.id.global_http_method_button)
    Button mGlobalHttpMethodButton;

    @BindView(R.id.global_auth_switch)
    Switch mGlobalHttpAuthSwitch;

    @BindView(R.id.global_auth_username)
    EditText mGlobalHttpAuthUsername;

    @BindView(R.id.global_auth_password)
    EditText mGlobalHttpAuthPassword;

    @BindView(R.id.send_test_button)
    Button mSendTestButton;

    @BindView(R.id.notification_success_switch)
    Switch mNotificationSuccessSwitch;

    @BindView(R.id.notification_fail_switch)
    Switch mNotificationFailSwitch;

    @BindView(R.id.notification_only_latest_switch)
    Switch mNotificationOnlyLatestSwitch;

    @BindView(R.id.notification_sound_switch)
    Switch mNotificationSoundSwitch;

    @BindView(R.id.trigger_threshold_enabled_switch)
    Switch mTriggerThresholdEnabled;

    @BindView(R.id.trigger_threshold_seekbar)
    SeekBar mTriggerThresholdSeekBar;

    @BindView(R.id.trigger_threshold_notice)
    TextView mTriggerThresholdNotice;

    @Inject
    RequestManager mRequestManager;

    private void updateThresholdNotice() {
        if (mTriggerThresholdEnabled.isChecked()) {
            mTriggerThresholdNotice.setText(getString(R.string.trigger_threshold_notice, mTriggerThresholdSeekBar.getProgress()));
            return;
        }
        mTriggerThresholdNotice.setText(getString(R.string.trigger_threshold_notice_disabled));
    }

    private void setupThreshold() {
        mTriggerThresholdEnabled.setChecked(mPrefs.getBoolean(Preferences.TRIGGER_THRESHOLD_ENABLED, false));
        mTriggerThresholdSeekBar.setEnabled(mTriggerThresholdEnabled.isChecked());
        mTriggerThresholdSeekBar.setProgress(mPrefs.getInt(Preferences.TRIGGER_THRESHOLD_VALUE, Preferences.TRIGGER_THRESHOLD_VALUE_DEFAULT) / 1000);
        mTriggerThresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                updateThresholdNotice();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        mTriggerThresholdEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mTriggerThresholdSeekBar.setEnabled(b);
                updateThresholdNotice();
            }
        });
        updateThresholdNotice();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((LocativeApplication) getApplication()).getComponent().inject(this);

        mUrlText.setText(mPrefs.getString(Preferences.HTTP_URL, null));
        mGlobalHttpMethodButton.setText(
                mPrefs.getInt(Preferences.HTTP_METHOD, 0) == 0 ? "POST" : "GET"
        );
        mGlobalHttpAuthSwitch.setChecked(
                mPrefs.getBoolean(Preferences.HTTP_AUTH, false)
        );
        switchHttpAuth();

        mGlobalHttpAuthUsername.setText(mPrefs.getString(Preferences.HTTP_USERNAME, null));
        mGlobalHttpAuthPassword.setText(mPrefs.getString(Preferences.HTTP_PASSWORD, null));
        mNotificationSuccessSwitch.setChecked(mPrefs.getBoolean(Preferences.NOTIFICATION_SUCCESS, false));
        mNotificationFailSwitch.setChecked(mPrefs.getBoolean(Preferences.NOTIFICATION_FAIL, false));
        mNotificationOnlyLatestSwitch.setChecked(mPrefs.getBoolean(Preferences.NOTIFICATION_SHOW_ONLY_LATEST, false));
        mNotificationSoundSwitch.setChecked(mPrefs.getBoolean(Preferences.NOTIFICATION_SOUND, false));
        mHttpMethod = (Constants.HttpMethod.POST.ordinal() == mPrefs.getInt(Preferences.HTTP_METHOD, 0)) ? Constants.HttpMethod.POST : Constants.HttpMethod.GET;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                save(true);
                return true;
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupThreshold();
    }

    @Override
    public void onResume() {
        super.onResume();
    }


    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_settings;
    }

    @Override
    protected String getToolbarTitle() {
        return "Settings";
    }

    @Override
    protected int getMenuResourceId() {
        return R.menu.settings;
    }

    @OnClick(R.id.global_auth_switch)
    public void switchHttpAuth() {
        mGlobalHttpAuthUsername.setEnabled(mGlobalHttpAuthSwitch.isChecked());
        mGlobalHttpAuthPassword.setEnabled(mGlobalHttpAuthSwitch.isChecked());
    }

    @OnClick(R.id.send_test_button)
    public void sendTestRequest() {

        final String locationId = "test";

        if (mUrlText.getText().length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.note)
                    .setMessage(R.string.please_login_to_test_request)
                    .setNeutralButton(R.string.ok, null)
                    .show();
            return;
        }

        Geofences.Geofence geofence = new Geofences.Geofence(
                UUID.randomUUID().toString(), // ID
                locationId, // Custom ID
                "Test Location", // Name
                0, // Triggers
                0.00f, // Lat
                0.00f, // Lng
                50, // Radius
                0, // Auth
                null, // Auth User
                null, // Auth Passwd
                0, // Enter Method
                null, // Enter Url
                0, // Exit Method
                null, // Exit Url
                0 // Currently Entered
        );

        mRequestManager.dispatch(geofence, EventType.ENTER);

        new AlertDialog.Builder(this)
                .setTitle(R.string.note)
                .setMessage(R.string.test_request_sent_message)
                .setNeutralButton(R.string.ok, null)
                .show();
    }

    @OnClick(R.id.global_http_method_button)
    public void selectMethodForTriggerType() {

        new AlertDialog.Builder(this)
                .setMessage("Choose HTTP Method")
                .setPositiveButton("POST", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGlobalHttpMethodButton.setText("POST");
                        mHttpMethod = Constants.HttpMethod.POST;
                    }
                })
                .setNeutralButton("GET", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGlobalHttpMethodButton.setText("GET");
                        mHttpMethod = Constants.HttpMethod.GET;
                    }
                })
                .show();
    }

    private void save(boolean finish) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(Preferences.HTTP_URL, mUrlText.getText().toString());
        editor.putInt(Preferences.HTTP_METHOD, mHttpMethod.ordinal());
        editor.putBoolean(Preferences.HTTP_AUTH, mGlobalHttpAuthSwitch.isChecked());
        editor.putString(Preferences.HTTP_USERNAME, mGlobalHttpAuthUsername.getText().toString());
        editor.putString(Preferences.HTTP_PASSWORD, mGlobalHttpAuthPassword.getText().toString());
        editor.putBoolean(Preferences.NOTIFICATION_SUCCESS, mNotificationSuccessSwitch.isChecked());
        editor.putBoolean(Preferences.NOTIFICATION_FAIL, mNotificationFailSwitch.isChecked());
        editor.putBoolean(Preferences.NOTIFICATION_SHOW_ONLY_LATEST, mNotificationOnlyLatestSwitch.isChecked());
        editor.putBoolean(Preferences.NOTIFICATION_SOUND, mNotificationSoundSwitch.isChecked());
        editor.putBoolean(Preferences.TRIGGER_THRESHOLD_ENABLED, mTriggerThresholdEnabled.isChecked());
        editor.putInt(Preferences.TRIGGER_THRESHOLD_VALUE, mTriggerThresholdSeekBar.getProgress() * 1000);
        editor.apply();

        if (finish) {
            finish();
        }
    }
}
