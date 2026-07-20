# OAuth 2.0 Setup для CoPlanly - Навигация по документации

## 🚨 Частые проблемы

### Проблема 1: "App is currently being tested"

Если вы видите это сообщение, это означает, что приложение в режиме тестирования.

**⚡ Быстрое решение (2 минуты):**

1. Откройте: https://console.cloud.google.com/apis/credentials/consent?project=coparently-a39c9
2. Прокрутите до раздела **Test users**
3. Нажмите **+ ADD USERS**
4. Добавьте ваш email (тот, который используете для входа в Google)
5. Нажмите **ADD**

**Подробно:** [quick-fix-test-users.md](./quick-fix-test-users.md)

### Проблема 2: "Failed to get access token"

**⚡ Быстрое решение:**

Выполните пошаговую диагностику в документе: [troubleshoot-access-token.md](./troubleshoot-access-token.md)

**Основные проверки:**
1. ✅ Google Calendar API включена
2. ✅ OAuth Client ID настроен
3. ✅ `google-services.json` содержит `oauth_client`
4. ✅ Ваш email в Test users

---

## 📚 Документация по темам

### Для начала работы

1. **[google-oauth-setup.md](./google-oauth-setup.md)** - Полная пошаговая инструкция
   - Включение Google Calendar API
   - Создание OAuth Client ID
   - Настройка Firebase
   - Обновление google-services.json

2. **[google-oauth-quick-reference.md](./google-oauth-quick-reference.md)** - Краткая справка
   - Команды для получения SHA-1
   - Чек-лист настройки
   - Ключевые параметры проекта

3. **[current-sha1-info.md](./current-sha1-info.md)** - Текущие значения SHA-1
   - SHA-1 для debug версии уже готов
   - Готов к использованию

### Режим тестирования

4. **[google-oauth-testing-mode.md](./google-oauth-testing-mode.md)** - Подробно о режиме Testing
   - Что означает режим тестирования
   - Как добавить тестовых пользователей
   - Как опубликовать приложение для всех
   - Решение проблем

5. **[quick-fix-test-users.md](./quick-fix-test-users.md)** - Быстрое решение
   - 5-минутная инструкция
   - Добавление тестовых пользователей

6. **[troubleshoot-access-token.md](./troubleshoot-access-token.md)** - Решение проблемы с токеном
   - Пошаговая диагностика
   - Проверка всех настроек
   - Частые ошибки и решения
   - Чек-лист

---

## 🎯 Рекомендуемый порядок действий

### Для разработки (сейчас)

1. ✅ Прочитайте [quick-fix-test-users.md](./quick-fix-test-users.md)
2. ✅ Добавьте ваш email в Test users
3. ✅ Проверьте, что Google Calendar API включена
4. ✅ Создайте OAuth Client ID для Android
5. ✅ Обновите google-services.json

### Для production (позже)

1. Прочитайте [google-oauth-testing-mode.md](./google-oauth-testing-mode.md)
2. Добавьте Privacy Policy (если требуется)
3. Опубликуйте приложение через **PUBLISH APP**
4. Дождитесь верификации Google (1-7 дней)

---

## 📋 Чек-лист настройки

- [ ] Google Calendar API включена
- [ ] OAuth consent screen настроен
- [ ] Тестовые пользователи добавлены
- [ ] OAuth Client ID (Android) создан
- [ ] SHA-1 добавлен в OAuth Client ID
- [ ] SHA-1 добавлен в Firebase
- [ ] google-services.json обновлен
- [ ] Приложение пересобрано

---

## 🔑 Текущие значения

- **Project ID**: `coparently-a39c9`
- **Package name**: `com.coparently.app`
- **SHA-1 (Debug)**: `A4:61:51:71:EC:CD:1F:7C:69:51:17:A3:E8:9D:DE:26:CB:BD:8A:04`

---

## 🔗 Полезные ссылки

- [Google Cloud Console - Credentials](https://console.cloud.google.com/apis/credentials?project=coparently-a39c9)
- [OAuth Consent Screen](https://console.cloud.google.com/apis/credentials/consent?project=coparently-a39c9)
- [Firebase Console](https://console.firebase.google.com/project/coparently-a39c9)
- [Google Calendar API](https://console.cloud.google.com/apis/library/calendar-json.googleapis.com?project=coparently-a39c9)

---

## ❓ Нужна помощь?

1. Проверьте раздел "Решение проблем" в [google-oauth-setup.md](./google-oauth-setup.md)
2. Прочитайте [google-oauth-testing-mode.md](./google-oauth-testing-mode.md) для деталей о режиме тестирования
3. Убедитесь, что ваш email добавлен в Test users

