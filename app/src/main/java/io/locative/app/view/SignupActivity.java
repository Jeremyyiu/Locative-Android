package io.locative.app.view;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.Button;
import android.widget.EditText;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.OnClick;
import io.locative.app.LocativeApplication;
import io.locative.app.R;
import io.locative.app.model.Fencelog;
import io.locative.app.model.Geofences;
import io.locative.app.network.LocativeApiWrapper;
import io.locative.app.network.LocativeNetworkingCallback;
import io.locative.app.utils.Constants;

public class SignupActivity extends BaseActivity implements LocativeNetworkingCallback {


    @BindView(R.id.username_text)
    EditText mUsernameText;

    @BindView(R.id.email_text)
    EditText mEmailText;

    @BindView(R.id.password_text)
    EditText mPasswordText;

    @BindView(R.id.signup_button)
    Button mSignupButton;

    @BindView(R.id.tos_button)
    Button mTosButtonl;

    @Inject
    LocativeApiWrapper mLocativeNetworkingWrapper;

    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((LocativeApplication) getApplication()).inject(this);
    }

    @OnClick(R.id.signup_button)
    public void signup() {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle(R.string.loading);
        mProgressDialog.setMessage("Creating Account…");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();

        mLocativeNetworkingWrapper.doSignup(mUsernameText.getText().toString(), mPasswordText.getText().toString(), mEmailText.getText().toString(), this);
    }

    @OnClick(R.id.tos_button)
    public void tosClick() {
        Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(Constants.TOS_URI));
        startActivity(intent);
    }

    private void simpleAlert(String msg) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setNeutralButton("OK", null)
                .show();
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_signup;
    }

    @Override
    protected String getToolbarTitle() {
        return "Sign Up";
    }

    @Override
    protected int getMenuResourceId() {
        return 0;
    }

    @Override
    public void onLoginFinished(boolean success, String sessionId) {

    }

    @Override
    public void onSignupFinished(boolean success, boolean userAlreadyExisting) {
        mProgressDialog.dismiss();
        if (!success) {
            if (userAlreadyExisting) {
                simpleAlert("Error when creating Account. Username or E-Mail is already existing.");
                return;
            }
            simpleAlert("Error when creating Account. Please try again.");
            return;
        }
        new AlertDialog.Builder(SignupActivity.this)
                .setMessage("Your Account has been created. You may now sign in with your Username & Password")
                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SignupActivity.this.finish();
                    }
                })
                .show();
    }

    @Override
    public void onCheckSessionFinished(boolean sessionValid) {

    }

    @Override
    public void onDispatchFencelogFinished(boolean success) {

    }

    @Override
    public void onGetGeoFencesFinished(List<Geofences.Geofence> fences) {

    }

    @Override
    public void onGetFencelogsFinished(List<Fencelog> fencelogs) {

    }

    @Override
    public void onSyncReceived(List<Geofences.Geofence> geofences, List<String> deleted) {

    }

    @Override
    public void onStoredGeofence() {

    }
}
