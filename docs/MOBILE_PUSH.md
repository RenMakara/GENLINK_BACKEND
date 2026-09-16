# Mobile push notifications

GenLink has two different push meanings:

- `PUSH` is the existing authenticated WebSocket delivery for a user who is online.
- `MOBILE_PUSH` is the offline/background channel. It uses Firebase Cloud Messaging (FCM) for
  Android and for iOS after the iOS app's APNs credentials are configured in Firebase.

The FCM provider is disabled by default. No Firebase service-account key belongs in this
repository. The backend uses Google Application Default Credentials (ADC), so production should
use a workload identity or a secret-mounted service-account file with
`GOOGLE_APPLICATION_CREDENTIALS`. A secret manager may inject
`FIREBASE_CREDENTIALS_BASE64` instead; never commit either credential value.

## Backend configuration

Set these only in the environment or secret manager:

```text
NOTIFICATION_MOBILE_PUSH_ENABLED=true
FIREBASE_PROJECT_ID=your-firebase-project-id
GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/genlink-firebase-admin.json
# Alternative when a secret manager injects one line instead of mounting a file:
FIREBASE_CREDENTIALS_BASE64=<base64-of-service-account-json>
```

The Firebase Admin Java SDK is only initialised when `NOTIFICATION_MOBILE_PUSH_ENABLED=true`.
When enabled, the Firebase project must have the FCM HTTP v1 API enabled. The iOS Firebase app
must also have a valid APNs authentication key/certificate configured in the Firebase console;
the backend does not hold direct Apple credentials.

## Device registration API

All routes require the authenticated mobile JWT:

```text
GET    /api/v1/profile/settings/notifications/devices
PUT    /api/v1/profile/settings/notifications/devices
DELETE /api/v1/profile/settings/notifications/devices/{deviceId}
```

Register or refresh a token after login and whenever the Flutter client reports a token refresh:

```json
{
  "platform": "ANDROID",
  "token": "<FCM registration token>"
}
```

`platform` is `ANDROID` or `IOS`. The response contains only `deviceId`, `platform`, `active`,
and `lastSeenAt`; the raw token is never returned. The backend stores a SHA-256 token hash for
idempotency and an AES-GCM protected token for provider delivery. A token is associated with one
account at a time, so registering it after a logout/login moves it away from the previous account.

Call the delete endpoint during logout when the installation should stop receiving notifications
for the current account. Re-registering the token later activates it again.

## Delivery flow

1. A domain module explicitly requests `MOBILE_PUSH` through `NotificationEmitter`; existing
   domain calls remain `IN_APP` only until they are deliberately opted in. Event RSVP is now the
   first selected trigger and requests both `IN_APP` and `MOBILE_PUSH` for the event organiser.
2. The after-commit listener writes a channel-specific row to `notification_outbox`.
3. The worker re-checks global/type/channel preferences and renders the user's bilingual template.
4. The mobile transport loads active devices and sends the same navigation-aware payload to each
   device through FCM.
5. FCM `UNREGISTERED` responses deactivate the device and finish that delivery without an endless
   retry. Other provider failures remain retryable through the existing bounded outbox backoff.
6. No active device is a successful no-op; the in-app notification history remains the source of
   truth and a later device registration receives future notifications.

The delivery queue is at-least-once. If one device succeeds and another has a transient failure,
a retry can deliver the successful device again. Clients should deduplicate by `deliveryId` or
`dedupeKey` when showing a notification.

FCM data keys currently include `deliveryId`, `type`, and, when present, `referenceType`,
`referenceId`, and `dedupeKey`. Flutter should use those values to open the relevant GenLink
screen, while the notification center remains authoritative.

## Client responsibilities

- Flutter Firebase Core/Messaging setup, initialization, permission request, token retrieval,
  authenticated registration, token-refresh registration, and logout deactivation are implemented
  in the mobile client.
- Flutter foreground display and background/terminated tap handling are implemented in
  `lib/features/notification/data/firebase_mobile_notification_service.dart`. Foreground FCM
  messages are shown through the local notification channel `genlink_mobile_push`; taps use
  `referenceType`/`referenceId` and `type` to route to an existing GenLink screen, with the
  notification center as the safe fallback.
- The Event RSVP path is the first end-to-end candidate for mobile push; the backend test and a
  real Firebase-configured Android smoke test must still confirm delivery in this environment.
- Keep the real Android/iOS device smoke test in release validation: authenticate, verify the
  `PUT /api/v1/profile/settings/notifications/devices` request, trigger a `MOBILE_PUSH`, and test
  foreground/background/terminated app states.
- Android 13+ notification permission and the default FCM notification channel are declared in
  the Android manifest. iOS still requires the APNs key/certificate in Firebase before expecting
  iOS background delivery; the iOS AppDelegate also wires the notification-center delegate and
  local-notification plugin registrant callback.

FCM/APNs delivery is supplemental. It does not replace `GET /api/v1/notifications` or the mobile
notification BFF as the durable read source.
