# Быстрое исправление ошибки Permission Denied

## Шаг 1: Используйте упрощенные правила (для тестирования)

Скопируйте содержимое файла `firestore.rules.simple` и разверните его:

1. Откройте: https://console.firebase.google.com/project/coparently-a39c9/firestore/rules
2. Откройте файл `firestore.rules.simple` в корне проекта
3. Скопируйте все содержимое
4. Вставьте в редактор правил в Firebase Console
5. Нажмите **"Publish"**

## Шаг 2: Проверьте, работает ли приложение

1. Перезапустите приложение (или очистите данные)
2. Войдите в систему
3. Попробуйте выполнить "co-parent pairing"

## Если упрощенные правила работают:

Это означает, что проблема в основных правилах. Вернитесь к основным правилам из `firestore.rules` и убедитесь, что они развернуты.

## Если упрощенные правила НЕ работают:

Проблема не в правилах. Проверьте:

1. **Аутентификация:**
   - Откройте Logcat в Android Studio
   - Найдите логи с `FirebaseAuth` или `FirebaseAuthService`
   - Убедитесь, что пользователь действительно аутентифицирован

2. **Firestore API:**
   - Проверьте статус API: https://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=coparently-a39c9
   - API должен быть "Enabled"

3. **База данных:**
   - Проверьте, что база данных создана: https://console.firebase.google.com/project/coparently-a39c9/firestore
   - Должна быть видна база данных "(default)"

## Проверка через Logcat

Откройте Logcat и найдите:
- `PairingViewModel` - для ошибок при pairing
- `FirestoreUserDataSource` - для ошибок чтения/записи пользователей
- `FirebaseAuthService` - для ошибок аутентификации

Скопируйте полное сообщение об ошибке - оно покажет точную причину.

