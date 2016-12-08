package com.ryanpotsander.chisauapp;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.OnDisconnect;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity //TODO: remove database entry when user exits app
        implements
        View.OnClickListener,
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ActivityCompat.OnRequestPermissionsResultCallback,
        LocationListener,
        ResultCallback<Status> {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 0;

    private static final int REQUEST_CHECK_SETTINGS = 1;
    private static final int REQUEST_SIGN_IN = 2;

    private static final float PROXIMITY = 1609.34f; // 1 mile in meters

    private GoogleMap mMap;

    private GoogleApiClient mGoogleApiClient;

    private GoogleSignInAccount mAccount;

    private SignInButton signInButton;

    private LocationRequest mLocationRequest;

    private Location mLocation;

    private DatabaseReference mDatabase;
    private FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authStateListener;

    private DatabaseReference mLocationRef;

    private HashMap<String, Marker> mMarkers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("Chi Sau Finder");
        setSupportActionBar(toolbar);


        mMarkers = new HashMap<>();

        //Auth
        GoogleSignInOptions options =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build();

        mGoogleApiClient =
                new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        //.enableAutoManage(this, this)
                        .addApi(LocationServices.API)
                        .addApi(Auth.GOOGLE_SIGN_IN_API, options)
                        .build();

        auth = FirebaseAuth.getInstance();
        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();

                if (user != null) {
                    //mInbox = "client-" + Integer.toString(Math.abs(user.getUid().hashCode()));
                    Log.d(TAG, "Signed in as " + user.getDisplayName());

                } else {
                    Log.d(TAG, "Signed out");
                }
            }
        };

        //init buttons
        initControls();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                if (resultCode == RESULT_OK) startLocationUpdates(); //try getting location again
                break;
            case REQUEST_SIGN_IN:
                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);

                if (result.isSuccess()) {
                    mAccount = result.getSignInAccount();
                    AuthCredential credential = GoogleAuthProvider.getCredential(mAccount.getIdToken(), null);
                    auth.signInWithCredential(credential)
                            .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                                @Override
                                public void onComplete(@NonNull Task<AuthResult> task) {
                                    if (task.isSuccessful()) {
                                        startLocationUpdates();
                                    } else {
                                        Log.d(TAG, "Firebase login failed");
                                    }
                                }
                            });

                    signInButton.setVisibility(View.GONE);
                } else {
                    Log.d(TAG, "Google sign in failed");
                }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) return;

        //permission granted
        if (permissions.length == 1 && permissions[0] == Manifest.permission.ACCESS_FINE_LOCATION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    private void checkLocationSettings() {

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {

                Status status = locationSettingsResult.getStatus();
                //LocationSettingsStates states = locationSettingsResult.getLocationSettingsStates();

                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        //no action needed
                        break;

                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:

                        try {
                            status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }

                        break;

                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:

                        Log.d(TAG, "Settings change unavailable");

                        break;
                }

            }
        });
    }

    private void startLocationUpdates() {

        //TODO: consider conditional to avoid running twice
        checkLocationSettings();

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            //request runtime permission
            String[] permissions = new String[1];
            permissions[0] = Manifest.permission.ACCESS_FINE_LOCATION;
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            //permission granted
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, MainActivity.this);
        }
    }



    @Override
    public void onLocationChanged(Location location) {

        writeToFirebase(location);

        mLocation = location;

        if (mMap == null) getMapFragment().getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;
        LatLng latLng = new LatLng(mLocation.getLatitude(), mLocation.getLongitude());
        mMap.addMarker(new MarkerOptions().position(latLng).title("My Location"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10));

    }

    @Override
    protected void onStart() {
        super.onStart();

        mGoogleApiClient.connect();
        auth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();

        mLocationRef.removeValue();

        mGoogleApiClient.disconnect();
        if (authStateListener != null) auth.removeAuthStateListener(authStateListener);
    }

    private void initControls() {

        signInButton = (SignInButton) findViewById(R.id.sign_in_btn);
        signInButton.setOnClickListener(this);


    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.sign_in_btn:
                Intent signIn = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
                startActivityForResult(signIn, REQUEST_SIGN_IN);
                break;
        }
    }

    private MapFragment getMapFragment() {

        MapFragment fragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map_container);

        if (fragment == null) {

            fragment = MapFragment.newInstance();
            android.app.FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.add(R.id.map_container, fragment);
            transaction.commit();
        }

        return fragment;
    }

    private void writeToFirebase(Location location) {

        //if database not initialized
        if (mDatabase == null) initFirebase();

        //if no existing data
        if (mLocationRef == null) mLocationRef = mDatabase.push();

        mLocationRef.setValue(new DatabaseItem(mLocationRef.getKey(), location.getLatitude(), location.getLongitude()));

    }

    private void initFirebase() {
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mDatabase.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                //do nothing if triggered by own location
                if (dataSnapshot.getKey().equals(mLocationRef.getKey())) return;

                DatabaseItem item = dataSnapshot.getValue(DatabaseItem.class);
                String key = dataSnapshot.getKey();
                mMarkers.put(key, mMap.addMarker(new MarkerOptions().position(new LatLng(item.lat, item.lng)).title(key)));

            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                DatabaseItem item = dataSnapshot.getValue(DatabaseItem.class);


                Marker marker = mMarkers.get(dataSnapshot.getKey());
                if (marker != null) marker.setPosition(new LatLng(item.lat, item.lng)); //TODO: temp fix for null pointer
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                String key = dataSnapshot.getKey();

                //remove from map
                mMarkers.get(key).remove();

                //remove from hashmap
                mMarkers.remove(key);
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onResult(@NonNull Status status) {

    }
}
