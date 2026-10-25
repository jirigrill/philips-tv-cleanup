package com.jirigrill.naturehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Current weather plus a next-hour precipitation outlook, using Open-Meteo
 * (no API key, no account, no ads).
 *
 * <p>Location resolution, in order: a {@code location.txt} file in the app's
 * external files directory ({@code "lat,lon"} or {@code "lat lon"}), a cached
 * IP lookup, then a fresh IP lookup. The IP lookup runs at most once a week.</p>
 */
final class WeatherClient {

    static final class Weather {
        final int tempC;
        final String condition;
        final String nextHour;
        final int iconRes;
        final long fetchedAt;

        Weather(int tempC, String condition, String nextHour, int iconRes, long fetchedAt) {
            this.tempC = tempC;
            this.condition = condition;
            this.nextHour = nextHour;
            this.iconRes = iconRes;
            this.fetchedAt = fetchedAt;
        }
    }

    private static final long LOCATION_TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 15000;

    private WeatherClient() {
    }

    static Weather fetch(Context context) {
        if (!isOnline(context)) {
            return null;
        }
        double[] location = resolveLocation(context);
        if (location == null) {
            return null;
        }

        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&minutely_15=precipitation,precipitation_probability"
                        + "&forecast_minutely_15=8"
                        + "&timezone=auto",
                location[0], location[1]);

        try {
            JSONObject root = new JSONObject(httpGet(url));
            JSONObject current = root.optJSONObject("current");
            if (current == null) {
                return null;
            }
            double temperature = current.optDouble("temperature_2m", Double.NaN);
            int code = current.optInt("weather_code", -1);
            if (Double.isNaN(temperature) || code < 0) {
                return null;
            }

            String condition = conditionText(code);
            int icon = conditionIcon(code, current.optInt("is_day", 1) == 1);
            String nextHour = nextHourText(root, code, temperature);
            return new Weather((int) Math.round(temperature), condition, nextHour, icon,
                    System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isOnline(Context context) {
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    private static double[] resolveLocation(Context context) {
        double[] fileLocation = readLocationFile(context);
        if (fileLocation != null) {
            return fileLocation;
        }

        SharedPreferences prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        long stamped = prefs.getLong("location_at", 0L);
        double lat = Double.longBitsToDouble(prefs.getLong("location_lat", 0L));
        double lon = Double.longBitsToDouble(prefs.getLong("location_lon", 0L));
        if (stamped > 0 && System.currentTimeMillis() - stamped < LOCATION_TTL_MS
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
            return new double[]{lat, lon};
        }

        double[] looked = lookupIpLocation(context);
        return looked != null ? looked : (stamped > 0 ? new double[]{lat, lon} : null);
    }

    private static double[] readLocationFile(Context context) {
        File root = context.getExternalFilesDir(null);
        if (root == null) {
            root = context.getFilesDir();
        }
        File file = new File(root, "location.txt");
        if (!file.isFile()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            String cleaned = line.replace(',', ' ').trim();
            String[] parts = cleaned.split("\\s+");
            if (parts.length < 2) {
                return null;
            }
            double lat = Double.parseDouble(parts[0]);
            double lon = Double.parseDouble(parts[1]);
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                return null;
            }
            return new double[]{lat, lon};
        } catch (Exception e) {
            return null;
        }
    }

    private static double[] lookupIpLocation(Context context) {
        String[][] endpoints = {
                {"https://ipapi.co/json/", "latitude", "longitude"},
                {"https://ipwho.is/", "latitude", "longitude"}
        };
        for (String[] endpoint : endpoints) {
            try {
                JSONObject json = new JSONObject(httpGet(endpoint[0]));
                double lat = json.optDouble(endpoint[1], Double.NaN);
                double lon = json.optDouble(endpoint[2], Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    continue;
                }
                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    continue;
                }
                context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
                        .putLong("location_lat", Double.doubleToLongBits(lat))
                        .putLong("location_lon", Double.doubleToLongBits(lon))
                        .putLong("location_at", System.currentTimeMillis())
                        .apply();
                return new double[]{lat, lon};
            } catch (Exception ignored) {
                // Try the next provider.
            }
        }
        return null;
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "NatureHome/2.0 (Philips Android TV)");
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            StringBuilder body = new StringBuilder();
            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            return body.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String nextHourText(JSONObject root, int code, double temperature) {
        JSONObject minutely = root.optJSONObject("minutely_15");
        if (minutely == null) {
            return "Next hour unavailable";
        }
        JSONArray precipitation = minutely.optJSONArray("precipitation");
        JSONArray probability = minutely.optJSONArray("precipitation_probability");
        if (precipitation == null || precipitation.length() == 0) {
            return "Next hour unavailable";
        }

        int slots = Math.min(4, precipitation.length());
        int firstWetSlot = -1;
        int peakProbability = 0;
        for (int i = 0; i < slots; i++) {
            double amount = precipitation.optDouble(i, 0.0);
            int chance = probability == null ? 0 : probability.optInt(i, 0);
            peakProbability = Math.max(peakProbability, chance);
            if (firstWetSlot < 0 && (amount >= 0.1 || chance >= 50)) {
                firstWetSlot = i;
            }
        }

        boolean snow = isSnowCode(code) || temperature <= 0.5;
        String what = snow ? "Snow" : "Rain";
        String chance = peakProbability > 0 ? " · " + peakProbability + "%" : "";

        if (firstWetSlot == 0) {
            return what + " now" + chance;
        }
        if (firstWetSlot > 0) {
            return what + " in " + (firstWetSlot * 15) + " min" + chance;
        }
        return "Dry for the next hour";
    }

    private static boolean isSnowCode(int code) {
        return (code >= 71 && code <= 77) || code == 85 || code == 86;
    }

    private static int conditionIcon(int code, boolean day) {
        if (code == 0 || code == 1) {
            return day ? R.drawable.ic_w_sun : R.drawable.ic_w_clear_night;
        }
        if (code == 2) {
            return day ? R.drawable.ic_w_partly : R.drawable.ic_w_cloud;
        }
        if (code == 3) {
            return R.drawable.ic_w_cloud;
        }
        if (code == 45 || code == 48) {
            return R.drawable.ic_w_fog;
        }
        if (isSnowCode(code)) {
            return R.drawable.ic_w_snow;
        }
        if (code >= 95) {
            return R.drawable.ic_w_thunder;
        }
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return R.drawable.ic_w_rain;
        }
        return R.drawable.ic_w_cloud;
    }

    private static String conditionText(int code) {
        if (code == 0) {
            return "Clear";
        }
        if (code == 1) {
            return "Mostly clear";
        }
        if (code == 2) {
            return "Partly cloudy";
        }
        if (code == 3) {
            return "Overcast";
        }
        if (code == 45 || code == 48) {
            return "Fog";
        }
        if (code >= 51 && code <= 57) {
            return "Drizzle";
        }
        if (code >= 61 && code <= 67) {
            return "Rain";
        }
        if (code >= 71 && code <= 77) {
            return "Snow";
        }
        if (code >= 80 && code <= 82) {
            return "Showers";
        }
        if (code == 85 || code == 86) {
            return "Snow showers";
        }
        if (code >= 95) {
            return "Thunderstorm";
        }
        return "Weather";
    }
}
