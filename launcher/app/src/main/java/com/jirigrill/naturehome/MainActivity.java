package com.jirigrill.naturehome;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String TAG = "NatureHome";

    private static final long SLIDE_MS = 45000L;
    private static final long FADE_MS = 1800L;
    private static final long WEATHER_REFRESH_MS = 15L * 60L * 1000L;
    private static final long WEATHER_RETRY_MS = 5L * 60L * 1000L;

    /** Remote-control vendor keycodes, as dispatched by org.droidtv.GlobalKey. */
    private static final int KEY_RAKUTEN = 779;
    private static final int KEY_IVI = 790;

    private static final String ACTION_LAUNCH_NATIVE_APP =
            "org.droidtv.tv.intent.action.LAUNCH_NATIVE_APP";

    private static final class DockSpec {
        final String label;
        final int customIcon;
        final String component;
        final String[] packages;

        DockSpec(String label, int customIcon, String component, String... packages) {
            this.label = label;
            this.customIcon = customIcon;
            this.component = component;
            this.packages = packages;
        }
    }

    private static final String[] RAKUTEN_PACKAGES = {
            "tv.wuaki.apptv",
            "com.rakuten.tv",
            "com.rakuten.tech.tv",
            "com.rakuten.tv.android",
            "com.rakuten.android.tv"
    };

    private static final DockSpec[] DOCK = {
            new DockSpec("Live TV", R.drawable.ic_live_tv,
                    "org.droidtv.playtv/.PlayTvActivity", "org.droidtv.playtv"),
            new DockSpec("YouTube", 0, null, "com.google.android.youtube.tv"),
            new DockSpec("Netflix", 0, null, "com.netflix.ninja"),
            new DockSpec("Jellyfin", 0, null, "org.jellyfin.androidtv"),
            new DockSpec("Spotify", 0, null, "com.spotify.tv.android"),
            new DockSpec("Rakuten TV", 0, null, RAKUTEN_PACKAGES),
            new DockSpec("YouTube Music", 0,
                    "com.google.android.youtube.tvmusic/com.google.android.apps.youtube.tvmusic.activity.MainActivity",
                    "com.google.android.youtube.tvmusic"),
            new DockSpec("Apps", R.drawable.ic_apps, null)
    };

    /**
     * Apps that expose a launcher icon but do not belong in the drawer: platform
     * plumbing, system configuration screens, and duplicates of the dock.
     */
    private static final String[] DRAWER_HIDDEN = {
            "org.droidtv.devicesetup",
            "org.droidtv.nettvregistration",
            "org.droidtv.eum",
            "org.droidtv.demome",
            "org.droidtv.m7opapp",
            "org.droidtv.nettv.market",
            "org.droidtv.nettvrecommender",
            "org.droidtv.recommendation",
            "org.droidtv.contentexplorer",   // "Media" file browser
            "org.droidtv.channels",          // TV Guide, reachable from Live TV
            "org.droidtv.playtv",            // Live TV is already in the dock
            "io.mimi.tvapp",                 // Mimi sound personalisation (in TV Settings)
            "cz.tmobile.tvgo"                // carrier app
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.UK);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d MMMM", Locale.UK);

    private ImageView wallA;
    private ImageView wallB;
    private boolean wallAFront = true;
    private boolean wallpaperLoading;
    private ObjectAnimator zoomX;
    private ObjectAnimator zoomY;

    private TextView clockView;
    private TextView dateView;
    private TextView weatherTemp;
    private TextView weatherDesc;
    private TextView weatherNext;
    private ImageView weatherIcon;
    private LinearLayout dock;
    private View lastDockFocus;

    private android.widget.FrameLayout drawer;
    private GridView drawerGrid;
    private TextView drawerEmpty;

    private WallpaperSet wallpapers;
    private boolean weatherLoading;
    private boolean hasWeather;
    private long lastWeatherAttempt;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            clockView.setText(timeFormat.format(now));
            dateView.setText(dateFormat.format(now));
            handler.removeCallbacks(this);
            handler.postDelayed(this, (60L - now.getSeconds()) * 1000L + 100L);
        }
    };

    private final Runnable slideTick = new Runnable() {
        @Override
        public void run() {
            showNextWallpaper();
        }
    };

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            refreshWeather(true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();
        setContentView(R.layout.activity_main);

        wallA = findViewById(R.id.wall_a);
        wallB = findViewById(R.id.wall_b);
        clockView = findViewById(R.id.clock);
        dateView = findViewById(R.id.date);
        weatherTemp = findViewById(R.id.weather_temp);
        weatherDesc = findViewById(R.id.weather_desc);
        weatherNext = findViewById(R.id.weather_next);
        weatherIcon = findViewById(R.id.weather_icon);
        dock = findViewById(R.id.dock);
        drawer = findViewById(R.id.drawer);
        drawerGrid = findViewById(R.id.drawer_grid);
        drawerEmpty = findViewById(R.id.drawer_empty);

        dock.setClipChildren(false);
        dock.setClipToPadding(false);

        wallpapers = new WallpaperSet(this);
        Bitmap first = wallpapers.next(displayWidth(), displayHeight());
        if (first != null) {
            wallA.setImageBitmap(first);
            wallA.setAlpha(1f);
            startZoom(wallA);
        }

        loadCachedWeather();
        buildDock();
        tickClock();

        drawerGrid.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry entry = ((AppDrawerAdapter) drawerGrid.getAdapter()).getItem(position);
            hideDrawer();
            if (entry.settings) {
                openSettings();
            } else {
                launchPackage(entry.packageName, null);
            }
        });

        handler.post(() -> handleRemoteIntent(getIntent()));
        handler.postDelayed(slideTick, SLIDE_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        tickClock();
        handler.removeCallbacks(slideTick);
        handler.postDelayed(slideTick, SLIDE_MS);
        refreshWeather(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Leave no drawer behind when the user switches to another app.
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) {
            hideDrawer();
        }
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(slideTick);
        handler.removeCallbacks(weatherTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        if (zoomX != null) {
            zoomX.cancel();
        }
        if (zoomY != null) {
            zoomY.cancel();
        }
        releaseWallpaper(wallA);
        releaseWallpaper(wallB);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) {
            hideDrawer();
        }
        handleRemoteIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (drawer.getVisibility() == View.VISIBLE) {
            hideDrawer();
        }
    }

    /**
     * The Philips remote's dedicated app buttons never reach apps as normal keys.
     * org.droidtv.GlobalKey turns them into intents:
     *
     * <ul>
     *   <li>Rakuten: LAUNCH_NATIVE_APP with extra KEY_EVENT=779. It used to be
     *       handled by org.droidtv.nettv.market, which this project disabled, so
     *       the button died. Nature Home now answers it itself.</li>
     *   <li>IVI: LAUNCH_NATIVE_APP with KEY_EVENT=790, also unhandled here and
     *       mapped to Live TV.</li>
     *   <li>All apps: android.intent.action.ALL_APPS. The stock launcher used to
     *       answer it; Nature Home now opens its own drawer.</li>
     * </ul>
     *
     * The Prime Video button is different: GlobalKey starts an explicit
     * component inside com.amazon.amazonvideo.livingroom, which no third-party
     * app can intercept without root. See README.
     */
    private void handleRemoteIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (ACTION_LAUNCH_NATIVE_APP.equals(action)) {
            int key = intent.getIntExtra("android.intent.extra.KEY_EVENT", -1);
            if (key == KEY_RAKUTEN) {
                launchFirst(RAKUTEN_PACKAGES, null);
            } else if (key == KEY_IVI) {
                launchFirst(new String[]{"org.droidtv.playtv"},
                        "org.droidtv.playtv/.PlayTvActivity");
            }
        } else if ("org.droidtv.tv.intent.action.LAUNCH_RAKUTEN_TV".equals(action)) {
            launchFirst(RAKUTEN_PACKAGES, null);
        } else if (Intent.ACTION_ALL_APPS.equals(action)) {
            showDrawer();
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void tickClock() {
        handler.removeCallbacks(clockTick);
        clockTick.run();
    }

    private void buildDock() {
        dock.removeAllViews();
        PackageManager pm = getPackageManager();

        for (DockSpec spec : DOCK) {
            if (spec.packages == null || spec.packages.length == 0) {
                addDockItem(spec.label, getDrawable(spec.customIcon), v -> showDrawer());
                continue;
            }

            String pkg = firstInstalled(pm, spec.packages);
            if (pkg == null) {
                continue;
            }

            Drawable icon;
            if (spec.customIcon != 0) {
                icon = getDrawable(spec.customIcon);
            } else {
                try {
                    icon = pm.getApplicationIcon(pkg);
                } catch (PackageManager.NameNotFoundException e) {
                    continue;
                }
            }

            final String target = pkg;
            final String component = spec.component;
            addDockItem(spec.label, icon, v -> launchPackage(target, component));
        }

        dock.post(() -> {
            if (dock.getChildCount() > 0) {
                dock.getChildAt(0).requestFocus();
            }
        });
    }

    private void addDockItem(String label, Drawable icon, View.OnClickListener click) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackgroundResource(R.drawable.dock_item_bg);
        item.setContentDescription(label);
        item.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        itemParams.setMargins(dp(4), 0, dp(4), 0);
        item.setLayoutParams(itemParams);

        ImageView iconView = new ImageView(this);
        int iconSize = dp(42);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        iconView.setImageDrawable(icon);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setContentDescription(null);
        item.addView(iconView);

        item.setOnClickListener(click);
        item.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.18f : 1f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(140)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            if (hasFocus) {
                lastDockFocus = v;
            }
        });

        dock.addView(item);
    }

    private String firstInstalled(PackageManager pm, String[] packages) {
        for (String pkg : packages) {
            try {
                pm.getApplicationInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {
                // try the next candidate
            }
        }
        return null;
    }

    private void launchFirst(String[] packages, String component) {
        String pkg = firstInstalled(getPackageManager(), packages);
        if (pkg != null) {
            launchPackage(pkg, component);
        }
    }

    private void launchPackage(String pkg, String component) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(pkg);
        }
        if (intent == null && component != null) {
            String[] parts = component.split("/", 2);
            if (parts.length == 2) {
                String cls = parts[1].startsWith(".")
                        ? parts[0] + parts[1]
                        : parts[1];
                intent = new Intent().setComponent(new ComponentName(parts[0], cls));
            }
        }
        if (intent == null) {
            Toast.makeText(this, "Can't open " + pkg, Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Can't open " + pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            launchPackage("com.android.tv.settings", null);
        }
    }

    private void showDrawer() {
        loadApps();
        drawer.setVisibility(View.VISIBLE);
        drawer.setAlpha(0f);
        drawer.animate().alpha(1f).setDuration(180).start();
        drawerGrid.setSelection(0);
        drawerGrid.requestFocus();
    }

    private void hideDrawer() {
        drawer.setVisibility(View.GONE);
        if (lastDockFocus != null && lastDockFocus.isAttachedToWindow()) {
            lastDockFocus.requestFocus();
        } else if (dock.getChildCount() > 0) {
            dock.getChildAt(0).requestFocus();
        }
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();

        addResolveInfos(pm, new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), found);
        addResolveInfos(pm, new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER), found);

        List<AppEntry> apps = new ArrayList<>();
        for (ResolveInfo info : found.values()) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) {
                continue;
            }
            String label = info.loadLabel(pm).toString().trim();
            if (label.isEmpty()) {
                label = pkg;
            }
            if (isHiddenFromDrawer(pkg, label)) {
                continue;
            }
            apps.add(new AppEntry(label, info.loadIcon(pm), pkg, false));
        }

        if (!found.containsKey("com.android.tv.settings")) {
            apps.add(new AppEntry("TV Settings",
                    getDrawable(R.drawable.ic_settings), "com.android.tv.settings", true));
        }

        apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

        AppDrawerAdapter adapter = new AppDrawerAdapter(this, apps);
        drawerGrid.setAdapter(adapter);
        drawerEmpty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean isHiddenFromDrawer(String pkg, String label) {
        for (String hidden : DRAWER_HIDDEN) {
            if (hidden.equals(pkg)) {
                return true;
            }
        }
        // Unlabelled platform components fall back to their package name; hide those.
        return label.equals(pkg);
    }

    private void addResolveInfos(PackageManager pm, Intent intent, Map<String, ResolveInfo> out) {
        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (!out.containsKey(pkg)) {
                out.put(pkg, info);
            }
        }
    }

    // ---------------------------------------------------------------- weather

    private void loadCachedWeather() {
        SharedPreferences prefs = getSharedPreferences("weather", Context.MODE_PRIVATE);
        long at = prefs.getLong("at", 0L);
        if (at > 0 && System.currentTimeMillis() - at < 6L * 60L * 60L * 1000L) {
            hasWeather = true;
            applyWeather(prefs.getInt("temp", 0),
                    prefs.getString("condition", ""),
                    prefs.getString("next", ""),
                    prefs.getInt("icon", R.drawable.ic_w_cloud));
        } else {
            weatherTemp.setText("--°");
            weatherDesc.setText("Loading weather");
            weatherNext.setText("");
        }
    }

    private void refreshWeather(boolean force) {
        long now = System.currentTimeMillis();
        if (weatherLoading) {
            return;
        }
        if (!force && now - lastWeatherAttempt < WEATHER_REFRESH_MS) {
            return;
        }
        weatherLoading = true;
        lastWeatherAttempt = now;

        worker.execute(() -> {
            WeatherClient.Weather weather = WeatherClient.fetch(this);
            handler.post(() -> {
                weatherLoading = false;
                if (weather != null) {
                    hasWeather = true;
                    applyWeather(weather);
                } else if (!hasWeather) {
                    weatherDesc.setText("Weather unavailable");
                    weatherNext.setText("");
                }
                if (!isFinishing()) {
                    handler.removeCallbacks(weatherTick);
                    handler.postDelayed(weatherTick,
                            weather != null ? WEATHER_REFRESH_MS : WEATHER_RETRY_MS);
                }
            });
        });
    }

    private void applyWeather(WeatherClient.Weather weather) {
        SharedPreferences prefs = getSharedPreferences("weather", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("temp", weather.tempC)
                .putString("condition", weather.condition)
                .putString("next", weather.nextHour)
                .putInt("icon", weather.iconRes)
                .putLong("at", weather.fetchedAt)
                .apply();
        applyWeather(weather.tempC, weather.condition, weather.nextHour, weather.iconRes);
    }

    private void applyWeather(int tempC, String condition, String nextHour, int iconRes) {
        weatherTemp.setText(tempC + "°");
        weatherDesc.setText(condition);
        weatherNext.setText(nextHour);
        weatherIcon.setImageResource(iconRes);
        weatherIcon.setContentDescription(condition);
    }

    // ------------------------------------------------------------- wallpapers

    private void showNextWallpaper() {
        if (isFinishing() || wallpaperLoading) {
            return;
        }
        wallpaperLoading = true;
        final int width = displayWidth();
        final int height = displayHeight();

        worker.execute(() -> {
            Bitmap bitmap = wallpapers.next(width, height);
            handler.post(() -> {
                wallpaperLoading = false;
                if (isFinishing() || isDestroyed()) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                    return;
                }
                if (bitmap != null) {
                    crossfade(bitmap);
                }
                handler.removeCallbacks(slideTick);
                handler.postDelayed(slideTick, bitmap != null ? SLIDE_MS : WEATHER_RETRY_MS);
            });
        });
    }

    private void crossfade(Bitmap bitmap) {
        ImageView incoming = wallAFront ? wallB : wallA;
        ImageView outgoing = wallAFront ? wallA : wallB;

        incoming.setImageBitmap(bitmap);
        incoming.setAlpha(0f);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        startZoom(incoming);

        incoming.animate()
                .alpha(1f)
                .setDuration(FADE_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        outgoing.animate()
                .alpha(0f)
                .setDuration(FADE_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        wallAFront = !wallAFront;
        final ImageView old = outgoing;
        handler.postDelayed(() -> releaseWallpaper(old), FADE_MS + 250);
    }

    private void startZoom(ImageView view) {
        if (zoomX != null) {
            zoomX.cancel();
        }
        if (zoomY != null) {
            zoomY.cancel();
        }

        long duration = SLIDE_MS + FADE_MS;
        zoomX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.07f);
        zoomY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.07f);
        zoomX.setDuration(duration);
        zoomY.setDuration(duration);
        zoomX.setInterpolator(new LinearInterpolator());
        zoomY.setInterpolator(new LinearInterpolator());
        zoomX.start();
        zoomY.start();
    }

    private void releaseWallpaper(ImageView view) {
        Drawable drawable = view.getDrawable();
        view.setImageDrawable(null);
        view.setAlpha(0f);
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private int displayWidth() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(metrics.widthPixels, 1920);
    }

    private int displayHeight() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(metrics.heightPixels, 1080);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
