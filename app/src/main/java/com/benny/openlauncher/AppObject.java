package com.benny.openlauncher;

import android.app.Application;
import android.content.Intent;

import xyz.no.domain.OverlayDrawerService;

public class AppObject extends Application {
    private static AppObject _instance;

    public static AppObject get() {
        return _instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        _instance = this;
    }
}