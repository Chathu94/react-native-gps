package com.syarul.rnlocation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class NotificationReceiver extends BroadcastReceiver {
    public static Callback cb;
    public static ReactApplicationContext mReactContext;

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == LocationService.ACTION_OFFLINE) {
            context.stopService(new Intent(context, LocationService.class));
            if (cb != null) {
                try {
                    WritableMap out = Arguments.createMap();
                    out.putBoolean("status", false);
                    sendEvent(mReactContext, "UPDATE_SERVICE_STATE", out);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (intent.getAction() == LocationService.ACTION_OPEN) {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(context.getPackageName());
            context.startActivity(launchIntent);
        }
    }
}