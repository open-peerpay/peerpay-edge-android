# PeerPay Edge Android

PeerPay Edge Android 是 PeerPay 的安卓收款监听端。应用扫码绑定后会保存服务端地址和设备密钥，监听微信、支付宝收款通知，并把到账文本签名上报到 PeerPay 后端。

## 功能

- 扫描 PeerPay 管理台生成的设备配对二维码，不在 APK 内固定服务器地址。
- 支持同一台手机多次扫码，绑定多个微信或支付宝收款账号。
- 通过无障碍服务监听 `com.tencent.mm`、`com.eg.android.alipaygphone` 的通知事件。
- 同时提供系统通知读取服务作为补充，提高不同 ROM 上的通知捕获成功率。
- 前台服务常驻运行，每 60 秒向 `/api/android/heartbeat` 发送心跳。
- 到账通知上报到 `/api/android/notifications`，请求按服务端 HMAC-SHA256 规则签名。
- `minSdk 21`，尽量覆盖旧版 Android；Android 13+ 会请求通知权限。

## 服务端配对流程

1. 在 PeerPay 管理台创建收款账号。
2. 在管理台为该收款账号生成设备配对二维码。
3. 安卓端点击“扫码绑定账号”，扫描二维码。
4. 应用向二维码中的 `/api/android/enroll?token=...` 注册设备。
5. 注册成功后，本地保存：
   - `serverBaseUrl`
   - `deviceId`
   - `deviceSecret`
   - 已绑定账号列表

注册之后，通知上报和心跳都会带以下请求头：

```text
x-peerpay-device-id
x-peerpay-timestamp
x-peerpay-nonce
x-peerpay-signature
```

签名内容与服务端一致：

```text
HTTP_METHOD
URL_PATH
TIMESTAMP
NONCE
SHA256_BODY_HEX
```

## 手机端授权

首次绑定后，建议在首页依次完成：

1. 打开无障碍授权，启用“PeerPay 收款监听”。
2. 打开通知读取授权，启用“PeerPay 通知监听”。
3. 忽略电池优化。
4. 确认前台通知保持显示。

部分国产 ROM 还需要在系统管家里手动允许自启动、后台运行、锁屏运行。

## 构建

项目使用 Android Gradle Plugin，依赖 Maven Central 上的 ZXing 扫码库。为了兼容 Android 21-23，`zxing-android-embedded:4.3.0` 按官方旧版配置关闭传递依赖，并固定 `zxing:core` 为 `3.3.0`；同时显式引入 AndroidX Core，供扫码 Activity 运行时使用。

```bash
gradle :app:assembleDebug
```

当前仓库没有提交 Gradle Wrapper；如果本机没有 Gradle，可以先用 Android Studio 打开工程，或在本地生成 wrapper。

## 与服务端字段对应

通知上报请求体示例：

```json
{
  "deviceId": "android-xxxx",
  "paymentChannel": "alipay",
  "channel": "accessibility",
  "packageName": "com.eg.android.alipaygphone",
  "actualAmount": "10.00",
  "rawText": "支付宝到账 10.00 元"
}
```

服务端会按设备已绑定的收款账号、付款渠道和实付金额匹配 pending 订单，匹配成功后触发回调。
