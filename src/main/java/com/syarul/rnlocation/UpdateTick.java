package com.syarul.rnlocation;

import android.location.Location;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.TimerTask;

class UpdateTick extends TimerTask {
    public static ReactApplicationContext mReactContext;
    public static final String TAG = UpdateTick.class.getSimpleName();
    private Location lastLocation;

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);
        }
    }

    /*
    calcCost({ wait, kms, pricing }) {
    const cost = pricing;
    let total = 0;
    for (let i = 0; i < cost.running.length; i++) {
      const start = i === cost.running.length-1 ? 0 : cost.running[cost.running.length-i-2].range_end;
      const end = cost.running[cost.running.length-i-1].range_end;
      if (kms >= start) {
        if (cost.running[cost.running.length-i-1].method === 'oneTime') {
          total += parseInt(cost.running[cost.running.length-i-1].amount, 10);
        } else if (cost.running[cost.running.length-i-1].method === 'perKm') {
          const per_end = end < 0 ? kms : kms > end ? end : kms;
          total += parseInt(cost.running[cost.running.length-i-1].amount, 10) * (per_end - start);
        }
      }
    }
    total += (cost.waiting * wait);
    return total;
  }
    * */
    private double calcCost(float wait, double kms) throws JSONException {
        double total = 0;
        try {
            JSONArray running = LocationService.pricing.getJSONArray("running");
            for (int i = 0; i < running.length(); i++) {
                Double start = 0.0;
                double per_end;
                if (i != running.length() - 1) {
                    JSONObject s = (JSONObject) running.get(running.length() - i - 2);
                    start = s.getDouble("range_end");
                }
                if (kms < start) continue;
                JSONObject e = (JSONObject) running.get(running.length() - i - 1);
                Double end = e.getDouble("range_end");
                String method = e.getString("method");
                if ("oneTime".equals(method)) {
                    total += e.getDouble("amount");
                } else if ("perKm".equals(method)) {
                    per_end = end < 0 ? kms : kms > end ? end : kms;
                    total += e.getDouble("amount") * (per_end - start);
                }
            }
            total += LocationService.pricing.getDouble("waiting") * wait;
        } catch (Exception e) {}
        return total;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1))
                * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(lat1))
                * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        return (dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

    public void run() {
        if (!LocationService.isServiceRunning || LocationService.currentLocation == null || LocationService.pendingConnection) return;
        Log.i(TAG, "TICK FROM - " + LocationService.user_id);
        if (!LocationService.mSocket.connected()) {
            Log.i(TAG, "Connecting - " + LocationService.user_id);
            LocationService.pendingConnection = true;
            LocationService.mSocket.connect();
        }
        if (lastLocation != null && !LocationService.currentLocation.equals(lastLocation)) {
            Double tkms = distance(lastLocation.getLatitude(), lastLocation.getLongitude(), LocationService.currentLocation.getLatitude(), LocationService.currentLocation.getLongitude());
            if (!Double.isNaN(tkms) || tkms < 100) LocationService.kms += tkms;
        }
        if (LocationService.currentLocation.getSpeed() < LocationService.minSpeed) {
            LocationService.wait += 1;
        }
        try {
            JSONObject obj = new JSONObject();
            obj.put("latitude", LocationService.currentLocation.getLatitude());
            obj.put("longitude", LocationService.currentLocation.getLongitude());
            obj.put("updated", LocationService.currentLocation.getTime());
            obj.put("course", LocationService.currentLocation.getBearing());
            obj.put("wait", LocationService.wait);
            try {
                obj.put("amount", calcCost(LocationService.wait, LocationService.kms));
                Log.i(TAG, "amount - " + obj.getString("amount"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            LocationService.mSocket.emit("uDL", obj);
            lastLocation = LocationService.currentLocation;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}