package com.jirigrill.naturehome;

import android.graphics.drawable.Drawable;

public class AppEntry {

    public final String label;
    public final Drawable icon;
    public final String packageName;
    public final boolean settings;

    public AppEntry(String label, Drawable icon, String packageName, boolean settings) {
        this.label = label;
        this.icon = icon;
        this.packageName = packageName;
        this.settings = settings;
    }
}
