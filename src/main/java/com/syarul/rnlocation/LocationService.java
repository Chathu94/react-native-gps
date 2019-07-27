package com.syarul.rnlocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Timer;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class LocationService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    public static final String ACTION_START = "lk.bcon.taxi.SLLS_ACTION_START";
    public static final String ACTION_OFFLINE = "lk.bcon.taxi.SLLS_ACTION_OFFLINE";
    public static final String ACTION_OPEN = "lk.bcon.taxi.SLLS_ACTION_OPEN";
    public static final String TAG = LocationService.class.getSimpleName();
    public static final String TAG_SOCKET = "SOCKET IO";
    private static final long INTERVAL = 1000 * 3;
    private static final long FASTEST_INTERVAL = 1000 * 1;
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = "bcon_notification_channel";

    public static boolean isServiceRunning = false;
    public static Location currentLocation;
    public static Socket mSocket;
    public static int wait = 0;
    public static int amount = 0;
    public static double kms = 0;
    public static double minSpeed = 4;
    public static String _id;
    public static boolean pendingConnection = false;
    MediaPlayer mPlayer;

    DrawView dv;
    FrameLayout frameLayout;
    LayoutInflater layoutInflater;
    PowerManager.WakeLock mWakeLock;
    Boolean init = false;
    /** code to post/handler request for permission */

    private UpdateTick mUpdateTick;
    private WindowManager windowManager;
    private NotificationManager mNotificationManager;
    private LocationManager mLocationManager;
    private PowerManager mPowerManager;
    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private Timer timer;
    private String jwtAccessToken;
    public static String url;
    public static String user_id;
    public static JSONObject pricing;

    public LocationService() {
        super();
    }

    public void removeOnTopView() {
        if (frameLayout != null) windowManager.removeView(frameLayout);
        frameLayout = null;
        if (dv != null) windowManager.removeView(dv);
        dv = null;
        if (mPlayer != null && mPlayer.isPlaying()) mPlayer.stop();
    }

    public static boolean openApp(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            Intent i = manager.getLaunchIntentForPackage(packageName);
            if (i == null) {
                return false;
                //throw new ActivityNotFoundException();
            }
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            context.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    public void createOnTopView(Double pLat, Double pLon, String pInfo, int reqTimeout) {
        try {
            WindowManager.LayoutParams params;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_PRIORITY_PHONE,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);

            } else {
                params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
            }
            params.gravity = Gravity.CENTER;
            params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_FULLSCREEN;
            params.dimAmount = 0.7f;

            if (frameLayout == null) frameLayout = new FrameLayout(getApplicationContext());
            Runnable r = new Runnable() {
                public void run() {
                    removeOnTopView();
                }
            };
            if (dv == null) dv = new DrawView(getApplicationContext(), currentLocation.getLatitude(), currentLocation.getLongitude(), pLat, pLon, pInfo, reqTimeout, r);

            try {
                mPlayer = MediaPlayer.create(getApplicationContext(), R.raw.incallmanager_ringtone);
                mPlayer.setLooping(true);
                mPlayer.prepare();
            } catch (IllegalStateException e) {

            } catch (IOException e) {}
            mPlayer.start();

            windowManager = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
            windowManager.addView(dv, params);

            params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN;
            params.dimAmount = 0.0f;

            windowManager.addView(frameLayout, params);

            layoutInflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            // Here is the place where you can inject whatever layout you want.
            View main = layoutInflater.inflate(R.layout.layout, frameLayout);
            ImageButton b = main.findViewById(R.id.imageButton);
//        CoordinatorLayout f = main.findViewById(R.id.frmLay);
//        f.setTop(0);
//        f.setLeft(0);
//        Display display = windowManager.getDefaultDisplay();
//        f.setRight(display.getWidth());
//        f.setBottom(display.getHeight());
            b.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    mSocket.emit("tripRequestApprove", _id);
                    openApp(getApplicationContext(), getApplicationContext().getPackageName());
                    removeOnTopView();
//                showNotification("You are online", true);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void initialize() {
        timer = new Timer();
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        try {
            mPlayer = MediaPlayer.create(getApplicationContext(), R.raw.incallmanager_ringtone);
            mPlayer.setLooping(true);
            mPlayer.prepare();
        } catch (IllegalStateException e) {
//            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
        mWakeLock.acquire();
        boolean enabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        isServiceRunning = false;
        try {
            mSocket.off();
            mSocket.disconnect();
        } catch (Exception e) {}
        try {
            windowManager.removeView(frameLayout);
        } catch (Exception e) {}
        try {
            windowManager.removeView(dv);
        } catch (Exception e) {}
        try {
            mUpdateTick.cancel();
        } catch (Exception e) {}
        try {
            timer.cancel();
        } catch (Exception e) {}
        try {
            mWakeLock.release();
        } catch (Exception e) {}
        try {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        } catch (Exception e) {}
        try {
            mGoogleApiClient.disconnect();
        } catch (Exception e) {}
        super.onDestroy();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Location services connected.");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        } else {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    private boolean isGooglePlayServicesAvailable() {
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());
        if (ConnectionResult.SUCCESS == status) {
            return true;
        } else {
//            GooglePlayServicesUtil.getErrorDialog(status, this, 0).show();
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        @SuppressLint("WrongConstant") NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "BCON Notifications", NotificationManager.IMPORTANCE_DEFAULT);
        notificationChannel.setDescription("BCON");
        notificationChannel.enableLights(true);
        notificationChannel.setLightColor(Color.CYAN);
        notificationChannel.enableVibration(false);
        mNotificationManager.createNotificationChannel(notificationChannel);
    }

    public void showNotification(String text, Boolean enableOffline) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        Intent notificationIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        notificationIntent.setAction(ACTION_OPEN);
        notificationIntent.setFlags(FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        Intent offlineReceive = new Intent(getApplicationContext(), NotificationReceiver.class);
        offlineReceive.setAction(ACTION_OFFLINE);
        PendingIntent pendingIntentYes = PendingIntent.getBroadcast(this, 12345, offlineReceive, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

        Notification.Builder mNotificationBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationBuilder = new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID);
        } else {
            mNotificationBuilder = new Notification.Builder(getApplicationContext());
        }
        Notification.Builder notificationBuilder = mNotificationBuilder
                .setContentTitle("BCON")
                .setTicker("BCON")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_bcon_notification)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (enableOffline) {
            notificationBuilder.addAction(R.drawable.common_google_signin_btn_icon_dark, "Go Offline", pendingIntentYes);
        }
        startForeground(101, notificationBuilder.build());
    }

    void startServiceWithNotification() {
        if (isServiceRunning) return;
        isServiceRunning = true;

        mUpdateTick = new UpdateTick();
        showNotification("You are online", true);

        IO.Options mOptions = new IO.Options();
        mOptions.reconnection = false;
        mOptions.forceNew = true;
        try {
            mOptions.query = "service=1&token=" + URLEncoder.encode(jwtAccessToken, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
        mOptions.transports = new String[]{"websocket"};
        try {
            mSocket = IO.socket(url, mOptions);
            handleSocket(mSocket);
            mSocket.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, e.getMessage());
        }
        mGoogleApiClient.connect();
    }

    private void handleSocket(final Socket mSocket) {
        mSocket.on("connect", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        pendingConnection = false;
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("unauthorizedService", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        stopService();
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("disconnect", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        pendingConnection = false;
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("error", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        pendingConnection = false;
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("requestingTaxi", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        int reqTimeout;
                        JSONArray pickup;
                        Double pLat;
                        Double pLon;
                        JSONObject rider;
                        String pInfo;
                        try {
                            JSONObject data = (JSONObject) args[0];
                            _id = data.getString("_id");
                            reqTimeout = Math.round(data.getInt("reqTimeout") / 1000);
                            pickup = data.getJSONArray("pickup");
                            pLat = (Double) pickup.get(1);
                            pLon = (Double) pickup.get(0);
                            rider = data.getJSONObject("rider");
                            pInfo = rider.getString("name") + " " + rider.getString("mobile");
                            createOnTopView(pLat, pLon, pInfo, reqTimeout);
                            showNotification("You have incoming trip request", false);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("startTrip", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        JSONObject trip = (JSONObject) args[0];
                        try {
                            pricing = trip.getJSONObject("pricing");
                            Log.i(TAG, "pricing - " + pricing.toString());
                            kms = 0;
                            wait = 0;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        showNotification("You are ongoing a trip", false);
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("tripCountUpdate", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        int count = (int) args[0];
                        if (count > 0) {
                            mSocket.emit("reloadTripData");
                        }
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("requestTaxiFailed", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        removeOnTopView();
                        showNotification("You are online", true);
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("tripInfoReload", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        JSONArray trip = (JSONArray) args[0];
                        try {
                            JSONObject t = ((JSONObject) trip.get(0));
                            pricing = t.getJSONObject("pricing");
                            if (t.getDouble("kms") > kms) kms = t.getDouble("kms");
                            if (t.getInt("wait") > wait) wait = t.getInt("wait");
                            int status = t.getInt("status");
                            if (status >= 30) {
                                showNotification("You are online", true);
                            } else {
                                showNotification("You are ongoing a trip", false);
                            }
                        } catch (JSONException e) {}
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("tripInfoUpdate", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        try {
                            JSONArray trip = (JSONArray) args[0];
                            try {
                                JSONObject t = ((JSONObject) trip.get(0));
                                pricing = t.getJSONObject("pricing");
                                if (t.getDouble("kms") > kms) kms = t.getDouble("kms");
                                if (t.getInt("wait") > wait) wait = t.getInt("wait");
                                int status = t.getInt("status");
                                if (status >= 30) {
                                    showNotification("You are online", true);
                                } else {
                                    showNotification("You are ongoing a trip", false);
                                }
                            } catch (JSONException e) {}
                        } catch (Exception e) {

                        }
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        mSocket.on("updateTrip", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        try {
                            JSONObject trip = (JSONObject) args[0];
                            try {
                                JSONObject update = trip.getJSONObject("update");
                                int status = update.getInt("status");
                                if (status >= 30) {
                                    showNotification("You are online", true);
                                }
                            } catch (JSONException e) {}
                        } catch (Exception e) {

                        }
                    }
                };
                Message message = h.obtainMessage(0, new Object());
                message.sendToTarget();
            }
        });
        //
        mSocket.emit("tripInfoUpdate");
    }

    public void stopService() {
        try {
            timer.cancel();
        } catch (Exception e) {}
        try {
            windowManager.removeView(frameLayout);
        } catch (Exception e) {}
        try {
            windowManager.removeView(dv);
        } catch (Exception e) {}
        try {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        } catch (Exception e) {}
        try {
            mGoogleApiClient.disconnect();
        } catch (Exception e) {}
        try {
            mUpdateTick.cancel();
        } catch (Exception e) {}
        try {
            mSocket.off();
            mSocket.disconnect();
        } catch (Exception e) {}
        try {
            mWakeLock.release();
        } catch (Exception e) {}
        try {
            stopForeground(true);
        } catch (Exception e) {}
        try {
            stopSelf();
        } catch (Exception e) {}
        isServiceRunning = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction().equals(ACTION_START)) {
            if (isServiceRunning) return Service.START_STICKY;
            initialize();
            Bundle extras = intent.getExtras();
            jwtAccessToken = extras.getString("jwtAccessToken");
            url = extras.getString("url");
            user_id = extras.getString("user_id");
            String pricingStr = extras.getString("pricing");
            try {
                pricing = new JSONObject(pricingStr);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            startServiceWithNotification();
            timer.scheduleAtFixedRate(mUpdateTick, 0, 1000);
        } else {
            stopService();
        }
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public void onLocationChanged(Location location) {
        currentLocation = location;
        if (init == false) {
            init = true;
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}