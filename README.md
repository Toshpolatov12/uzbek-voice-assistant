# O'zbek Ovozli Yordamchisi (Uzbek Voice Assistant)

Android operatsion tizimi uchun **Kotlin** va **Jetpack Compose** (minSdk 26, targetSdk 34) da yozilgan to'liq funksional shaxsiy ovozli yordamchi ilova. Samsung Bixby yoki Google Assistant kabi tizim darajasida ishlaydi hamda sun'iy intellekt (Gemini Vision) orqali telefon ekranidagi tugmalarni inson kabi bosib, ilovalar va sozlamalarni mustaqil boshqara oladi.

---

## Asosiy Imkoniyatlar

1. **Tizim Darajasida Chaqirish (System-Wide Invocation)**:
   - Android'ning rasmiy `VoiceInteractionService` va `VoiceInteractionSessionService` API'laridan foydalanadi.
   - Bosh ekran (Home) tugmasini uzoq bosganda istalgan ilova ustidan ochiladi.
   - Bildirishnomalar panelida bir bosishda ishga tushirish uchun **Tezkor Sozlamalar Katakchasi (Quick Settings Tile)** mavjud.
2. **Qo'lsiz Ishlatish (Hands-Free Wake Word)**:
   - Ochiq manbali va shaxsiy server talab qilmaydigan **openWakeWord (TFLite)** modeli integratsiya qilingan.
   - Standart holatda o'chirilgan, Sozlamalardan yoqiladi.
   - Mikrofon orqali doimiy eshituvchi Foreground Service asosida ishlaydi.
3. **O'zbekcha Ovozli Kirish va Chiqish (STT / TTS)**:
   - Ovozni tanish (STT): `SpeechRecognizer` (`uz-UZ`), agar qurilmada o'zbek tili bo'lmasa, avtomatik ravishda rus (`ru-RU`) yoki turk (`tr-TR`) tillariga ogohlantirish bilan o'tadi.
   - Ovoz chiqarish (TTS): `TextToSpeech` (`uz-UZ`), aqlli zaxira (fallback) mexanizmi bilan.
4. **Tezkor Mahalliy Buyruqlar (AI talab qilinmaydi, lahzalik natija)**:
   - Hozirgi vaqt / soat ("soat necha", "vaqt qancha")
   - Chiroqni (fonar) yoqish va o'chirish ("fonarni yoq", "chiroqni o'chir")
   - Qo'ng'iroq qilish ("Aliga qo'ng'iroq qil", "Dadamga tel qil")
   - SMS yozish ("Karimga xabar yoz: Men yetib keldim")
   - Budilnik o'rnatish ("budilnikni 07:30 ga qo'y", "soat 8 da uyg'ot")
   - Ilovalarni nomi bo'yicha ochish ("Telegramni och", "YouTube ilovasini och")
   - Ovoz balandligini boshqarish ("ovozni balandlat", "ovozni pasaytir")
   - Wi-Fi va Bluetooth sozlamalarini ochish ("wifi ni och", "bluetooth ni och")
5. **Universal Ekran Agenti (AccessibilityService + Gemini Vision)**:
   - Mahalliy qoidalar qamrab ololmagan barcha murakkab buyruqlarda (masalan: *"qorong'i rejimni yoq"*, *"falonchi ilovaning bildirishnomasini o'chir"*, *"Telegramda yangi guruh och"*):
     - `ScreenAgentService` ekran daraxtini (Accessibility Node Hierarchy) o'qiydi va API 30+ da skrinshot oladi.
     - Google Gemini API (`gemini-2.5-flash` yoki `gemini-1.5-flash`) ga skrinshot va ixcham JSON ma'lumotlarini yuboradi.
     - Gemini yuborgan qat'iy JSON buyruqlar asosida ekranni bosadi (`dispatchGesture`), matn kiritadi (`ACTION_SET_TEXT`) yoki skroll qiladi (har bir vazifa uchun maksimal 15 qadam).
     - Har bir qadam foydalanuvchiga ovozli tushuntirib boriladi.
     - **Xavfsizlik kafolati**: Pul to'lash, shaxsiy ma'lumotlarni o'chirish yoki xavfsizlik sozlamalarini o'zgartirishdan oldin foydalanuvchidan ovozli *"Ha / Yo'q"* tasdig'ini so'raydi.
6. **Xavfsiz Sozlamalar**:
   - Gemini API kaliti `EncryptedSharedPreferences` yordamida shifrlangan holda saqlanadi.

---

## Loyihani Android Studio'da Ochish, Yig'ish va Ishga Tushirish

