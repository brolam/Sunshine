package com.example.android.sunshine.sync;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.example.android.sunshine.utilities.NotificationUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Service to receive and send message to Android Wear Device connected to the Sunshine app.
 */
public class SunshineWearService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {
    /*
    * Path message for weather outdated information.
    */
    private final static String PATH_TODAY_WEATHER_OUTDATED = "/notifyTodayWeatherOutdated";
    private static final String FLAG_SEND_TODAY_WEATHER = "flag_send_today_weather";

    GoogleApiClient googleApiClient;

    /**
     * Send the weather information updated to Android Wear Device connected
     * @param context
     */
    public static void sendTodayWeather(Context context) {
        Intent intent = new Intent(context, SunshineWearService.class);
        intent.putExtra(FLAG_SEND_TODAY_WEATHER, true);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        this.googleApiClient.connect();
        Wearable.MessageApi.addListener(googleApiClient, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getExtras().getBoolean(FLAG_SEND_TODAY_WEATHER, false)) {
            NotificationUtils.notifyWearOfNewWeather(this, googleApiClient);
        }
        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        //Receives the information message weather outdated and
        // sends the weather information updated.
        if (messageEvent.getPath().equals(PATH_TODAY_WEATHER_OUTDATED)) {
            NotificationUtils.notifyWearOfNewWeather(this, googleApiClient);
        }
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

}
