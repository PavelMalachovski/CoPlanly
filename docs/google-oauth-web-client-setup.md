# Настройка OAuth 2.0 Web Client ID для Credential Manager API

Эта инструкция поможет настроить **Web Client ID** для использования Credential Manager API в приложении CoPlanly.

## Важно: Разница между Android и Web Client ID

В приложении используются **ДВА** разных OAuth Client ID:

1. **Android Client ID** - для Google Sign-In SDK
   - Хранится в `google-services.json`
   - Используется старым `GoogleSignInService` (если используется)

2. **Web Client ID** - для Credential Manager API ⭐ **НЕОБХОДИМО**
   - Хранится в `strings.xml` как `default_web_client_id`
   - Используется новым `CredentialManagerService`
   - **Это тот, который вы настраиваете сейчас!**

## Шаг 1: Включение Google Calendar API

Если еще не включено:

1. Откройте [Google Cloud Console](https://console.cloud.google.com/)
2. Выберите проект **coparently-a39c9**
3. Перейдите в **APIs & Services** → **Library**
4. В поиске введите "Google Calendar API"
5. Нажмите на **Google Calendar API**
6. Нажмите кнопку **Enable** (Включить)
7. Дождитесь активации (обычно несколько секунд)

## Шаг 2: Создание OAuth 2.0 Client ID для Web приложения

### 2.1. Переход в OAuth настроек

1. В Google Cloud Console перейдите в **APIs & Services** → **Credentials**
2. Вверху страницы нажмите **+ CREATE CREDENTIALS** → **OAuth client ID**

### 2.2. Настройка OAuth consent screen (если еще не настроен)

Если вы видите предупреждение о необходимости настроить OAuth consent screen:

1. Нажмите **CONFIGURE CONSENT SCREEN**
2. Выберите **External** (внешний) и нажмите **CREATE**
3. Заполните обязательные поля:
   - **App name**: `CoPlanly`
   - **User support email**: ваш email
   - **Developer contact information**: ваш email
4. Нажмите **SAVE AND CONTINUE**
5. На шаге **Scopes** добавьте:
   - `https://www.googleapis.com/auth/calendar` (Google Calendar API)
6. Нажмите **SAVE AND CONTINUE**
7. На шаге **Test users** добавьте тестовые email адреса:
   - Добавьте свой email и email всех, кто будет тестировать приложение
   - Пример: `your-email@gmail.com`
8. Нажмите **SAVE AND CONTINUE**
9. Нажмите **BACK TO DASHBOARD**

**Примечание:** После настройки приложение будет в режиме **Testing**. Только добавленные тестовые пользователи смогут войти.

### 2.3. Создание OAuth Client ID для Web

**ВАЖНО:** Выберите тип **Web application**, НЕ Android!

1. Вернитесь в **APIs & Services** → **Credentials**
2. Нажмите **+ CREATE CREDENTIALS** → **OAuth client ID**
3. В поле **Application type** выберите: **Web application** ⚠️ **НЕ Android!**
4. Введите **Name** (имя): `CoPlanly Web Client` (или любое другое имя)
5. Поле **Authorized JavaScript origins** оставьте пустым (не требуется для мобильных приложений)
6. Поле **Authorized redirect URIs** оставьте пустым (не требуется для Credential Manager API)
7. Нажмите **CREATE**

### 2.4. Копирование Web Client ID

После создания вы увидите диалог с **Client ID** (например: `123456789-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com`)

**Важно:**
- Это **Web Client ID**, который отличается от Android Client ID
- Формат: `XXXX-XXXX.apps.googleusercontent.com`
- Скопируйте весь Client ID

## Шаг 3: Добавление Web Client ID в strings.xml

1. Откройте файл `app/src/main/res/values/strings.xml`
2. Найдите строку:
   ```xml
   <string name="default_web_client_id" translatable="false">YOUR_WEB_CLIENT_ID.apps.googleusercontent.com</string>
   ```
3. Замените `YOUR_WEB_CLIENT_ID.apps.googleusercontent.com` на ваш **Web Client ID**
   ```xml
   <string name="default_web_client_id" translatable="false">123456789-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com</string>
   ```
4. Сохраните файл

**Пример:**
```xml
<!-- До -->
<string name="default_web_client_id" translatable="false">YOUR_WEB_CLIENT_ID.apps.googleusercontent.com</string>

<!-- После -->
<string name="default_web_client_id" translatable="false">123456789-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com</string>
```

## Шаг 4: Проверка настройки

1. Убедитесь, что в `strings.xml`:
   - ✅ `default_web_client_id` не содержит `YOUR_WEB_CLIENT_ID`
   - ✅ Значение имеет формат `XXXX-XXXX.apps.googleusercontent.com`
   - ✅ Атрибут `translatable="false"` присутствует

2. Пересоберите приложение:
   ```bash
   ./gradlew clean assembleDebug
   ```

3. Запустите приложение и попробуйте войти через Google:
   - Settings → Google Calendar Sync → Sign in with Google
   - Если все настроено правильно, появится диалог выбора аккаунта
   - После входа вы увидите: "Signed in to Google"

## Решение проблем

### Проблема: "Google OAuth not configured"

**Причина:** Web Client ID не настроен или содержит placeholder.

**Решение:**
1. ✅ Проверьте, что в `strings.xml` значение `default_web_client_id` заменено на реальный Client ID
2. ✅ Убедитесь, что это **Web Client ID**, а не Android Client ID
3. ✅ Пересоберите приложение после изменения

### Проблема: "Sign-in failed" или "Authentication failed"

**Причины:**
1. OAuth consent screen не настроен
2. Google Calendar API не включена
3. Ваш email не добавлен в Test users

**Решение:**
1. ✅ Проверьте OAuth consent screen (см. шаг 2.2)
2. ✅ Убедитесь, что Google Calendar API включена (см. шаг 1)
3. ✅ Добавьте свой email в Test users в OAuth consent screen

### Проблема: "Failed to get Calendar access"

**Причина:** Scope для Google Calendar не добавлен или не предоставлен пользователем.

**Решение:**
1. ✅ Убедитесь, что scope `https://www.googleapis.com/auth/calendar` добавлен в OAuth consent screen
2. ✅ При входе предоставьте разрешение на доступ к Google Calendar

### Проблема: "App is currently being tested"

**Причина:** Приложение в режиме Testing, и ваш email не добавлен в Test users.

**Решение:**
1. ✅ Добавьте свой email в **Test users** в OAuth consent screen
2. ✅ Используйте тот же Google аккаунт, который добавлен в Test users
3. ✅ Подождите несколько минут после добавления

## Проверочный чек-лист

Перед тестированием убедитесь, что:

- [ ] Google Calendar API включена в Google Cloud Console
- [ ] OAuth consent screen настроен
- [ ] Scope `https://www.googleapis.com/auth/calendar` добавлен в OAuth consent screen
- [ ] Ваш email добавлен в Test users
- [ ] OAuth 2.0 Client ID для **Web application** создан
- [ ] Web Client ID добавлен в `strings.xml` как `default_web_client_id`
- [ ] Значение в `strings.xml` НЕ содержит `YOUR_WEB_CLIENT_ID`
- [ ] Приложение пересобрано после изменений

## Дополнительные ресурсы

- [Google Cloud Console - Credentials](https://console.cloud.google.com/apis/credentials)
- [Credential Manager Guide](https://developer.android.com/training/sign-in/credential-manager)
- [Google Calendar API Documentation](https://developers.google.com/calendar/api)
- [OAuth 2.0 для мобильных приложений](https://developers.google.com/identity/protocols/oauth2/native-app)

## Отличие от Android Client ID

| Параметр | Android Client ID | Web Client ID |
|----------|-------------------|---------------|
| **Где создается** | Google Cloud Console → OAuth Client ID → Android | Google Cloud Console → OAuth Client ID → Web |
| **Где хранится** | `google-services.json` (автоматически) | `strings.xml` (вручную) |
| **Для чего используется** | Google Sign-In SDK (старый API) | Credential Manager API (новый API) |
| **Нужен ли SHA-1** | Да | Нет |
| **Формат** | `XXXX-XXXX.apps.googleusercontent.com` | `XXXX-XXXX.apps.googleusercontent.com` |
| **Тип** | Android application | Web application |

**Важно:** Оба Client ID могут иметь одинаковый формат, но они **разные** и используются для разных целей.

