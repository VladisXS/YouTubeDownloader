# YouTube Downloader

<p align="center">
  <b>Android-застосунок для завантаження відео та аудіо з YouTube</b><br/>
  Kotlin · Jetpack Compose · Material 3 · yt-dlp · ffmpeg
</p>

---

## Про проєкт

Повноцінний Android-клієнт для завантаження відео з YouTube, написаний повністю
на Kotlin з використанням Jetpack Compose. Працює локально на пристрої — без
власного сервера та без хмарних сервісів.

**Автор:** Vladislav

---

## Скріншоти

> Космічна тема з анімованим зоряним полем, градієнтними картками та неоновим акцентом.

---

## Можливості

- **Відео + звук, тільки аудіо, відео без звуку** — три режими завантаження
- **Вибір якості** — від 144p до 4K (2160p), з оцінкою розміру файлу
- **Аудіоформати** — MP3 та M4A
- **Автоматичне злиття** DASH-потоків (відео + аудіо) через ffmpeg
- **Вхід у YouTube через WebView** — cookies розблоковують якість 1080p+ (без них YouTube віддає `HTTP 403` на високі DASH-стріми)
- **PO Token (BotGuard)** — власна реалізація генерації proof-of-origin токенів для обходу SABR-обмежень
- **Foreground Service** — завантаження продовжується у фоні з прогресом у нотифікації
- **Share Intent** — «Поділитися посиланням» з YouTube одразу відкриває застосунок
- **Космічний UI** — кастомна тема з анімованим зоряним полем, градієнтними картками та неоновим акцентом

---

## Технічний стек

| Шар | Технології |
| --- | --- |
| Мова | Kotlin 2.4 |
| UI | Jetpack Compose, Material 3, Material Icons Extended |
| Архітектура | MVVM (StateFlow), Repository |
| Асинхронність | Kotlin Coroutines, Flow |
| Завантаження | [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp + ffmpeg + aria2c) |
| Мережа | OkHttp |
| Серіалізація | kotlinx.serialization + org.json |
| Збірка | AGP 9.5, Gradle Version Catalog |
| Мінімальний SDK | API 26 (Android 8.0) |
| Target SDK | API 37 |

---

## Архітектура

```
com.vladisx.youtubedownloader
├── MainActivity.kt              — точка входу, edge-to-edge, share intent
├── YtDlApp.kt                   — Application, ініціалізація бінарників
│
├── data/
│   ├── YtDlpManager.kt          — обгортка над yt-dlp: fetchInfo + download
│   ├── CookieStore.kt           — збереження cookies WebView у Netscape-формат
│   ├── MediaInfo.kt             — модель метаданих відео та форматів
│   ├── DownloadRequest.kt       — параметри одного завдання завантаження
│   └── Progress.kt              — стани прогресу
│
├── download/
│   ├── DownloadService.kt       — Foreground Service (dataSync)
│   ├── DownloadRepository.kt    — єдине джерело істини для стану завантаження
│   └── Logger.kt
│
├── potoken/
│   ├── PoTokenWebView.kt        — офскрін WebView для BotGuard VM
│   ├── PoTokenManager.kt        — кеш сесій та per-video токенів
│   └── JavascriptUtil.kt
│
└── ui/
    ├── MainScreen.kt            — головний екран
    ├── LoginScreen.kt           — вхід у YouTube через WebView
    ├── MainViewModel.kt         — UiState + бізнес-логіка
    └── theme/
        ├── Theme.kt             — космічна колірна схема
        └── Cosmic.kt            — зоряне поле, градієнтні поверхні
```

### Потік даних

```
Користувач вставляє URL
        ↓
MainViewModel.onUrlChange → debounce 600ms → fetchInfo()
        ↓
YtDlpManager.fetchInfo() → yt-dlp --dump-single-json
        ↓
MediaInfo (список форматів) → UiState.qualities
        ↓
Вибір якості → DownloadRequest
        ↓
DownloadRepository.start() → DownloadService (foreground)
        ↓
yt-dlp -f <format> + ffmpeg merge → Download/YouTubeDownloader/
```

---

## Як зібрати

### Вимоги

- Android Studio (остання стабільна)
- JDK 17
- Android SDK 37

### Кроки

