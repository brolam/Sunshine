/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.sunshine.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with Sunshine weather summary.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static String LOG_TAG = SunshineWatchFace.class.getName();
    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    /*
    * Path message for weather information.
    */
    private final static String PATH_TODAY_WEATHER = "/today_Weather";
    /*
    * Path message for weather outdated information.
    */
    private final static String PATH_TODAY_WEATHER_OUTDATED = "/notifyTodayWeatherOutdated";

    /*
    * temp weather information.
    */
    TodayWeatherData todayWeatherData;


    @Override
    public Engine onCreateEngine() {
        this.todayWeatherData = new TodayWeatherData();
        return new Engine();
    }
    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    public class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mAmbient;
        Calendar mCalendar;
        GoogleApiClient googleApiClient;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset = 0;
        float mYOffset = 0;

        private int specW, specH;
        private View viewWatchFaceLayout;
        private ProgressBar progressBarSecond;
        private TextView textViewDigitalClock, fullDate, highTemperature, lowTemperature;
        private LinearLayout linearLayoutWeather;
        private ImageView weatherIcon;
        private final Point displaySize = new Point();


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            this.googleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            // Inflate the layout that we're using for the watch face
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            this.viewWatchFaceLayout = inflater.inflate(R.layout.sunshine_watch_face, null);


            // Load the display spec - we'll need this later for measuring myLayout
            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            display.getSize(displaySize);

            this.progressBarSecond = (ProgressBar) viewWatchFaceLayout.findViewById(R.id.progressBarSecond);
            this.textViewDigitalClock = (TextView)viewWatchFaceLayout.findViewById(R.id.digital_Clock);
            this.fullDate = (TextView) viewWatchFaceLayout.findViewById(R.id.full_date);
            this.highTemperature = (TextView) viewWatchFaceLayout.findViewById(R.id.high_temperature);
            this.lowTemperature = (TextView) viewWatchFaceLayout.findViewById(R.id.low_temperature);
            this.linearLayoutWeather = (LinearLayout) viewWatchFaceLayout.findViewById(R.id.linear_layout_weather);
            this.weatherIcon = (ImageView) viewWatchFaceLayout.findViewById(R.id.weather_icon);
            setTextAppearance(getBaseContext(), R.style.text_digital_clock, textViewDigitalClock);
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            //googleApiClient.disconnect();
            super.onDestroy();
        }



        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                googleApiClient.connect();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
            if ( todayWeatherData.isExpired()) {
                notifyTodayWeatherOutdated();
            }
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Recompute the MeasureSpec fields - these determine the actual size of the layout
            specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY);
            specH = View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;

                // Show/hide the seconds fields
                if (inAmbientMode) {
                    progressBarSecond.setVisibility(View.INVISIBLE);
                    setTextAppearance(getBaseContext(), R.style.text_digital_clock_low, textViewDigitalClock);
                    fullDate.setVisibility(View.INVISIBLE);
                    linearLayoutWeather.setVisibility(View.INVISIBLE);

                } else {
                    progressBarSecond.setVisibility(View.VISIBLE);
                    setTextAppearance(getBaseContext(),R.style.text_digital_clock, textViewDigitalClock);
                    fullDate.setVisibility(View.VISIBLE);
                    linearLayoutWeather.setVisibility(View.VISIBLE);
                }

                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            this.textViewDigitalClock.setText(String.format("%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE)));
            this.progressBarSecond.setProgress(mCalendar.get(Calendar.SECOND));
            this.fullDate.setText(new SimpleDateFormat("EEE, d MMM yyyy").format(mCalendar.getTime()));
            this.highTemperature.setText(todayWeatherData.getFormatTemperatureHigh());
            this.lowTemperature.setText(todayWeatherData.getFormatTemperatureLow());

            if (todayWeatherData.getBytesImage().length > 0) {
                Bitmap bitmapWeatherIcon = BitmapFactory.decodeByteArray(todayWeatherData.getBytesImage(), 0, todayWeatherData.getBytesImage().length);
                this.weatherIcon.setImageBitmap(bitmapWeatherIcon);
            } else {
                this.weatherIcon.setImageBitmap(null);
            }

            // Update the layout
            viewWatchFaceLayout.measure(specW, specH);
            viewWatchFaceLayout.layout(0, 0, viewWatchFaceLayout.getMeasuredWidth(), viewWatchFaceLayout.getMeasuredHeight());

            // Draw it to the Canvas
            canvas.drawColor(Color.BLACK);
            canvas.translate(mXOffset, mYOffset);
            viewWatchFaceLayout.draw(canvas);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        /**
         * Set text style by SDK version
         * @param context
         * @param style
         * @param textView
         */
        void setTextAppearance(Context context, int style, TextView textView){
            if (Build.VERSION.SDK_INT < 22) {
                textView.setTextAppearance(context, style);
            } else if (Build.VERSION.SDK_INT > 22) {
                textView.setTextAppearance(style);
            }
        }

        /**
         * Send message to Sansine app notifying that the weather information is expired.
         */
        private void notifyTodayWeatherOutdated() {
            if (googleApiClient.isConnected()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                        for (Node node : nodes.getNodes()) {
                            MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), PATH_TODAY_WEATHER_OUTDATED, new byte[]{}).await();
                            if (!result.getStatus().isSuccess()) {
                                Log.e(LOG_TAG, "Error node:" + node.getDisplayName());
                            } else {
                                Log.i(LOG_TAG, "Sent to: " + node.getDisplayName() + ", path: " + PATH_TODAY_WEATHER);
                            }
                        }
                    }
                }).start();

            }
        }

        /**
         * Receive and update weather information
         * @param messageEvent
         */
        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            if ( messageEvent.getPath().equals(PATH_TODAY_WEATHER) ){
                byte[] bytes = messageEvent.getData();
                DataMap dataMap = DataMap.fromByteArray(bytes);
                todayWeatherData = new TodayWeatherData(dataMap);
                Log.v(LOG_TAG, "onMessageReceived set todayWeatherData");
                invalidate();
            }
            Log.v(LOG_TAG, String.format("onMessageReceived: %s", messageEvent.getPath()));
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.MessageApi.addListener(googleApiClient, this);
            notifyTodayWeatherOutdated();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Wearable.MessageApi.removeListener(googleApiClient, this);
            Log.v(LOG_TAG, "onConnectionSuspended.");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.v(LOG_TAG, String.format("onConnectionFailed - %s",connectionResult));
        }
    }




}
