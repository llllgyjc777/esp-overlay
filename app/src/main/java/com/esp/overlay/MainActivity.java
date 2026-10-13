package com.esp.overlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#101418"));
        int pad = (int) (getResources().getDisplayMetrics().density * 24);
        root.setPadding(pad, pad, pad, pad);

        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16);
        tv.setText("ESP 方框透视 覆盖层\n\n"
                + "1. 已 root 设备\n"
                + "2. 先在终端跑 run_esp.sh 产出数据\n"
                + "3. 点下面按钮授悬浮窗权限\n"
                + "4. 启动覆盖层，切回游戏");
        tv.setGravity(Gravity.CENTER);
        root.addView(tv);

        Button btnPerm = new Button(this);
        btnPerm.setText("① 授权悬浮窗");
        btnPerm.setOnClickListener(v -> requestOverlayPermission());
        root.addView(btnPerm);

        Button btnStart = new Button(this);
        btnStart.setText("② 启动覆盖层");
        btnStart.setOnClickListener(v -> startOverlay());
        root.addView(btnStart);

        Button btnStop = new Button(this);
        btnStop.setText("③ 停止覆盖层");
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            tv.setText("已停止");
        });
        root.addView(btnStop);

        setContentView(root);

        if (!Settings.canDrawOverlays(this)) {
            tv.append("\n\n[!] 尚未授权悬浮窗");
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        }
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        moveTaskToBack(true);
    }
}