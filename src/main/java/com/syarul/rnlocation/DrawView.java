package com.syarul.rnlocation;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DrawView extends View {
    public static final String TAG = LocationService.class.getSimpleName();

    int w;
    int h;
    int r;

    float margin_lr;
    float radius;
    float radiusMap;
    float boxLeft;
    float boxTop;
    float boxRight;
    float boxBottom;
    float mapBottom;

    float screenFactor;
    Context context;
    Double cLat;
    Double cLon;
    Double pLat;
    Double pLon;
    String pInfo;
    Runnable complete;
    int d;

    String kms = "-";
    String mins = "-";
    String pAddress = "-";

    TextPaint bconPaint;
    TextPaint incommingPaint;
    TextPaint pickupInfoPaintL;
    TextPaint pickupInfoPaintR;
    TextPaint pickupInfoPaintC;
    TextPaint passengerInfoPaint;
    Paint progressPaint = new Paint();
    float animationValue = 0.0f;
    Bitmap map;
    Canvas canvas;

    public static Bitmap getRoundedCornerBitmap(Context context, Bitmap input, int pixels , int w , int h , boolean squareTL, boolean squareTR, boolean squareBL, boolean squareBR  ) {

        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final float densityMultiplier = context.getResources().getDisplayMetrics().density;

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, w, h);
        final RectF rectF = new RectF(rect);
        final float roundPx = pixels*densityMultiplier;

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        if (squareTL ){
            canvas.drawRect(0, 0, w/2, h/2, paint);
        }
        if (squareTR ){
            canvas.drawRect(w/2, 0, w, h/2, paint);
        }
        if (squareBL ){
            canvas.drawRect(0, h/2, w/2, h, paint);
        }
        if (squareBR ){
            canvas.drawRect(w/2, h/2, w, h, paint);
        }

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(input, 0,0, paint);

        return output;
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        float l;
        float t;
        float r;
        float b;
        float c;

        public DownloadImageTask(float left, float top, float right, float bottom, float radius) {
            l = left;
            t = top;
            r = right;
            b = bottom;
            c = radius;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return mIcon11;
        }
        protected void onPostExecute(Bitmap source) {
            if (canvas != null) canvas.drawBitmap(getRoundedCornerBitmap(context, source, Math.round(radius), Math.round(r - l), Math.round(b - t), false, false, true, true), l, t, null);
            if (canvas == null) map = source;
//            if (canvas != null) canvas.drawRoundRect(new RectF(l, t, r, b), c, c, map);

            invalidate();
        }
    }

    private class CallGoogle extends AsyncTask<String, Integer, String> {
        String ga_key;

        CallGoogle(String google_api_key) {
            ga_key = google_api_key;
        }

        protected String doInBackground(String... urls) {
            String content = "", line;
            Long tsLong = System.currentTimeMillis()/1000;
            String ts = tsLong.toString();
            try {
                URL url = new URL("https://maps.googleapis.com/maps/api/directions/json?origin=" + cLat +"," + cLon + "&destination=" + pLat + "," + pLon + "&departure_time=" + ts + "&traffic_model=optimistic&key=" + ga_key + "&alternatives=true");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoOutput(true);
                connection.setConnectTimeout(25000);
                connection.setReadTimeout(25000);
                connection.connect();
                BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = rd.readLine()) != null) {
                    content += line + "\n";
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return content;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(String result) {
            // this is executed on the main thread after the process is over
            // update your UI here
            try {
                JSONObject res = new JSONObject(result);
                JSONArray routes = res.getJSONArray("routes");
                JSONArray legs = ((JSONObject) routes.get(0)).getJSONArray("legs");
                JSONObject duration = ((JSONObject) legs.get(0)).getJSONObject("duration");
                JSONObject distance = ((JSONObject) legs.get(0)).getJSONObject("distance");
                pAddress = ((JSONObject) legs.get(0)).getString("end_address");
                mins = duration.getString("text");
                kms = distance.getString("text");
                invalidate();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public DrawView(Context activity, Double currentLat, Double currentLon, Double pickupLat, Double pickupLon, String passengerInfo, int duration, Runnable removeOnTopView) {
        super(activity);
        context = activity;
        cLat = currentLat;
        cLon = currentLon;
        pLat = pickupLat;
        pLon = pickupLon;
        pInfo = passengerInfo;
        d = duration;
        complete = removeOnTopView;

        bconPaint = new TextPaint();
        incommingPaint = new TextPaint();
        pickupInfoPaintL = new TextPaint();
        pickupInfoPaintR = new TextPaint();
        pickupInfoPaintC = new TextPaint();
        passengerInfoPaint = new TextPaint();

        new CallGoogle(getResources().getString(R.string.google_api_key)).execute();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {

        w = width;
        h = height;
        r = w / 2;

        screenFactor = (r / 160f);


        margin_lr = 20;
        radius = 10;
        radiusMap = 5;
        boxLeft = 0;
        boxTop = h - w - (margin_lr * 2);
        boxRight = w;
        boxBottom = h - margin_lr;
        mapBottom = boxTop + ((boxBottom - boxTop) / 2);

        new DownloadImageTask(boxLeft, boxTop, boxRight, mapBottom, radiusMap).execute("https://maps.googleapis.com/maps/api/staticmap?size=" + Math.round(boxRight - boxLeft) + "x" + Math.round(mapBottom - boxTop) + "&key=" + getResources().getString(R.string.google_api_key) + "&scale=1&zoom=15&center=" + pLat + "," + pLon + "&markers=color:red|label:Pickup|" + pLat + "," + pLon + "&style=feature%3Aadministrative.locality%7Celement%3Alabels.text.fill%7Ccolor%3A0x0a0000%7C&style=feature%3Aadministrative.neighborhood%7Celement%3Alabels.text.fill%7Ccolor%3A0x7e1182%7C");

        bconPaint.setAntiAlias(true);
        bconPaint.setTextSize(100);
        bconPaint.setTextAlign(Paint.Align.CENTER);
        bconPaint.setTypeface(Typeface.create("Roboto", Typeface.BOLD));

        progressPaint.setARGB(255,206,26,211);

        incommingPaint.setAntiAlias(true);
        incommingPaint.setTextSize(50);
        incommingPaint.setTextAlign(Paint.Align.CENTER);
        incommingPaint.setTypeface(Typeface.create("Roboto", Typeface.NORMAL));

        pickupInfoPaintL.setAntiAlias(true);
        pickupInfoPaintL.setTextSize(30);
        pickupInfoPaintL.setTextAlign(Paint.Align.RIGHT);
        pickupInfoPaintL.setTypeface(Typeface.create("Roboto", Typeface.NORMAL));

        pickupInfoPaintR.setAntiAlias(true);
        pickupInfoPaintR.setTextSize(30);
        pickupInfoPaintR.setTextAlign(Paint.Align.LEFT);
        pickupInfoPaintR.setTypeface(Typeface.create("Roboto", Typeface.NORMAL));

        pickupInfoPaintC.setAntiAlias(true);
        pickupInfoPaintC.setTextSize(24);
        pickupInfoPaintC.setTextAlign(Paint.Align.CENTER);
        pickupInfoPaintC.setTypeface(Typeface.create("Roboto", Typeface.NORMAL));

        passengerInfoPaint.setAntiAlias(true);
        passengerInfoPaint.setTextSize(26);
        passengerInfoPaint.setTextAlign(Paint.Align.CENTER);
        passengerInfoPaint.setTypeface(Typeface.create("Roboto", Typeface.NORMAL));

        ValueAnimator animator= ValueAnimator.ofFloat(0, 1);
        animator.setDuration(d * 1000);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                animationValue = ((Float) (animation.getAnimatedValue())).floatValue();
                if (animationValue <= 1.0 && animationValue > 0.0) {
                    invalidate();
                }
                if (animationValue == 1.0) {
                    complete.run();
                }
            }
        });
        animator.start();

        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        Paint paint = new Paint();
        paint.setARGB(250, 200, 200, 200);
        canvas.drawRoundRect(new RectF(boxLeft, mapBottom - radius, boxRight, boxBottom), radius, radius, paint);
        bconPaint.setARGB(200,255,255,255);
        incommingPaint.setARGB(200,206,255,255);
        canvas.drawText("BCON", w / 2, h / 4, bconPaint);
        canvas.drawText("Incoming trip request", w / 2, h / 3, incommingPaint);

        canvas.drawRect(boxLeft, mapBottom,boxRight * animationValue,mapBottom + 10, progressPaint);

        float textArea = ((boxBottom - 5) - (mapBottom + 5));
        float lines = 7;
        float topSpan = mapBottom + 10;
        canvas.drawText(mins, (w / 2) - 10, topSpan + ((textArea / lines) * 1), pickupInfoPaintL);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.BLACK);
        canvas.drawLine(w / 2, topSpan + ((textArea / lines) * 1) - 25, w / 2, topSpan + ((textArea / lines) * 1) + 10, linePaint);

        canvas.drawText(kms, (w / 2) + 10, topSpan + ((textArea / lines) * 1), pickupInfoPaintR);
        String[] add = pAddress.split(", ");
        for (int i = 0; i < add.length; i++) {
            canvas.drawText(add[i], w / 2, topSpan + ((textArea / lines) * (2 + i)), pickupInfoPaintC);
        }
        canvas.drawText(pInfo, w / 2, topSpan + ((textArea / lines) * (3 + add.length)) - 10, passengerInfoPaint);
        if (map != null) canvas.drawBitmap(getRoundedCornerBitmap(context, map, Math.round(radiusMap), Math.round(boxRight - boxLeft), Math.round(mapBottom - boxTop), false, false, true, true), boxLeft, boxTop, null);
    }
}