### Talablar:
- **Android Studio** (Hedgehog, Iguana, Jellyfish yoki undan yangi versiya).
- **JDK 17** (Android Studio ichiga o'rnatilgan JBR 17 tavsiya etiladi).
- **Android SDK 34** (Build-Tools 34.0.0, Platform 34).

### 1. Loyihani ochish
1. Android Studio'ni oching.
2. Bosh sahifada **Open** tugmasini bosing.
3. Ushbu papkani tanlang: `uzbek-voice-assistant`.
4. Gradle sinxronizatsiyasi (Sync) tugashini kuting.

### 2. Loyihani yig'ish (Build APK)
- Terminalda:
  ```bash
  # Windows PowerShell yoki CMD:
  .\gradlew.bat assembleDebug

  # Linux yoki macOS:
  ./gradlew assembleDebug
  ```
- Tayyor APK fayli quyidagi manzilda hosil bo'ladi:
  `app/build/outputs/apk/debug/app-debug.apk`

### 3. Haqiqiy telefonda sinash va o'rnatish
1. Android telefoningizda **Dasturchi sozlamalari (Developer Options)** va **USB orqali nosozliklarni tuzatish (USB Debugging)** ni yoqing.
2. Telefonni kompyuterga USB kabel orqali ulang.
3. Android Studio'ning yuqori panelida telefoningiz nomini tanlang va yashil **Run ▶** tugmasini bosing.
4. Yoki terminal orqali o'rnating:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Google AI Studio'dan Bepul Gemini API Kalitini Olish

Ekran agenti (Gemini Vision) ishlashi uchun bepul API kaliti kerak:
1. Brauzerda [Google AI Studio](https://aistudio.google.com/) saytiga kiring.
2. Google hisobingiz bilan tizimga kiring.
3. Chap menyudan **"Get API key"** tugmasini bosing.
4. Yangi loyiha tanlab, **"Create API key"** tugmasini bosing.
5. Hosil bo'lgan kalitni (masalan: `AIzaSy...`) nusxalab oling (bepul tarif oyiga yetarli so'rovlarni taqdim etadi, karta talab qilinmaydi).
6. Telefoningizda **O'zbek Ovozli Yordamchi** ilovasini oching, **"4. Gemini API Kaliti"** maydoniga joylang va **"Saqlash"** tugmasini bosing.

---

## Tizimning Standart Yordamchisi (Default Assistant) Qilib Sozlash

Home tugmasini uzoq bosganda ushbu yordamchi ochilishi uchun:
1. Ilovani oching va **"1. Standart Yordamchi qilish"** tugmasini bosing (bu to'g'ridan-to'g'ri `Settings.ACTION_VOICE_INPUT_SETTINGS` tizim ekranini ochadi).
2. Yoki telefon sozlamalaridan: **Sozlamalar** -> **Ilovalar** -> **Standart ilovalar** -> **Raqamli yordamchi ilovasi (Default digital assistant)** bo'limiga kiring.
3. Ro'yxatdan **"O'zbek Ovozli Yordamchi"** ilovasini tanlang va ruxsat bering.
4. Endi telefonning istalgan joyida turib Home tugmasini bosib tursangiz, pastdan o'zbekcha ovozli yordamchi oynasi ko'tariladi.

---

## Ekranni Boshqarish (Maxsus Imkoniyatlar Xizmati - Accessibility)ni Yoqish

Sun'iy intellekt ekranni ko'rishi va tugmalarni bosa olishi uchun:
1. Ilovani oching va **"2. Maxsus Imkoniyatlarni Yoqish"** tugmasini bosing.
2. Telefonning **Maxsus imkoniyatlar (Accessibility)** sozlamalari ochiladi.
3. O'rnatilgan xizmatlar (Downloaded apps / Installed services) ro'yxatidan **"O'zbek Yordamchi — Ekranni Boshqarish"** bandini toping.
4. Xizmatni yoqing va tizim so'ragan xavfsizlik ruxsatlarini tasdiqlang.

---

## openWakeWord O'zbekcha Modelini O'rgatish (Colab Training)

Ilovada qo'llanilgan `openWakeWord` butunlay ochiq manbali (Apache 2.0) bo'lib, internetga ulanmasdan to'g'ridan-to'g'ri telefon protsessorida (TFLite) ishlaydi. 

Dunyo bo'yicha tayyor modellarning deyarli barchasi faqat inglizcha so'zlarga (masalan: "Hey Siri", "Alexa") moslashgan. O'zbek tilidagi maxsus iborani (masalan: *"Salom Yordamchi"* yoki *"Hey O'zbek"*) yuqori aniqlikda tanish uchun:
1. openWakeWord loyihasining rasmiy [Google Colab Notebook](https://github.com/dscripka/openWakeWord) sahifasini oching.
2. Modelga o'rgatiladigan matn sifatida `Salom Yordamchi` iborasini kiriting.
3. Colab da sun'iy audio generatori (TTS) minglab turli ovozlarda ushbu iborani generatsiya qilib, modelni o'rgatadi.
4. Hosil bo'lgan `.tflite` faylini yuklab olib, loyihadagi quyidagi manzilga joylang:
   `app/src/main/assets/wakeword/uzbek_wakeword.tflite`
5. Loyihani qayta yig'ing (Rebuild).

---

## Fondagi Mikrofon Bildirishnomasi Haqida Muhim Eslatma (Android Privacy)

> **DIQQAT (Android OS Xavfsizlik Talabi)**:
> Agar sozlamalarda **"Doimiy Fon Mikrofoni (Wake Word)"** funksiyasini yoqsangiz, telefon bildirishnomalar panelida doimiy **"Ovozli yordamchi fon rejimida eshitmoqda"** xabari paydo bo'ladi.
> 
> Bu Google Android operatsion tizimining majburiy maxfiylik talabi (`foregroundServiceType="microphone"`) bo'lib, foydalanuvchidan yashirin tarzda mikrofonni yoqib qo'yishning oldini oladi. Ushbu bildirishnomani ilova dasturchisi yashira olmaydi va bu tizimning to'g'ri ishlayotganligidan dalolat beradi. Agar fonda doimiy eshitish kerak bo'lmasa, uni istalgan payt Sozlamalardan o'chirib qo'yishingiz mumkin.
