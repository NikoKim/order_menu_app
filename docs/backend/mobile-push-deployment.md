# Mobile push deployment

Chat and message-center notifications are persisted by the backend first, then sent to the configured mobile push gateway. The gateway must translate the request into an FCM notification payload (not data-only) so Android and iOS can show a system banner while the app is backgrounded or terminated.

## Backend

Set both variables in production:

```text
ORDER_MENU_PUSH_GATEWAY_URL=https://push.example.com/send
ORDER_MENU_PUSH_GATEWAY_TOKEN=<shared bearer token>
```

The gateway receives:

```json
{
  "deliveryId": "stable-uuid-for-this-delivery",
  "tokens": ["fcm-device-token"],
  "title": "消息标题",
  "content": "消息内容",
  "payload": "{\"module\":\"CHAT\"}"
}
```

The gateway should deduplicate retries by `deliveryId` (also supplied as the `Idempotency-Key` header), send `title` and `content` in FCM's `notification` section, and retain `payload` in the data section for routing after the user taps the banner. Without both backend variables, push dispatch is intentionally disabled while in-app WebSocket badges continue to work.

## Customer app

Supply the Firebase project values at build time:

```powershell
flutter build apk `
  --dart-define=CUSTOMER_FIREBASE_API_KEY=... `
  --dart-define=CUSTOMER_FIREBASE_APP_ID=... `
  --dart-define=CUSTOMER_FIREBASE_MESSAGING_SENDER_ID=... `
  --dart-define=CUSTOMER_FIREBASE_PROJECT_ID=... `
  --dart-define=CUSTOMER_FIREBASE_STORAGE_BUCKET=...
```

For iOS also provide `CUSTOMER_FIREBASE_IOS_BUNDLE_ID` and configure APNs in the Firebase project.

## Shop app

Use the corresponding shop variables:

```powershell
flutter build apk `
  --dart-define=SHOP_FIREBASE_API_KEY=... `
  --dart-define=SHOP_FIREBASE_APP_ID=... `
  --dart-define=SHOP_FIREBASE_MESSAGING_SENDER_ID=... `
  --dart-define=SHOP_FIREBASE_PROJECT_ID=... `
  --dart-define=SHOP_FIREBASE_STORAGE_BUCKET=...
```

For iOS also provide `SHOP_FIREBASE_IOS_BUNDLE_ID`. Validate notification permission, foreground badges, background banners, terminated-app banners, token rotation, logout, and account switching on physical devices before release.
