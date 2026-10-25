package com.jirigrill.naturehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Wallpaper rotation with three sources:
 *
 * <ol>
 *   <li>the eight photos bundled in the APK (always available),</li>
 *   <li>every image dropped into
 *       {@code Android/data/com.jirigrill.naturehome/files/wallpapers/},</li>
 *   <li>every URL listed in {@code wallpapers/urls.txt} in the same folder,
 *       downloaded once and cached under {@code files/wallpaper-cache/}.</li>
 * </ol>
 *
 * Local files and URLs can number in the hundreds. Nothing outside the app's own
 * external files directory is read, so no storage permission is needed.
 */
final class WallpaperSet {

    private static final int[] BUNDLED = {
            R.drawable.wp1,
            R.drawable.wp2,
            R.drawable.wp3,
            R.drawable.wp4,
            R.drawable.wp5,
            R.drawable.wp6,
            R.drawable.wp7,
            R.drawable.wp8
    };

    private static final String[] IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"};
    private static final int MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024;

    private static final class Item {
        final int resId;
        final File file;
        final String url;

        Item(int resId, File file, String url) {
            this.resId = resId;
            this.file = file;
            this.url = url;
        }
    }

    private final Context context;
    private final File wallpapersDir;
    private final File cacheDir;
    private final List<Item> items = new ArrayList<>();
    private int[] order = new int[0];
    private int cursor = -1;

    WallpaperSet(Context context) {
        this.context = context.getApplicationContext();
        File root = this.context.getExternalFilesDir(null);
        if (root == null) {
            root = this.context.getFilesDir();
        }
        wallpapersDir = new File(root, "wallpapers");
        cacheDir = new File(root, "wallpaper-cache");
        discover();
    }

    private void discover() {
        for (int id : BUNDLED) {
            items.add(new Item(id, null, null));
        }

        File[] files = wallpapersDir.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                if (file.isFile() && hasImageSuffix(file.getName())) {
                    items.add(new Item(0, file, null));
                }
            }
        }

        File manifest = new File(wallpapersDir, "urls.txt");
        List<String> urls = readUrlManifest(manifest);
        for (String url : urls) {
            items.add(new Item(0, null, url));
        }

        List<Integer> shuffled = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            shuffled.add(i);
        }
        Collections.shuffle(shuffled, new Random());
        order = new int[shuffled.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = shuffled.get(i);
        }
    }

    private static boolean hasImageSuffix(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        for (String suffix : IMAGE_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> readUrlManifest(File manifest) {
        List<String> urls = new ArrayList<>();
        if (!manifest.isFile()) {
            return urls;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(manifest), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("https://") || line.startsWith("http://")) {
                    urls.add(line);
                }
            }
        } catch (Exception ignored) {
            // A malformed manifest simply falls back to the bundled photos.
        }
        return urls;
    }

    int size() {
        return order.length;
    }

    int localCount() {
        int count = 0;
        for (Item item : items) {
            if (item.resId != 0 || item.file != null) {
                count++;
            }
        }
        return count;
    }

    int remoteCount() {
        return items.size() - localCount();
    }

    /**
     * Decodes the next wallpaper in shuffled order. Tries every remaining source
     * once, so one broken URL cannot stall the slideshow.
     */
    Bitmap next(int width, int height) {
        if (order.length == 0) {
            return null;
        }
        for (int attempt = 0; attempt < order.length; attempt++) {
            cursor = (cursor + 1) % order.length;
            Bitmap bitmap = load(items.get(order[cursor]), width, height);
            if (bitmap != null) {
                return bitmap;
            }
        }
        return null;
    }

    private Bitmap load(Item item, int width, int height) {
        if (item.resId != 0) {
            return decodeResource(item.resId, width, height);
        }
        File source = item.file;
        if (source == null && item.url != null) {
            source = cachedCopy(item.url);
        }
        if (source == null || !source.isFile()) {
            return null;
        }
        return decodeFile(source, width, height);
    }

    private File cachedCopy(String url) {
        File cached = new File(cacheDir, cacheName(url));
        if (cached.isFile() && cached.length() > 0) {
            return cached;
        }
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
            return null;
        }
        File temp = new File(cacheDir, cached.getName() + ".part");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "NatureHome/2.0");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            long copied = 0;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    copied += read;
                    if (copied > MAX_DOWNLOAD_BYTES) {
                        break;
                    }
                    out.write(buffer, 0, read);
                }
            }
            if (copied == 0 || copied > MAX_DOWNLOAD_BYTES || !temp.renameTo(cached)) {
                temp.delete();
                return null;
            }
            return cached;
        } catch (Exception e) {
            temp.delete();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String cacheName(String url) {
        return Integer.toHexString(url.hashCode()) + "-" + Integer.toHexString(url.length()) + ".img";
    }

    private Bitmap decodeResource(int resId, int width, int height) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resId, bounds);
        return BitmapFactory.decodeResource(context.getResources(), resId, decodeOptions(bounds, width, height));
    }

    private static Bitmap decodeFile(File file, int width, int height) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath(), decodeOptions(bounds, width, height));
    }

    private static BitmapFactory.Options decodeOptions(BitmapFactory.Options bounds, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        int sample = 1;
        int targetWidth = Math.max(width, 1);
        int targetHeight = Math.max(height, 1);
        while (bounds.outWidth / (sample * 2) >= targetWidth
                && bounds.outHeight / (sample * 2) >= targetHeight) {
            sample *= 2;
        }
        options.inSampleSize = sample;
        return options;
    }
}
