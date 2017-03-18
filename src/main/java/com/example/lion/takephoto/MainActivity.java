package com.example.lion.takephoto;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MainActivity extends AppCompatActivity implements
        GestureDetector.OnGestureListener {

    private static final int CAMERA_REQUEST_CODE = 1001;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 200;
    private static final int REFRACTORY_PERIOD = 500;
    public Handler uiHandler = new Handler();
    private ViewFlipper viewFlipper;
    private GestureDetector detector;
    private CharSequence defaultBanner;
    private int currentPage = 0;
    private ReentrantLock LR = new ReentrantLock();
    private boolean inRefractoryPeriod = false;
    private ReentrantReadWriteLock LP = new ReentrantReadWriteLock();
    private boolean progressStatus = false;
    private File image;
    private Uri uri;
    private ReentrantReadWriteLock L = new ReentrantReadWriteLock();
    private boolean disable = false;

    private ReentrantReadWriteLock L2 = new ReentrantReadWriteLock();
    private String baseUrl;
    private File baseUrlFile;

    private StringBuffer logBuffer = new StringBuffer();

    public synchronized void log(CharSequence tag, CharSequence msg) {
        logBuffer.append("\n").append(tag).append(": ").append(msg);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.textViewLog)).setText(logBuffer);
            }
        });
    }

    private boolean jobEnter() {
        boolean isDisabled;
        L.readLock().lock();
        isDisabled = disable;
        L.readLock().unlock();
        if (isDisabled) return false;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                defaultBanner = ((TextView) findViewById(R.id.preview_content)).getText();
                ((TextView) findViewById(R.id.preview_content)).setText("...");
            }
        });
        L.writeLock().lock();
        disable = true;
        L.writeLock().unlock();
        return true;
    }

    private void jobLeave() {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.preview_content)).setText(defaultBanner);
            }
        });
        L.writeLock().lock();
        disable = false;
        L.writeLock().unlock();
    }

    private String getBaseURL() {
        L2.readLock().lock();
        String serverName = this.baseUrl;
        L2.readLock().unlock();
        return "http://" + serverName;
    }

    private synchronized void setProgressBar(final boolean flag) {
        LP.writeLock().lock();
        this.progressStatus = flag;
        LP.writeLock().unlock();
        LR.lock();
        if (inRefractoryPeriod) {
            LR.unlock();
            return;
        }
        if (flag) {
            inRefractoryPeriod = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(REFRACTORY_PERIOD);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    LR.lock();
                    inRefractoryPeriod = false;
                    LR.unlock();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.updateProgressBar();
                        }
                    });
                }
            }).start();
        }
        LR.unlock();
        this.updateProgressBar();
    }

    private synchronized void updateProgressBar() {
        LP.readLock().lock();
        ((ProgressBar) findViewById(R.id.progressBar)).setIndeterminate(progressStatus);
        LP.readLock().unlock();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                log("permission", "granted");
                Toast.makeText(MainActivity.this, "Now try again", Toast.LENGTH_SHORT).show();
            } else {
                log("permission", "denied");
            }
            this.jobLeave();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                if (!this.image.exists() || this.image.length() <= 0) {
                    log("camera", "no image");
                    MainActivity.this.jobLeave();
                    return;
                }
                log("camera", "image file size: " + String.valueOf(image.length() / 1024) + " KiB");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.processFile(MainActivity.this.image, ((EditText) findViewById(R.id.editServer)).getText().toString());
                        MainActivity.this.image.delete();
                        MainActivity.this.jobLeave();
                    }
                }).start();
            } else if (resultCode == RESULT_CANCELED) {
                log("camera", "canceled");
                MainActivity.this.jobLeave();
            } else {
                log("camera", "error, resultCode is " + String.valueOf(resultCode));
                MainActivity.this.jobLeave();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void processFile(File image, String text) {
        log("upload", "start");
        String result = null;
        try {
            result = HttpHelper.uploadFile(this, image, this.getBaseURL() + "/upload", "img");
        } catch (IOException e) {
            log("upload", "failed, " + e.getLocalizedMessage());
            return;
        }
        log("upload", "success");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);

        this.baseUrlFile = new File(getFilesDir(), "serverName.conf");

        this.viewFlipper = (ViewFlipper) findViewById(R.id.flipper);
        this.detector = new GestureDetector(this, this);

        try {
            FileInputStream fis = new FileInputStream(this.baseUrlFile);
            InputStreamReader isr = new InputStreamReader(fis, "utf-8");
            char[] a = new char[100];
            isr.read(a);
            fis.close();
            this.baseUrl = String.valueOf(a).trim();
        } catch (IOException e) {
            e.printStackTrace();
        }

        EditText edit = ((EditText) findViewById(R.id.editServer));
        edit.setText(this.baseUrl);
        edit.setSelection(edit.length());

        findViewById(R.id.button).setOnClickListener(new ButtonTestClick());
        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                MainActivity.this.L2.writeLock().lock();
                MainActivity.this.baseUrl = s.toString();
                MainActivity.this.L2.writeLock().unlock();
                try {
                    FileOutputStream fos = new FileOutputStream(MainActivity.this.baseUrlFile);
                    OutputStreamWriter osw = new OutputStreamWriter(fos, "utf-8");
                    osw.write(MainActivity.this.baseUrl);
                    osw.flush();
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        this.image = new File(getCacheDir(), "image.jpg");
        this.uri = FileProvider.getUriForFile(MainActivity.this, "com.example.lion.provider", image);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            log("permission", "required, grant later");
        } else {
            log("permission", "granted");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                MainActivity.this.uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.flipNext();
                    }
                });
            }
        }).start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return this.detector.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        if (this.currentPage != 0) return false;
        if (this.jobEnter()) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Grant permission\nandroid.permission.CAMERA", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        log("permission", "request {android.permission.CAMERA}");
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
                    }
                }).start();
            } else {
                log("camera", "open");
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, this.uri);
                this.image.delete();
                startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
            }
        }
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (velocityX < -5) {
            this.flipNext();
            return true;
        } else if (velocityX > 5) {
            this.flipPrevious();
            return true;
        }
        return false;
    }

    private void flipNext() {
        this.viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this,
                R.anim.right_in));
        this.viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this,
                R.anim.left_out));
        this.viewFlipper.showNext();
        this.currentPage = (++this.currentPage) ^ 2;
    }

    private void flipPrevious() {
        this.viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this,
                R.anim.left_in));
        this.viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this,
                R.anim.right_out));
        this.viewFlipper.showPrevious();
        this.currentPage = (--this.currentPage) ^ 2;
    }

    private class Test extends Thread {
        @Override
        public void run() {
            try {
                String s = HttpHelper.test(MainActivity.this, MainActivity.this.getBaseURL() + "/test");
                log("test", s);
            } catch (IOException e) {
                log("test", "failed, " + e.getLocalizedMessage());
            }
            MainActivity.this.uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.setProgressBar(false);
                    findViewById(R.id.button).setEnabled(true);
                }
            });
        }
    }

    private class ButtonTestClick implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            findViewById(R.id.button).setEnabled(false);
            MainActivity.this.setProgressBar(true);
            Test test = new Test();
            test.start();
        }
    }
}
