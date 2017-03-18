package com.example.lion.takephoto;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

class HttpHelper {
    private static final int CONNECT_TIME_OUT = 2 * 1000;
    private static final int READ_TIME_OUT = 5 * 1000;
    private static final String CHARSET = "utf-8";
    private static final String PREFIX = "--";
    private static final String LINE_END = "\r\n";
    private static final String CONTENT_TYPE = "multipart/form-data";

    private static final int TEST_SIZE = 4 * 1024 * 1024;
    private static Random rand = new Random();
    private static byte[] testBytes = null;

    private static boolean waitForDialog;
    private static boolean prepareDialog = true;
    private static boolean regardlessOfMeteredConnection;

    private static String uploadStream(MainActivity context, InputStream is, String RequestURL, String name, final String filename) throws IOException {
        String BOUNDARY = UUID.randomUUID().toString();

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isMetered = cm.isActiveNetworkMetered();
        if (isMetered) {
            if (prepareDialog) {
                askForMeteredConn(context);
                if (regardlessOfMeteredConnection) {
                    prepareDialog = false;
                }
            }
            if (!regardlessOfMeteredConnection) {
                throw new IOException("canceled due to metered connection");
            }
        }

        URL url = new URL(RequestURL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(READ_TIME_OUT);
        conn.setConnectTimeout(CONNECT_TIME_OUT);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Charset", CHARSET);
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Content-Type", CONTENT_TYPE + "; boundary=" + BOUNDARY);
        OutputStream outputSteam = conn.getOutputStream();
        DataOutputStream dos = new DataOutputStream(outputSteam);
        String sb = PREFIX +
                BOUNDARY + LINE_END +
                "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"" + LINE_END +
                "Content-Type: application/octet-stream; charset=" + CHARSET + LINE_END +
                LINE_END;
        dos.write(sb.getBytes());
        byte[] bytes = new byte[1024];
        int len;
        while ((len = is.read(bytes)) != -1) {
            dos.write(bytes, 0, len);
        }
        is.close();
        dos.write(LINE_END.getBytes());
        byte[] end_data = (PREFIX + BOUNDARY + PREFIX + LINE_END).getBytes();
        dos.write(end_data);
        dos.flush();
        int res = conn.getResponseCode();
        if (res == 200) return conn.getResponseMessage();
        throw new IOException("response code: " + res);
    }

    static String uploadFile(MainActivity context, File file, String RequestURL, String name) throws IOException {
        InputStream is = new FileInputStream(file);
        return uploadStream(context, is, RequestURL, name, file.getName());
    }

    static String uploadTest(MainActivity context, String RequestURL) throws IOException {
        if (testBytes == null) {
            testBytes = new byte[TEST_SIZE];
            rand.nextBytes(testBytes);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(testBytes);
        long startTick, endTick;
        startTick = System.currentTimeMillis();
        uploadStream(context, bais, RequestURL, "test", "");
        endTick = System.currentTimeMillis();
        double duration = endTick - startTick;
        return "success, speed: " + String.format(Locale.US, "%.1f", (TEST_SIZE / 1024.0) / (duration / 1000)) + " KiB/s";
    }

    private static void askForMeteredConn(MainActivity context) {
        waitForDialog = true;
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Warning: Metered Connection");
        builder.setMessage("You may incur additional charges because you are now using a metered connection, e.g. mobile data, metered Wi-Fi/Bluetooth.");
        builder.setPositiveButton("I DO NOT CARE", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                regardlessOfMeteredConnection = true;
                synchronized (builder) {
                    waitForDialog = false;
                    builder.notify();
                }
            }
        }).setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                waitForDialog = false;
                synchronized (builder) {
                    waitForDialog = false;
                    builder.notify();
                }
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                waitForDialog = false;
                synchronized (builder) {
                    waitForDialog = false;
                    builder.notify();
                }
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                waitForDialog = false;
                synchronized (builder) {
                    waitForDialog = false;
                    builder.notify();
                }
            }
        }).setCancelable(true);
        context.uiHandler.post(new Runnable() {
            @Override
            public void run() {
                builder.show();
            }
        });
        try {
            synchronized (builder) {
                while (waitForDialog) {
                    builder.wait();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
