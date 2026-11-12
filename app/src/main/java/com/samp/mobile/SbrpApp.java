package com.samp.mobile;

import android.app.Application;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SBRP: writes any uncaught Java exception to Download/SBRP_CRASH.txt so the
 * crash can be diagnosed on a non-rooted phone (no logcat access needed).
 */
public class SbrpApp extends Application {

    private static final String TAG = "SBRP";
    private static final String FILE_NAME = "SBRP_CRASH.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                try {
                    writeCrash(thread, throwable);
                } catch (Throwable ignored) {
                }
                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                }
            }
        });
    }

    private void writeCrash(Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== SBRP crash report ===");
        pw.println("time   : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("app    : " + getPackageName() + " v" + versionName());
        pw.println("device : " + Build.MANUFACTURER + " " + Build.MODEL
                + " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")");
        pw.println("thread : " + thread.getName());
        pw.println();
        throwable.printStackTrace(pw);
        pw.flush();
        String text = sw.toString();

        Log.e(TAG, text);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(text);
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File f = new File(dir, FILE_NAME);
            try {
                FileOutputStream os = new FileOutputStream(f);
                os.write(text.getBytes("UTF-8"));
                os.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void writeViaMediaStore(String text) {
        try {
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            try {
                getContentResolver().delete(collection,
                        MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{FILE_NAME});
            } catch (Exception ignored) {
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri item = getContentResolver().insert(collection, values);
            if (item == null) {
                return;
            }
            OutputStream os = getContentResolver().openOutputStream(item);
            if (os != null) {
                os.write(text.getBytes("UTF-8"));
                os.close();
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(item, values, null, null);
        } catch (Throwable ignored) {
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
