package com.ryanpotsander.chisauapp;

import android.location.Location;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Created by Ryan on 10/11/16.
 */

@IgnoreExtraProperties
public class DatabaseItem {


    public String name;
    public double lat, lng;

    public DatabaseItem() {
        //required default constructor
    }

    public DatabaseItem(String name, double lat, double lng) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }
}
