# ⚠️ СРОЧНО: Исправление ошибки PERMISSION_DENIED при записи пользователя

## Проблема
Приложение не может записать данные пользователя в Firestore из-за строгих правил безопасности.

## Быстрое решение

### Шаг 1: Используйте упрощенные правила (РЕКОМЕНДУЕТСЯ)

1. **Откройте файл `firestore.rules.simple`** в корне проекта
2. **Скопируйте ВСЕ содержимое** (см. ниже)
3. **Откройте Firebase Console:** https://console.firebase.google.com/project/coparently-a39c9/firestore/rules
4. **Вставьте содержимое** в редактор правил
5. **Нажмите "Publish"**
6. **Подождите подтверждения** (обычно несколько секунд)

### Упрощенные правила (для копирования):

```
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    // Simplified rules for debugging
    // TODO: Replace with production rules after testing

    // Allow authenticated users to read/write their own user document
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Allow authenticated users to create/read invitations
    match /invitations/{invitationId} {
      allow read, write: if request.auth != null;
    }

    // Deny everything else for now
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

### Шаг 2: Проверьте

1. **Полностью закройте приложение**
2. **Запустите приложение заново**
3. **Войдите в систему**
4. **Попробуйте выполнить операцию снова**

## Если упрощенные правила работают

После того, как убедитесь, что все работает с упрощенными правилами, можете вернуться к полным правилам из `firestore.rules`, которые я уже исправил.

## Альтернатива: Используйте исправленные полные правила

Если хотите сразу использовать полные правила:
1. Откройте файл `firestore.rules`
2. Скопируйте все содержимое
3. Вставьте в Firebase Console
4. Нажмите "Publish"

## Важно

**Правила должны быть развернуты в Firebase Console вручную!** Изменения в коде не применяются автоматически - нужно скопировать и опубликовать правила через веб-интерфейс.

