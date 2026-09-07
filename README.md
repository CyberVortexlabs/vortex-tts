# Vortex TTS

Vortex TTS یک اپلیکیشن Android با Kotlin و XML/ViewBinding است که مستقیماً از دستگاه کاربر به Gemini API متصل می‌شود و با مدل‌های Gemini 2.5 TTS خروجی صوتی WAV تولید می‌کند.

## مشخصات

- Package: `com.vortex.tts`
- Kotlin + Android XML + ViewBinding
- Minimum SDK 24
- Target SDK 34
- Compile SDK 35
- AGP 8.5.2
- Gradle 8.7
- Retrofit + OkHttp + Coroutines
- ذخیره کلید با `EncryptedSharedPreferences`
- بدون Backend یا سرور واسط
- خروجی سالم و واقعی WAV با PCM 16-bit / 24 kHz / Mono

## مدل‌های TTS

شناسه‌های واقعی استفاده‌شده در درخواست:

- `gemini-2.5-flash-preview-tts`
- `gemini-2.5-pro-preview-tts`

در صفحه کلید، پاسخ `GET /v1beta/models` بررسی و فقط همین دو مدل در صورت موجود بودن نمایش داده می‌شوند.

## API Key

کلید در صفحه اول وارد می‌شود و فقط با AndroidX Security در SharedPreferences رمزنگاری‌شده نگهداری می‌شود.

برای ساخت کلید می‌توانید از Google AI Studio استفاده کنید: https://aistudio.google.com/apikey

برای جلوگیری از قرار گرفتن کلید در query string، پروژه کلید را در هدر استاندارد `x-goog-api-key` می‌فرستد. بنابراین در کد Retrofit، URL شامل `?key=...` نیست؛ endpoint همان Gemini Generate Content API است.

کلید:

- hardcode نشده است
- در BuildConfig قرار نمی‌گیرد
- در Logcat چاپ نمی‌شود
- به Backend ارسال نمی‌شود
- در GitHub یا README قرار ندارد
- در لاگ URL قرار داده نمی‌شود

> نکته: نسخه پایدار فعلی `androidx.security:security-crypto:1.1.0` APIهای این artifact را deprecated اعلام کرده و مستندات جدید Android استفاده مستقیم از Android Keystore را پیشنهاد می‌کنند. با این حال این پروژه عمداً `EncryptedSharedPreferences` را نگه داشته است چون این storage به‌صورت صریح در مشخصات Vortex TTS خواسته شده بود. هیچ API قدیمی `MasterKeys` استفاده نشده و از `MasterKey.Builder` استفاده می‌شود.

## TTS request

ساختار درخواست مطابق Generate Content API است و فیلد ساختگی برای loudness یا MP3 به API اضافه نمی‌شود. `responseModalities` برابر `AUDIO` و `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName` استفاده می‌شود.

برای بلندتر شدن لحن، «بلند» به‌صورت دستور طبیعی داخل متن ورودی اعمال می‌شود، چون API فیلد مستقیم loudness در این مسیر ارائه نمی‌کند.

## صداها

UI صدای مستقیم زیر را ارائه می‌کند:

`Kore`, `Puck`, `Charon`, `Fenrir`, `Aoede`, `Leda`, `Orus`, `Zephyr`

حالت «خودکار» فعلاً به `Kore` fallback می‌کند تا درخواست همیشه یک voice معتبر داشته باشد؛ API برای «انتخاب خودکار voice» پارامتر مستقیمی در این endpoint ندارد.

## محدودیت متن

حداکثر متن ورودی در UI برابر 5000 کاراکتر است و `maxLength=5000` مانع ورود بیشتر می‌شود.

## خروجی صوتی

Gemini TTS در این مسیر PCM خام برمی‌گرداند. برنامه Base64 را decode می‌کند و با Header استاندارد RIFF/WAVE یک WAV معتبر می‌سازد.

- 24,000 Hz
- Mono
- 16-bit PCM

MP3 در این نسخه عمداً تولید نمی‌شود. Android framework به‌صورت عمومی encoder قابل اتکای MP3 برای MediaCodec/MediaMuxer ارائه نمی‌کند و تبدیل WAV به MP3 با تغییر پسوند غلط است. خروجی اصلی و سالم WAV است.

## ذخیره فایل

روی Android 10 و جدیدتر، از `MediaStore` و `RELATIVE_PATH` استفاده می‌شود:

`Downloads/VortexTTS`

نام فایل نمونه:

`VortexTTS_2026-09-07_123456.wav`

برای Android 9 و پایین‌تر، به دلیل Scoped Storage نبودن، دسترسی نوشتن legacy فقط با `WRITE_EXTERNAL_STORAGE` تا API 28 درخواست می‌شود.

## ساخت و اجرا

پروژه را در Android Studio باز کنید، Gradle Sync را انجام دهید و از `assembleDebug` یا `assembleRelease` استفاده کنید.

برای ساخت release:

```bash
./gradlew assembleRelease
```

خروجی:

`app/build/outputs/apk/release/app-release.apk`

## GitHub Actions

Workflow در `.github/workflows/build.yml`:

1. Repository را checkout می‌کند.
2. Java 17 را آماده می‌کند.
3. Android SDK را آماده می‌کند.
4. `./gradlew assembleRelease --stacktrace` اجرا می‌شود.
5. APK را با Artifact با نام `VortexTTS-release-apk` ذخیره می‌کند.

هیچ API Key برای build لازم نیست.

## ساختار

```text
VortexTTS/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
├── app/build.gradle.kts
├── app/proguard-rules.pro
├── app/src/main/AndroidManifest.xml
├── app/src/main/java/com/vortex/tts/
│   ├── MainActivity.kt
│   ├── ui/
│   ├── data/
│   ├── model/
│   └── audio/
├── app/src/main/res/
└── .github/workflows/build.yml
```

## فونت

پروژه سه فایل TTF واقعی با نام‌های `vazirmatn_*` را داخل `res/font` دارد تا پروژه در محیط آفلاین هم از نظر Resource Compile کامل باشد. در این محیط باینری رسمی Vazirmatn در دسترس نبود؛ بنابراین این فایل‌ها از Noto Sans Arabic با وزن‌های Regular/Medium/Bold تهیه شده‌اند و از نظر فنی فونت Vazirmatn نیستند. برای تطابق ۱۰۰٪ با طراحی در انتشار نهایی، همین سه فایل را با نسخه رسمی Vazirmatn جایگزین کنید. هیچ فایل placeholder یا خالی در پروژه استفاده نشده است.