```bash
# 1. Клонувати
git clone https://github.com/<your-username>/YouTubeDownloader.git
cd YouTubeDownloader

# 2. Вказати шлях до SDK (або відкрити в Android Studio — створиться автоматично)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 3. Debug-збірка
./gradlew :app:assembleDebug

# 4. Встановити на пристрій
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK з'явиться у `app/build/outputs/apk/debug/app-debug.apk`.

> **Примітка:** APK важить ~230 MB, оскільки містить нативні бінарники
> yt-dlp + ffmpeg + aria2c під 4 ABI (arm64-v8a, armeabi-v7a, x86, x86_64).
> Для меншого розміру можна залишити лише `arm64-v8a` у `abiFilters`.

---

## Дозволи

| Дозвіл | Призначення |
| --- | --- |
| `INTERNET` | Запити до YouTube |
| `ACCESS_NETWORK_STATE` | Перевірка з'єднання |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Фонове завантаження |
| `POST_NOTIFICATIONS` | Прогрес у шторці |
| `WAKE_LOCK` | Не засинати під час завантаження |

Застосунок **не** збирає аналітику і **не** надсилає дані на сторонні сервери.
Єдине місце зберігання чутливих даних — cookie-файл у приватній папці застосунку.

---

## Обмеження

- Застосунок є **технічною демонстрацією** роботи з медіа-стрімами на Android і не призначений для публікації в Google Play.
- Без входу в акаунт максимальна якість часто обмежується 1080p; 4K може бути недоступним через PoToken-контроль.

---

## Проблеми з авторизацією

Один з найцікавіших інженерних викликів проєкту — **нестабільна доступність
високих роздільностей** (4K / 1440p). Симптом: те саме відео іноді качається в 4K,
а іноді 4K у списку форматів взагалі відсутнє.

Це не баг застосунку, а наслідок того, як YouTube зараз контролює доступ до стрімів.

### Чому так відбувається

| Фактор | Вплив |
| --- | --- |
| **PoToken / SABR** | YouTube з 2024 р. вимагає *proof of origin* токен для багатьох відео. Без нього високі роздільності замінюються на `SABR`-стріми, які yt-dlp не може завантажити. |
| **Вік і популярність відео** | Старіші та менш популярні відео частіше мають суворий PoToken-контроль. |
| **Content ID / музичні лейбли** | Відео з музикою великих лейблів майже завжди вимагають токен. |
| **IP-адреса** | Дата-центр / VPN IP отримують значно жорсткіші обмеження, ніж мобільний побутовий IP. |
| **Регіон** | Частина відео обмежена за країною — висока якість недоступна для окремих регіонів. |
| **`player_client`** | Різні клієнти (`android_vr`, `web_safari`, `ios`) повертають різні набори форматів для одного й того ж відео. |

### Як це вирішено в коді

**1. Вхід у акаунт через WebView** — [LoginScreen.kt](app/src/main/kotlin/com/vladisx/youtubedownloader/ui/LoginScreen.kt)

Користувач входить у YouTube у звичайному WebView. Після повернення на
`youtube.com` застосунок зчитує cookie-jar і передає його yt-dlp як `--cookies`.
Це знімає більшість обмежень, бо запити виглядають як авторизована сесія.

**2. CookieStore** — [CookieStore.kt](app/src/main/kotlin/com/vladisx/youtubedownloader/data/CookieStore.kt)

Конвертує cookies з `CookieManager` у стандартний **Netscape HTTP Cookie File** —
формат, який очікує yt-dlp. Файл лежить у приватній директорії застосунку
(`/data/data/<package>/files/`) і недоступний іншим застосункам завдяки
Android sandbox.

**3. PO Token (BotGuard)** — [potoken/](app/src/main/kotlin/com/vladisx/youtubedownloader/potoken/)

Власна реалізація генерації proof-of-origin токенів:

```
PoTokenWebView.create()
   ↓ завантаження po_token.html (BotGuard helpers)
   ↓ POST /api/jnn/v1/Create → VM program + interpreter
   ↓ запуск VM у сторінці → botguard response
   ↓ PoTokenManager кешує streaming + per-video токени
   ↓ --extractor-args youtube:po_token=web.gvs+<token>;visitor_data=<vd>
```

**4. Модель запиту** — [DownloadRequest.kt](app/src/main/kotlin/com/vladisx/youtubedownloader/data/DownloadRequest.kt)

Містить поля для стабілізації високих роздільностей:
- `sourceClients` — `player_client`, яким бралися метадані (щоб підписи DASH-URL збігалися)
- `availableHeights` — список доступних роздільностей для graceful degradation при `HTTP 403`
- `videoId` — для генерації per-video PO токена

### Що можна покращити далі

- [ ] Підключити `PoTokenManager` безпосередньо у `YtDlpManager.fetchInfo()` та `downloadBlocking()`
- [ ] Явний `player_client` із fallback-списком (`android_vr` → `web_safari` → `ios`)
- [ ] Розумний селектор форматів: сортування за `res` + `vcodec`, перевага `avc1` для сумісності з MP4
- [ ] Graceful degradation: при `403` на 4K автоматично пробувати 1440p → 1080p
- [ ] Заповнювати `videoId` у `MainViewModel.startDownload()` для per-video токенів

---

## Ліцензія

MIT License — див. [LICENSE](LICENSE).

Проєкт використовує сторонні open-source бібліотеки:
- [youtubedl-android](https://github.com/yausername/youtubedl-android) (Apache 2.0)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) (Unlicense)
- [FFmpeg](https://ffmpeg.org/) (LGPL/GPL)

