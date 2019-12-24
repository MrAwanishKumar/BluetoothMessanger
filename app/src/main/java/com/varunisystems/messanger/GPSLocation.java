package com.varunisystems.messanger;

import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;

public class GPSLocation {

    String lat, lng;
    LocationManager locationManager;
    LocationListener locationListener;
    Context context;

    public GPSLocation(String lat, String lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public String ggetLocation() {
        String latlng = null;

//        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//
//        locationManager.requestLocationUpdates(context., 0,0,  context);

        return latlng;
    }
}
