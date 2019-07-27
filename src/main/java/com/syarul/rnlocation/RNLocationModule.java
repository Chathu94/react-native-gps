package com.syarul.rnlocation;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import androidx.annotation.RequiresApi;

public class RNLocationModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    // const
    public final static int REQUEST_CODE = -1010101;
    private static final String EVENT_LOCATION = "locationUpdated";
    private static final String EVENT_DISABLE = "locationDisable";
    private static final String EVENT_ENABLE = "locationEnable";
    private static final String EVENT_STATUSCHANGE = "locationStatus";

    // React Class Name as called from JS
    public static final String REACT_CLASS = "RNLocation";
    // Unique Name for Log TAG
    public static final String TAG = RNLocationModule.class.getSimpleName();
    // Save last Location Provided
    private Location mLastLocation;
    private LocationListener mLocationListener;
    private LocationManager locationManager;
    private Integer appType;

    //The React Native Context
    ReactApplicationContext mReactContext;


    // Constructor Method as called in Package
    public RNLocationModule(ReactApplicationContext reactContext) {
        super(reactContext);
        // Save Context for later use
        mReactContext = reactContext;
        NotificationReceiver.mReactContext = reactContext;
        UpdateTick.mReactContext = reactContext;
        locationManager = (LocationManager) mReactContext.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Get provider name.
     * @return Name of best suiting provider.
     * */
    String getProviderName() {
        LocationManager locationManager = (LocationManager) mReactContext.getSystemService(Context.LOCATION_SERVICE);

        Criteria criteria = new Criteria();
        criteria.setPowerRequirement(Criteria.POWER_LOW); // Chose your desired power consumption level.
        criteria.setAccuracy(Criteria.ACCURACY_FINE); // Choose your accuracy requirement.
        criteria.setSpeedRequired(true); // Chose if speed for first location fix is required.
        criteria.setAltitudeRequired(false); // Choose if you use altitude.
        criteria.setBearingRequired(false); // Choose if you use bearing.
        criteria.setCostAllowed(false); // Choose if this provider can waste money :-)

        // Provide your criteria and flag enabledOnly that tells
        // LocationManager only to return active providers.
        return locationManager.getBestProvider(criteria, true);
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) mReactContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void sendLocation(Location loc) {
        mLastLocation = loc;
        if (mLastLocation != null) {
            try {
                double longitude;
                double latitude;
                double speed;
                double altitude;
                double accuracy;
                double course;
                double time;

                // Receive Longitude / Latitude from (updated) Last Location
                longitude = mLastLocation.getLongitude();
                latitude = mLastLocation.getLatitude();
                speed = mLastLocation.getSpeed();
                altitude = mLastLocation.getAltitude();
                accuracy = mLastLocation.getAccuracy();
                course = mLastLocation.getBearing();
                time = mLastLocation.getTime();

//                Log.i(TAG, "Got new location. Lng: " +longitude+" Lat: "+latitude);

                // Create Map with Parameters to send to JS
                WritableMap params = Arguments.createMap();
                params.putDouble("longitude", longitude);
                params.putDouble("latitude", latitude);
                params.putDouble("speed", speed);
                params.putDouble("altitude", altitude);
                params.putDouble("accuracy", accuracy);
                params.putDouble("course", course);
                params.putDouble("time", time);

                // Send Event to JS to update Location
                if (accuracy < 50 && appType == 1) {
                    sendEvent(mReactContext, EVENT_LOCATION, params);
                } else if (appType == 2) {
                    sendEvent(mReactContext, EVENT_LOCATION, params);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.i(TAG, "Location services disconnected.");
            }
        }
    }


    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @ReactMethod
    public void bringToFront() {
        String packageName = this.mReactContext.getPackageName();
        Intent launchIntent = this.mReactContext.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();

        try {
            Class<?> activityClass = Class.forName(className);
            Intent activityIntent = new Intent(this.mReactContext, activityClass);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            this.mReactContext.startActivity(activityIntent);
        } catch (Exception e) {
            Log.e(TAG, "Class not found", e);
            return;
        }
    }

    /*
     * Location permission request (Not implemented yet)
     */
    @ReactMethod
    public void requestWhenInUseAuthorization() {
        Log.i(TAG, "Requesting authorization");
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void checkDrawOverlayPermission() {
        /** check if we already  have permission to draw over other apps */
        if (!Settings.canDrawOverlays(getReactApplicationContext())) {
            Activity activity = getReactApplicationContext().getCurrentActivity();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getReactApplicationContext().getPackageName()));
            activity.startActivityForResult(intent, REQUEST_CODE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            if (Settings.canDrawOverlays(getReactApplicationContext())) {
//                Toast.makeText(getReactApplicationContext(), "checkDrawOverlayPermission Pass!", Toast.LENGTH_SHORT).show();
            } else {
//                Toast.makeText(getReactApplicationContext(), "checkDrawOverlayPermission FAIL!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }


    @ReactMethod
    public void requestServicePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkDrawOverlayPermission();
        }
    }

    @ReactMethod
    public void startService(String jwtAccessToken, String url, String user_id, String pricing) {
        Intent myIntent = new Intent(mReactContext, LocationService.class);
        Bundle bundle = new Bundle();

        bundle.putString("url", url);
        bundle.putString("jwtAccessToken", jwtAccessToken);
        bundle.putString("user_id", user_id);
//        Log.i(TAG, "PRICING" + pricing);
        bundle.putString("pricing", pricing);
        myIntent.putExtras(bundle);
        myIntent.setAction(LocationService.ACTION_START);
        mReactContext.startService(myIntent);
        Log.i(TAG, "Starting service with " + jwtAccessToken + " and " + url);
    }

    @ReactMethod
    public void serviceStatus(Callback cb) {
        boolean status = isMyServiceRunning(LocationService.class);
        if (status) {
            Log.i(TAG, "Service is currently active");
        } else {
            Log.i(TAG, "Service is currently inactive");
        }
        cb.invoke(status);
        NotificationReceiver.cb = cb;
    }

    @ReactMethod
    public void stopService() {
        Log.i(TAG, "Stopping service");
        Intent myIntent = new Intent(mReactContext, LocationService.class);
        myIntent.setAction(LocationService.ACTION_OFFLINE);
        mReactContext.startService(myIntent);
//        mReactContext.startService(new Intent(mReactContext, LocationService.class));
    }

    /*
     * Location Callback as called by JS
     */
    @ReactMethod
    public void startUpdatingLocation(Integer appType) {
        this.appType = appType;
        if (ActivityCompat.checkSelfPermission(getReactApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getReactApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        sendLocation(mLastLocation);
        mLocationListener = new LocationListener() {
            @Override
            public void onStatusChanged(String str, int in, Bundle bd) {
                WritableMap out = Arguments.createMap();
                out.putInt("status", in);
                sendEvent(mReactContext, EVENT_STATUSCHANGE, out);
            }

            @Override
            public void onProviderEnabled(String str) {
                sendEvent(mReactContext, EVENT_ENABLE, null);
            }

            @Override
            public void onProviderDisabled(String str) {
                sendEvent(mReactContext, EVENT_DISABLE, null);
            }

            @Override
            public void onLocationChanged(Location loc) {
                sendLocation(loc);
            }
        };
        if (ActivityCompat.checkSelfPermission(getReactApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getReactApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(getProviderName(), 500, 0, mLocationListener);
    }

    @ReactMethod
    public void checkGooglePlayServices(Promise promise) {
        final int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mReactContext);
        if (status != ConnectionResult.SUCCESS) {
            Log.e(TAG, GooglePlayServicesUtil.getErrorString(status));

            // ask user to update google play services.
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, getCurrentActivity(), 1);
            dialog.show();
            promise.reject(new Throwable("Older version of play services"));
        } else {
            Log.i(TAG, GooglePlayServicesUtil.getErrorString(status));
            // google play services is updated.
            //your code goes here...
            promise.resolve(true);
        }
    }

    @ReactMethod
    public void stopUpdatingLocation() {
        try {
            locationManager.removeUpdates(mLocationListener);
            Log.i(TAG, "Location service disabled.");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Internal function for communicating with JS
     */
    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
      if (reactContext.hasActiveCatalystInstance()) {
        reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
          .emit(eventName, params);
      } else {
        Log.i(TAG, "Waiting for CatalystInstance...");
      }
    }
}
