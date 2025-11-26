# Развертывание правил безопасности Firestore

## Проблема
Если вы видите ошибку "Permission denied" или "Missing or insufficient permissions", это означает, что правила безопасности Firestore не развернуты или не соответствуют требованиям.

## Решение

### Вариант 1: Через Firebase Console (рекомендуется)

1. Откройте Firebase Console:
   - https://console.firebase.google.com/project/coparently-a39c9/firestore/rules

2. Откройте файл `firestore.rules` в корне проекта

3. Скопируйте **весь** содержимое файла `firestore.rules`

4. Вставьте содержимое в редактор правил в Firebase Console

5. Нажмите кнопку **"Publish"** (Опубликовать)

6. Дождитесь подтверждения, что правила развернуты

### Вариант 2: Через Firebase CLI

Если у вас установлен Firebase CLI:

```bash
# Войдите в Firebase (если еще не вошли)
firebase login

# Убедитесь, что проект выбран
firebase use coparently-a39c9

# Разверните только правила
firebase deploy --only firestore:rules
```

## Проверка

После развертывания правил:

1. **Перезапустите приложение** (или очистите данные приложения)
2. Войдите в систему заново
3. Попробуйте выполнить операцию "co-parent pairing"

## Важные моменты

- Правила должны быть развернуты **перед** первым использованием приложения
- Если правила не развернуты, все операции с Firestore будут отклоняться с ошибкой `PERMISSION_DENIED`
- После изменения правил их нужно снова развернуть

## Структура правил

Текущие правила безопасности позволяют:

- ✅ Аутентифицированным пользователям читать/писать свой профиль
- ✅ Читать профиль партнера (после pairing)
- ✅ Создавать и читать приглашения для pairing
- ✅ Управлять событиями, детьми, расходами и другими данными

## Устранение неполадок

### Если ошибка "Permission denied" сохраняется:

1. **Сначала попробуйте упрощенные правила для тестирования:**
   - Используйте файл `firestore.rules.simple` (временно)
   - Это поможет определить, в правилах ли проблема или в чем-то другом
   - Разверните упрощенные правила и проверьте, работает ли приложение

2. **Проверьте аутентификацию:**
   - Убедитесь, что вы вошли в систему в приложении
   - Проверьте, что Firebase Authentication работает правильно
   - Попробуйте выйти и войти заново

3. **Проверьте логи:**
   - Откройте Logcat в Android Studio
   - Ищите логи с тегами: `PairingViewModel`, `FirestoreUserDataSource`, `UserRepository`
   - Найдите точное сообщение об ошибке

4. **Проверьте конфигурацию Firebase:**
   - Убедитесь, что файл `google-services.json` находится в папке `app/`
   - Проверьте, что project_id в `google-services.json` соответствует проекту Firebase

5. **Проверьте статус API:**
   - Убедитесь, что Firestore API включен: https://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=coparently-a39c9
   - API должен быть в статусе "Enabled"

6. **Очистите кэш приложения:**
   - Удалите приложение с устройства
   - Или очистите данные: Settings → Apps → CoParently → Storage → Clear Data
   - Переустановите и войдите заново

## Ссылки

- Firebase Console: https://console.firebase.google.com/project/coparently-a39c9
- Firestore Rules Editor: https://console.firebase.google.com/project/coparently-a39c9/firestore/rules
- Firestore API Status: https://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=coparently-a39c9

