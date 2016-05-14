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

package com.example.haroonyousuf.wear.Service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.haroonyousuf.wear.R;
import com.example.haroonyousuf.wear.Util.Utility;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, DataApi.DataListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;

        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        private GoogleApiClient mGoogleApiClient;
        private Resources resources;

        private static final String WEATHER_PATH = "/weather";
        private static final String HIGH_TEMPERATURE = "high_temperature";
        private static final String LOW_TEMPERATURE = "low_temperature";
        private static final String WEATHER_CONDITION = "weather_condition";

        private static final int SPACE_BETWEEN_TEMPERATURES = 10;

        Paint backgroundPaint;
        Paint timeTextPaint;
        Paint linePaint;
        Paint dateTextPaint;
        Paint highTemperatureTextPaint;
        Paint lowTemperatureTextPaint;
        boolean isAmbientMode;
        Calendar calendar;

        float timeYOffset;
        float dateYOffset;
        float weatherYOffset;

        Bitmap conditionIcon;
        String highTemperature;
        String lowTemperature;

        boolean lowBitAmbient;

        int digitalTextColor = -1;
        int digitalTextColorSecondary = -1;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            /*setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTime = new Time();*/

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            resources = SunshineWatchService.this.getResources();
            timeYOffset = resources.getDimension(R.dimen.time_y_offset);
            dateYOffset = resources.getDimension(R.dimen.date_y_offset);
            weatherYOffset = resources.getDimension(R.dimen.weather_y_offset);

            backgroundPaint = new Paint();
            backgroundPaint.setColor(ContextCompat.getColor(SunshineWatchService.this, R.color.background));

            digitalTextColor = ContextCompat.getColor(SunshineWatchService.this, R.color.digital_text);
            digitalTextColorSecondary = ContextCompat.getColor(SunshineWatchService.this, R.color.digital_text_secondary);

            linePaint = new Paint();
            linePaint.setColor(digitalTextColorSecondary);

            timeTextPaint = createTextPaint(digitalTextColor);
            dateTextPaint = createTextPaint(digitalTextColorSecondary);
            highTemperatureTextPaint = createTextPaint(digitalTextColor);
            lowTemperatureTextPaint = createTextPaint(digitalTextColorSecondary);

            // allocate a Calendar to calculate local time using the UTC time and time zone
            calendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                calendar.setTimeZone(TimeZone.getDefault());
            } else {
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }

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
            SunshineWatchService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            /*Resources resources = SunshineWatchService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);*/

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchService.this.getResources();
            float timeTextSize = resources.getDimension(R.dimen.time_text_size);
            timeTextPaint.setTextSize(timeTextSize);

            float dateTextSize = resources.getDimension(R.dimen.date_text_size);
            dateTextPaint.setTextSize(dateTextSize);

            float temperatureTextSize = resources.getDimension(R.dimen.temperature_text_size);
            highTemperatureTextPaint.setTextSize(temperatureTextSize);
            lowTemperatureTextPaint.setTextSize(temperatureTextSize);
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
            /*super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();*/

            super.onAmbientModeChanged(inAmbientMode);
            if (isAmbientMode != inAmbientMode) {
                linePaint.setColor(inAmbientMode ? digitalTextColor : digitalTextColorSecondary);
                dateTextPaint.setColor(inAmbientMode ? digitalTextColor : digitalTextColorSecondary);
                lowTemperatureTextPaint.setColor(inAmbientMode ? digitalTextColor : digitalTextColorSecondary);
                isAmbientMode = inAmbientMode;
                if (lowBitAmbient) {
                    timeTextPaint.setAntiAlias(!inAmbientMode);
                    dateTextPaint.setAntiAlias(!inAmbientMode);
                    highTemperatureTextPaint.setAntiAlias(!inAmbientMode);
                    lowTemperatureTextPaint.setAntiAlias(!inAmbientMode);
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
            Resources resources = SunshineWatchService.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(ContextCompat.getColor(getBaseContext(), mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            /*// Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);*/

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), backgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            calendar.setTimeInMillis(System.currentTimeMillis());
            int seconds = calendar.get(Calendar.SECOND);
            int minutes = calendar.get(Calendar.MINUTE);
            int hours = calendar.get(Calendar.HOUR);
            String time = isAmbientMode
                    ? String.format("%d:%02d", hours, minutes)
                    : String.format("%d:%02d:%02d", hours, minutes, seconds);

            // Draw time text in x-center of screen
            float timeTextWidth = timeTextPaint.measureText(time);
            float halfTimeTextWidth = timeTextWidth / 2;
            float xOffsetTime = bounds.centerX() - halfTimeTextWidth;
            canvas.drawText(time, xOffsetTime, timeYOffset, timeTextPaint);


            // Draw date text in x-center of screen
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM dd yyyy", Locale.US);
            String date = dateFormat.format(calendar.getTime()).toUpperCase(Locale.US);

            float dateTextWidth = dateTextPaint.measureText(date);
            float halfDateTextWidth = dateTextWidth / 2;
            float xOffsetDate = bounds.centerX() - halfDateTextWidth;
            canvas.drawText(date, xOffsetDate, dateYOffset, dateTextPaint);

            // Draw high and low temperature, icon for weather condition
            if (conditionIcon != null && highTemperature != null && lowTemperature != null) {
                float highTemperatureTextWidth = highTemperatureTextPaint.measureText(highTemperature);
                float lowTemperatureTextWidth = lowTemperatureTextPaint.measureText(lowTemperature);

                Rect temperatureBounds = new Rect();
                highTemperatureTextPaint.getTextBounds(highTemperature, 0, highTemperature.length(), temperatureBounds);

                float lineYOffset = (dateYOffset + weatherYOffset) / 2 - (temperatureBounds.height() / 2);
                canvas.drawLine(bounds.centerX() - 4 * SPACE_BETWEEN_TEMPERATURES, lineYOffset,
                        bounds.centerX() + 4 * SPACE_BETWEEN_TEMPERATURES, lineYOffset, linePaint);

                float xOffsetHighTemperature;
                if (isAmbientMode) {
                    xOffsetHighTemperature = bounds.centerX() - ((highTemperatureTextWidth + lowTemperatureTextWidth + SPACE_BETWEEN_TEMPERATURES) / 2);
                } else {
                    xOffsetHighTemperature = bounds.centerX() - (highTemperatureTextWidth / 2);

                    canvas.drawBitmap(conditionIcon, xOffsetHighTemperature - conditionIcon.getWidth() - 2 * SPACE_BETWEEN_TEMPERATURES,
                            weatherYOffset - (temperatureBounds.height() / 2) - (conditionIcon.getHeight() / 2), null);
                }

                float xOffsetLowTemperature = xOffsetHighTemperature + highTemperatureTextWidth + SPACE_BETWEEN_TEMPERATURES;

                canvas.drawText(highTemperature, xOffsetHighTemperature, weatherYOffset, highTemperatureTextPaint);
                canvas.drawText(lowTemperature, xOffsetLowTemperature, weatherYOffset, lowTemperatureTextPaint);
            }
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

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().compareTo(WEATHER_PATH) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        setWeatherData(dataMap.getString(HIGH_TEMPERATURE),
                                dataMap.getString(LOW_TEMPERATURE), dataMap.getInt(WEATHER_CONDITION));
                        invalidate();
                    }
                }
            }

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        private void setWeatherData(String highTemperature, String lowTemperature, int weatherCondition) {
            this.highTemperature = highTemperature;
            this.lowTemperature = lowTemperature;
            this.conditionIcon = BitmapFactory.decodeResource(resources, Utility.getIconResourceForWeatherCondition(weatherCondition));
        }
    }
}
