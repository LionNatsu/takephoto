package com.example.lion.takephoto;

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
    private static final String TAG = "uploadFile";
    private static final int CONNECT_TIME_OUT = 2*1000;
    private static final int READ_TIME_OUT = 5*1000;
    private static final String CHARSET = "utf-8";
    private static final String PREFIX = "--";
    private static final String LINE_END = "\r\n";
    private static final String CONTENT_TYPE = "multipart/form-data";

    private static final int TEST_SIZE = 4*1024*1024;
    private static Random rand = new Random();
    private static byte[] testBytes = null;

    public static String uploadStream(InputStream is, String RequestURL, String name, String filename) throws IOException {
        String BOUNDARY = UUID.randomUUID().toString();
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
        OutputStream outputSteam=conn.getOutputStream();
        DataOutputStream dos = new DataOutputStream(outputSteam);
        String sb = PREFIX +
                BOUNDARY + LINE_END +
                "Content-Disposition: form-data; name=\"" + name +"\"; filename=\"" + filename + "\"" + LINE_END +
                "Content-Type: application/octet-stream; charset=" + CHARSET + LINE_END +
                LINE_END;
        dos.write(sb.getBytes());
        byte[] bytes = new byte[1024];
        int len = 0;
        while((len=is.read(bytes))!=-1)
        {
            dos.write(bytes, 0, len);
        }
        is.close();
        dos.write(LINE_END.getBytes());
        byte[] end_data = (PREFIX+BOUNDARY+PREFIX+LINE_END).getBytes();
        dos.write(end_data);
        dos.flush();
        int res = conn.getResponseCode();
        if(res == 200) return conn.getResponseMessage();
        throw new IOException("response code: "+res);
    }

    public static String uploadFile(File file, String RequestURL, String name) throws IOException {
        InputStream is = new FileInputStream(file);
        return uploadStream(is, RequestURL, name, file.getName());
    }

    public static String test(String RequestURL) throws IOException {
        if (testBytes == null) {
            testBytes = new byte[TEST_SIZE];
            rand.nextBytes(testBytes);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(testBytes);
        long startTick, endTick;
        startTick = System.currentTimeMillis();
        uploadStream(bais, RequestURL, "test", "");
        endTick = System.currentTimeMillis();
        double duration = endTick - startTick;
        return "success, speed: " + String.format(Locale.US, "%.1f", (TEST_SIZE/1024.0)/(duration/1000) ) + " KiB/s";
    }
}
