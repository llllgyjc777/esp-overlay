package com.esp.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 读 /data/local/tmp/esp_boxes.bin (ESPB 包), 在全屏透明悬浮窗上画方框。
 *
 * 注意: 应用自身无 root, 无法直接读 /data/local/tmp。
 * 解决: 由 root 侧 (run_esp.sh 或 Operit) 把 bin 复制到 app 可读位置:
 *       /sdcard/Android/data/com.esp.overlay/files/esp_boxes.bin
 * 本服务优先读该路径, 回退读 /data/local/tmp/esp_boxes.bin (若 app 被授予权限)。
 */
public class OverlayService extends Service {

    private static final String TAG = "EspOverlay";
    private static final String BIN_APP =
            "/sdcard/Android/data/com.esp.overlay/files/esp_boxes.bin";
    private static final String BIN_TMP = "/data/local/tmp/esp_boxes.bin";

    private WindowManager wm;
    private OverlayView view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running = true;

    // ESPB 包结构 (与 esp_auto.c 一致)
    private static final int MAX_BOX = 64;
    private static final int HDR = 48;                 // 4+4+4+4 + 6*4 + 4+4 = 48
    private static final int PKT = HDR + MAX_BOX * 16;

    private volatile int boxCount = 0;
    private volatile boolean camOk = false;
    private final float[] boxes = new float[MAX_BOX * 4];
    private volatile int scrW = 3200, scrH = 1440;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundInternal();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        view = new OverlayView(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        try {
            wm.addView(view, lp);
        } catch (Exception e) {
            Log.e(TAG, "addView fail", e);
        }
        handler.post(readLoop);
    }

    private void startForegroundInternal() {
        String chId = "esp_overlay";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        chId, "ESP Overlay", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
        }
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, chId)
                : new Notification.Builder(this);
        b.setContentTitle("ESP 覆盖层运行中")
         .setContentText("正在读取 box 数据并绘制")
         .setSmallIcon(android.R.drawable.ic_menu_view);
        startForeground(1, b.build());
    }

    private final Runnable readLoop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            readOnce();
            if (view != null) view.invalidate();
            handler.postDelayed(this, 16);   // ~60fps
        }
    };

    private void readOnce() {
        File f = new File(BIN_APP);
        if (!f.exists()) f = new File(BIN_TMP);
        if (!f.exists()) { camOk = false; return; }
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] buf = new byte[PKT];
            int n = raf.read(buf);
            if (n < HDR) return;
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[4];
            bb.get(magic);
            if (magic[0] != 'E' || magic[1] != 'S' || magic[2] != 'P' || magic[3] != 'B') return;
            int count = bb.getInt();
            int camok = bb.getInt();
            bb.getInt();                       // frame
            bb.position(16 + 24);              // skip camX..camR (6 floats)
            scrW = bb.getInt();
            scrH = bb.getInt();
            if (count < 0) count = 0;
            if (count > MAX_BOX) count = MAX_BOX;
            boxCount = count;
            camOk = (camok == 1);
            for (int i = 0; i < boxCount * 4; i++) {
                boxes[i] = bb.getFloat();
            }
        } catch (Exception e) {
            // 读失败(文件被 rename 原子替换时) → 跳过本帧
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (wm != null && view != null) {
            try { wm.removeView(view); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    /** 只负责画框的 View */
    private class OverlayView extends View {
        private final Paint boxPaint = new Paint();
        private final Paint fillPaint = new Paint();
        private final Paint txtPaint = new Paint();

        OverlayView(Context c) {
            super(c);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(3f);
            boxPaint.setColor(Color.RED);
            boxPaint.setAntiAlias(true);

            fillPaint.setStyle(Paint.Style.STROKE);
            fillPaint.setStrokeWidth(1f);
            fillPaint.setColor(Color.YELLOW);
            fillPaint.setAntiAlias(true);

            txtPaint.setColor(Color.GREEN);
            txtPaint.setTextSize(28f);
            txtPaint.setAntiAlias(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int vw = getWidth();
            int vh = getHeight();
            // 数据坐标基于 scrW x scrH, 换算到本 view
            float sx = (scrW > 0) ? (float) vw / scrW : 1f;
            float sy = (scrH > 0) ? (float) vh / scrH : 1f;

            if (!camOk) {
                canvas.drawText("ESP: waiting data...", 40, 80, txtPaint);
                return;
            }
            for (int i = 0; i < boxCount; i++) {
                float x = boxes[i * 4] * sx;
                float y = boxes[i * 4 + 1] * sy;
                float w = boxes[i * 4 + 2] * sx;
                float h = boxes[i * 4 + 3] * sy;
                if (w <= 1 || h <= 1) continue;
                canvas.drawRect(x, y, x + w, y + h, boxPaint);
            }
            canvas.drawText("ESP boxes=" + boxCount, 40, 80, txtPaint);
        }
    }
}