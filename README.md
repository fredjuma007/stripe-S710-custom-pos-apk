# RafikiPay

RafikiPay is a branded Stripe Reader S710 donation kiosk for Rafiki Community CIC.

The repository contains:

- `app/`: Android Kotlin + Jetpack Compose APK for Stripe Apps on Devices.
- `backend/`: Vercel TypeScript API for Stripe Terminal connection tokens, PaymentIntents, webhook verification, and health checks.

## Android App

Open the repository in Android Studio and sync Gradle. The app uses:

- Application ID: `community.rafiki.pay`
- App label: `RafikiPay`
- Compile SDK: 35
- Stripe Terminal Android SDK: 5.6.0

Set Gradle properties before building:

```properties
RAFIKIPAY_BACKEND_URL=https://your-rafikipay-backend.vercel.app
RAFIKIPAY_DEVICE_TOKEN=replace-with-a-shared-device-token
RAFIKIPAY_TERMINAL_LOCATION_ID=tml_xxx
```

For release signing, also set:

```properties
RAFIKIPAY_KEYSTORE_FILE=C:/path/to/rafikipay-release.jks
RAFIKIPAY_KEYSTORE_PASSWORD=...
RAFIKIPAY_KEY_ALIAS=rafikipay
RAFIKIPAY_KEY_PASSWORD=...
```

Debug builds default to a simulated reader path. Release builds use Stripe Apps on Devices handoff mode.

Build and test on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The test APK is written to:

```text
app/build/outputs/apk/debug/RafikiPay-v1.0.0-test-debug.apk
```

After adding release signing properties, build the production APK with:

```powershell
.\gradlew.bat :app:assembleRelease
```

## Backend

```bash
cd backend
npm install
npm run typecheck
npm test
```

Required environment variables:

```bash
STRIPE_SECRET_KEY=sk_live_or_test_xxx
STRIPE_WEBHOOK_SECRET=whsec_xxx
RAFIKIPAY_DEVICE_TOKEN=replace-with-a-shared-device-token
DEFAULT_CURRENCY=gbp
```

Deploy the `backend/` directory to Vercel. Configure Stripe webhooks to post to:

```text
https://your-rafikipay-backend.vercel.app/api/stripe/webhook
```

## Client S710 Deployment

1. Enable Stripe Terminal and Apps on Devices on the client account.
2. Register the S710 to a Terminal Location.
3. Create a deploy group under Stripe Dashboard > Terminal > Software.
4. Upload the signed RafikiPay APK and select S700/S710 compatibility.
5. Deploy the approved app version and set RafikiPay as the preferred kiosk app.

