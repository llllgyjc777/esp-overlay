# 绘制端 App · 使用说明

> 把 esp_auto 产出的方框数据，画到游戏屏幕上的透明悬浮层。
> 本 App 自身**无 root**，不读内存、不注入、不 Hook —— 只画图。

---

## 一、三层结构（先看懂再动手）

```
┌─────────────────────────────────────────────┐
│  ① 数据端  esp_auto (root)                    │
│     扫内存 → 算方框 → 写 /data/local/tmp/     │
│                          esp_boxes.bin       │
└──────────────────┬──────────────────────────┘
                   │  ② 搬运  bridge.sh (root)
                   ▼
      /sdcard/Android/data/com.esp.overlay/files/
                          esp_boxes.bin
                   │
                   ▼
┌─────────────────────────────────────────────┐
│  ③ 绘制端  ESP Overlay App (无root)           │
│     每 16ms 读一次 bin → 全屏透明层画红框     │
└─────────────────────────────────────────────┘
```

**为什么要有搬运这一步**：App 没 root，读不到 `/data/local/tmp`。
`bridge.sh` 每 0.2 秒把 bin 复制到 App 私有目录，App 就能读了。

---

## 二、编译 APK（手机上用 GitHub Actions 云编译）

1. 把整个 `overlay/` 文件夹上传到你的 GitHub 仓库（`lllgyjc777/Operit` 或新建仓库）。
2. `.github/workflows/build.yml` 会自动触发编译。
3. 进仓库 → **Actions** 标签 → 等 `Build ESP Overlay APK` 跑完（约 5~8 分钟）。
4. 下载 artifact **`esp-overlay-debug-apk`**，里面是 `app-debug.apk`。
5. 手机安装。

> 也可以用 Android Studio 本地编译：打开 `overlay/` 目录，直接 Build APK。

---

## 三、跑起来（按顺序）

游戏开一局、进对局（等人物出现）后：

```sh
# 第 1 步：开数据端（终端 1，保持运行）
su -c "sh /sdcard/Download/PUBG_ESP_FINAL/run_esp.sh"

# 第 2 步：开搬运（终端 2，保持运行）
su -c "sh /sdcard/Download/PUBG_ESP_FINAL/bridge.sh"
```

然后：

1. 打开 **ESP Overlay** App
2. 点 **① 授权悬浮窗** → 在弹出的系统页面里打开开关
3. 切回游戏（App 会自动退到后台）
4. 点 **② 启动覆盖层** → 屏幕左上角出现 `ESP boxes=xx`
5. 回到游戏对局 → 红框出现在人物身上

停止：App 里点 **③ 停止覆盖层**，终端里 Ctrl+C。

---

## 四、出问题了怎么查

| 现象 | 原因 | 处理 |
|------|------|------|
| 左上角一直 `ESP: waiting data...` | bin 没搬过来 / 数据端没跑 | 确认 `run_esp.sh` 和 `bridge.sh` 都在跑 |
| 一个框都没有 | `cam_ok=0`（相机没锁上） | 重跑 `run_esp.sh`，等出现 `cam_ok=1` |
| 框位置偏 / 大小不对 | 手机分辨率与 `scrW/scrH` 不匹配 | 本 App 已按 view 尺寸自动缩放；若仍偏，检查是否开了游戏内分辨率缩放 |
| 框太多、重叠一堆 | esp_auto 筛选偏宽（已知问题） | 属正常，当前版本含少量非玩家物件 |
| App 启动就崩 | 未授悬浮窗权限 | 先点 ① 授权 |
| 通知栏没有常驻通知 | Android 13+ 未给通知权限 | 系统设置里给本 App 通知权限 |

---

## 五、参数速查

- 读循环：`16ms`（约 60fps）→ `OverlayService.java` 里 `handler.postDelayed(this, 16)`
- 红框线宽：`3f`　黄框（备用）：`1f`　文字：`28f`
- bin 首选路径：`/sdcard/Android/data/com.esp.overlay/files/esp_boxes.bin`
- bin 回退路径：`/data/local/tmp/esp_boxes.bin`
- 包格式：`ESPB` + 头 48 字节 + 最多 64 框 × 16 字节

---

## 六、重新编译要改什么

| 想改 | 改哪 |
|------|------|
| 框颜色 | `OverlayService.java` → `boxPaint.setColor(Color.RED)` |
| 线宽 | `OverlayService.java` → `setStrokeWidth(3f)` |
| 刷新率 | `OverlayService.java` → `postDelayed(this, 16)` 里的 `16` |
| 加血量/距离文字 | `OverlayService.java` → `onDraw` 里加 `canvas.drawText` |

---

**风险提示**：本 App 只读取并绘制方框，不触碰游戏进程。
但同机存在 TP 反作弊（libtersafe.so），是否额外检测「悬浮窗」行为**不做保证**，风险自担。
