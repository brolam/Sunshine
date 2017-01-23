package com.example.android.sunshine.wear;

import com.google.android.gms.wearable.DataMap;
import java.util.Date;

/**
 * Temporarily stores the weather information and also controls the validity of the information
 * {@see isExpired }
 */
public class TodayWeatherData {
    String formatTemperatureHigh;
    String formatTemperatureLow;
    byte[] bytesImage;
    Date validUntil;

    public TodayWeatherData(DataMap dataMap) {
        this.formatTemperatureHigh = dataMap.getString("formatTemperatureHigh");
        this.formatTemperatureLow = dataMap.getString("formatTemperatureLow");
        this.bytesImage = dataMap.getByteArray("smallIcon");
        this.validUntil = new Date(dataMap.getLong("validUntil"));
    }

    public TodayWeatherData(){
        this.formatTemperatureHigh = "";
        this.formatTemperatureLow = "";
        this.bytesImage = new byte[]{};
        this.validUntil = new Date(0);
    }

    public String getFormatTemperatureHigh() {
        return formatTemperatureHigh;
    }

    public String getFormatTemperatureLow() {
        return formatTemperatureLow;
    }

    public byte[] getBytesImage() {
        return bytesImage;
    }


    /**
     * Informs if the weather information is expired.
     * @return true when expired
     */
    public boolean isExpired(){
        return new Date().after(validUntil);
    }
}
