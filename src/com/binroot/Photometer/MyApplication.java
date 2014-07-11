package com.binroot.Photometer;


import android.app.Application;

public class MyApplication extends Application {

    public ParseCloud mParseCloud;
    public HueData mHueData;

    @Override
    public void onCreate() {
        super.onCreate();

        mParseCloud = new ParseCloud(getApplicationContext());
    }
}
