# Настройка Firebase для Android-приложения CoPlanly

Подробная инструкция по добавлению Android-приложения в Firebase Console и настройке проекта.

## Шаг 1: Вход в Firebase Console

1. Откройте [Firebase Console](https://console.firebase.google.com/)
2. Войдите в свой аккаунт Google
3. Выберите ваш проект (или создайте новый, если его нет)

## Шаг 2: Добавление Android-приложения

### 2.1. Откройте раздел "Add app"

1. В Firebase Console найдите кнопку **⚙️ (Settings)** → **Project settings** (или просто нажмите на **Project Overview** → **Add app**)
2. В разделе **Your apps** нажмите кнопку **Add app** (или **➕ Add another app**)
3. Выберите иконку **Android** (🟢 Android)

### 2.2. Заполните информацию о приложении

В форме добавления Android-приложения заполните следующие поля:

#### **Android package name** (обязательно)
```
com.coparently.app
```

⚠️ **ВАЖНО:** Package name должен точно совпадать с `applicationId` в `app/build.gradle.kts` и `namespace` в `AndroidManifest.xml`.

Проверка:
- В `app/build.gradle.kts`: `applicationId = "com.coparently.app"` ✅
- В `app/build.gradle.kts`: `namespace = "com.coparently.app"` ✅

#### **App nickname** (необязательно)
```
CoPlanly
```
или любое другое удобное имя для идентификации

#### **Debug signing certificate SHA-1** (необязательно, но рекомендуется)

Этот шаг нужен для работы Google Sign-In и других функций Firebase, требующих OAuth.

**Как получить SHA-1:**

**Способ 1: Через командную строку (рекомендуется)**

1. Откройте PowerShell или командную строку
2. Перейдите в директорию проекта:
   ```powershell
   cd C:\Git\CoPlanly
   ```
3. Выполните команду для получения debug keystore SHA-1:

**Windows (PowerShell):**
```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

**Windows (CMD):**
```cmd
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

**macOS/Linux:**
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

4. Найдите строку с **SHA1:** и скопируйте значение (например: `A1:B2:C3:D4:E5:F6:...`)
5. Вставьте это значение в поле **Debug signing certificate SHA-1** в Firebase Console

**Способ 2: Через Android Studio**

1. Откройте Android Studio
2. Откройте проект CoPlanly
3. В правой панели откройте **Gradle** → **CoPlanly** → **app** → **Tasks** → **android** → **signingReport**
4. Дважды кликните на `signingReport`
5. В нижней панели **Run** найдите значение **SHA1** и скопируйте его

**Если ключ не найден или ошибка:**

Если вы получили ошибку, что файл не найден, debug keystore будет создан автоматически при первой сборке приложения. Можно пропустить этот шаг и добавить SHA-1 позже.

#### **App Store ID** (необязательно)

Оставьте пустым (это для iOS).

### 2.3. Завершение добавления

1. Нажмите кнопку **Register app** (или **Add app**)

## Шаг 3: Скачивание и установка google-services.json

### 3.1. Скачивание файла

После регистрации приложения Firebase Console предложит скачать файл `google-services.json`.

1. **Вариант 1:** Нажмите кнопку **Download google-services.json**
2. **Вариант 2:** Если кнопка не появилась, перейдите:
   - **⚙️ Project settings** → вкладка **General** → раздел **Your apps** → найдите ваше Android-приложение
   - Нажмите **Download google-services.json**

### 3.2. Размещение файла в проекте

⚠️ **КРИТИЧЕСКИ ВАЖНО:** Файл должен быть размещен в правильной директории!

1. Скачанный файл должен называться `google-services.json`
2. Переместите файл в следующую директорию проекта:

```
CoPlanly/
└── app/
    └── google-services.json  ← Сюда!
```

**Полный путь (пример для Windows):**
```
C:\Git\CoPlanly\app\google-services.json
```

**Правильная структура:**
```
CoPlanly/
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json  ← Файл должен быть здесь
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           └── ...
├── build.gradle.kts
└── ...
```

### 3.3. Проверка размещения

Убедитесь, что:
- ✅ Файл `google-services.json` находится в папке `app/` (на том же уровне, что и `build.gradle.kts`)
- ✅ Файл называется именно `google-services.json` (не `google-services.json.txt` или что-то другое)
- ✅ Файл не находится в `app/src/` или других подпапках

## Шаг 4: Проверка конфигурации проекта

### 4.1. Проверка build.gradle.kts

Убедитесь, что плагин `google-services` применен в `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")  // ← Должен быть здесь
    kotlin("kapt")
}
```

✅ **Статус:** Плагин уже применен в проекте

### 4.2. Проверка корневого build.gradle.kts

Убедитесь, что плагин добавлен в корневой `build.gradle.kts`:

```kotlin
plugins {
    // ...
    id("com.google.gms.google-services") version "4.4.2" apply false  // ← Должен быть здесь
}
```

✅ **Статус:** Плагин уже добавлен в корневой файл

## Шаг 5: Синхронизация проекта

1. Откройте Android Studio
2. Если файл `google-services.json` был добавлен после открытия проекта:
   - Перейдите: **File** → **Sync Project with Gradle Files**
   - Или нажмите на уведомление **"Gradle files have changed"** → **Sync Now**
3. Дождитесь окончания синхронизации

## Шаг 6: Проверка работы Firebase

### 6.1. Проверка файла google-services.json

Откройте файл `app/google-services.json` и убедитесь, что он содержит:

```json
{
  "project_info": {
    "project_number": "...",
    "project_id": "...",
    "storage_bucket": "..."
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "...",
        "android_client_info": {
          "package_name": "com.coparently.app"  // ← Должно совпадать
        }
      },
      "oauth_client": [...],
      "api_key": [...],
      "services": {...}
    }
  ]
}
```

Проверьте, что `package_name` в JSON файле совпадает с `com.coparently.app`.

### 6.2. Проверка сборки проекта

Попробуйте собрать проект:

**Через Android Studio:**
1. **Build** → **Make Project** (или `Ctrl+F9`)
2. Проверьте, что сборка проходит без ошибок

**Через командную строку:**
```powershell
.\gradlew.bat build
```

Если сборка успешна, Firebase настроен правильно! ✅

## Дополнительные настройки (опционально)

### Настройка Google Sign-In (для работы аутентификации)

Если вы планируете использовать Google Sign-In:

1. В Firebase Console перейдите: **⚙️ Project settings** → вкладка **General**
2. В разделе **Your apps** найдите ваше Android-приложение
3. Прокрутите вниз до раздела **SHA certificate fingerprints**
4. Убедитесь, что добавлен SHA-1 (см. Шаг 2.2)
5. Для release сборки также добавьте release SHA-1:
   - Используйте release keystore вместо debug
   - Выполните ту же команду `keytool` с путем к вашему release keystore

### Настройка Firestore Database

1. В Firebase Console перейдите: **Build** → **Firestore Database**
2. Нажмите **Create database**
3. Выберите режим (рекомендуется **Start in test mode** для разработки)
4. Выберите регион
5. Нажмите **Enable**

### Настройка Firebase Authentication

1. В Firebase Console перейдите: **Build** → **Authentication**
2. Нажмите **Get started**
3. Во вкладке **Sign-in method** включите:
   - **Google** (для Google Sign-In)
   - **Email/Password** (если нужно)
   - Другие методы по необходимости

### Настройка Cloud Messaging (FCM)

1. В Firebase Console перейдите: **Build** → **Cloud Messaging**
2. Для Android FCM работает автоматически после добавления `google-services.json`
3. Для тестирования push-уведомлений используйте Firebase Console для отправки тестовых сообщений

## Решение проблем

### Ошибка: "File google-services.json is missing"

**Решение:**
1. Убедитесь, что файл находится в `app/google-services.json` (не в `app/src/`)
2. Убедитесь, что файл называется именно `google-services.json`
3. Синхронизируйте проект: **File** → **Sync Project with Gradle Files**

### Ошибка: "Package name mismatch"

**Решение:**
1. Проверьте, что `package_name` в `google-services.json` = `com.coparently.app`
2. Проверьте, что `applicationId` в `app/build.gradle.kts` = `com.coparently.app`
3. Проверьте, что `namespace` в `app/build.gradle.kts` = `com.coparently.app`

### Ошибка: "Plugin with id 'com.google.gms.google-services' not found"

**Решение:**
1. Убедитесь, что в корневом `build.gradle.kts` есть:
   ```kotlin
   id("com.google.gms.google-services") version "4.4.2" apply false
   ```
2. Синхронизируйте проект: **File** → **Sync Project with Gradle Files**

### Ошибка при сборке: Firebase классы не найдены

**Решение:**
1. Убедитесь, что добавлены зависимости Firebase в `app/build.gradle.kts`
2. Проверьте, что файл `google-services.json` правильно размещен
3. Очистите проект: **Build** → **Clean Project**
4. Пересоберите: **Build** → **Rebuild Project**

## Проверочный список

Перед запуском приложения убедитесь, что:

- ✅ Проект создан в Firebase Console
- ✅ Android-приложение добавлено с package name: `com.coparently.app`
- ✅ SHA-1 добавлен (для Google Sign-In)
- ✅ Файл `google-services.json` скачан
- ✅ Файл `google-services.json` размещен в `app/google-services.json`
- ✅ Плагин `google-services` применен в `app/build.gradle.kts`
- ✅ Плагин `google-services` добавлен в корневой `build.gradle.kts`
- ✅ Проект синхронизирован с Gradle
- ✅ Проект успешно собирается

## Полезные ссылки

- [Firebase Console](https://console.firebase.google.com/)
- [Официальная документация Firebase для Android](https://firebase.google.com/docs/android/setup)
- [Документация по google-services.json](https://firebase.google.com/docs/android/setup#add-config-file)

---

**Версия документа:** 1.0
**Последнее обновление:** 2024

