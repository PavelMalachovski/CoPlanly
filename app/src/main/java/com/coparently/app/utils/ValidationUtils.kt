package com.coparently.app.utils

import android.util.Patterns

/**
 * Результат валидации.
 *
 * @see ValidationUtils
 */
sealed class ValidationResult {
    /**
     * Валидация прошла успешно.
     */
    data object Success : ValidationResult()

    /**
     * Ошибка валидации с сообщением.
     *
     * @param message Сообщение об ошибке для отображения пользователю
     */
    data class Error(val message: String) : ValidationResult()
}

/**
 * Утилиты для валидации форм.
 * Содержит методы для проверки различных типов данных.
 *
 * Использование:
 * ```kotlin
 * val result = ValidationUtils.validateEmail(email)
 * when (result) {
 *     is ValidationResult.Success -> { /* продолжить */ }
 *     is ValidationResult.Error -> { /* показать ошибку */ }
 * }
 * ```
 */
object ValidationUtils {

    /**
     * Минимальная длина пароля.
     */
    private const val MIN_PASSWORD_LENGTH = 6

    /**
     * Минимальная длина имени.
     */
    private const val MIN_NAME_LENGTH = 2

    /**
     * Максимальная длина заголовка события.
     */
    private const val MAX_TITLE_LENGTH = 100

    /**
     * Максимальная длина описания.
     */
    private const val MAX_DESCRIPTION_LENGTH = 500

    /**
     * Валидирует email адрес.
     *
     * Проверяет:
     * - Не пустой
     * - Соответствует паттерну email
     *
     * @param email Email для проверки
     * @return ValidationResult.Success если валидация прошла успешно, иначе ValidationResult.Error
     */
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult.Error("Email cannot be empty")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                ValidationResult.Error("Invalid email format")
            email.length > 254 -> // RFC 5321
                ValidationResult.Error("Email is too long")
            else -> ValidationResult.Success
        }
    }

    /**
     * Валидирует пароль.
     *
     * Проверяет:
     * - Не пустой
     * - Минимальная длина
     *
     * @param password Пароль для проверки
     * @return ValidationResult
     */
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Error("Password cannot be empty")
            password.length < MIN_PASSWORD_LENGTH ->
                ValidationResult.Error("Password must be at least $MIN_PASSWORD_LENGTH characters")
            password.length > 128 ->
                ValidationResult.Error("Password is too long")
            else -> ValidationResult.Success
        }
    }

    /**
     * Валидирует имя (пользователя, ребенка и т.д.).
     *
     * Проверяет:
     * - Не пустое
     * - Минимальная длина
     * - Содержит только буквы, пробелы и дефисы
     *
     * @param name Имя для проверки
     * @param fieldName Название поля для сообщения об ошибке
     * @return ValidationResult
     */
    fun validateName(name: String, fieldName: String = "Name"): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult.Error("$fieldName cannot be empty")
            name.length < MIN_NAME_LENGTH ->
                ValidationResult.Error("$fieldName must be at least $MIN_NAME_LENGTH characters")
            name.length > 50 ->
                ValidationResult.Error("$fieldName is too long")
            !name.matches(Regex("^[a-zA-Zа-яА-ЯёЁ\\s'-]+$")) ->
                ValidationResult.Error("$fieldName contains invalid characters")
            else -> ValidationResult.Success
        }
    }

    /**
     * Валидирует заголовок события.
     *
     * Проверяет:
     * - Не пустой
     * - Максимальная длина
     *
     * @param title Заголовок для проверки
     * @return ValidationResult
     */
    fun validateEventTitle(title: String): ValidationResult {
        return when {
            title.isBlank() -> ValidationResult.Error("Title cannot be empty")
            title.length > MAX_TITLE_LENGTH ->
                ValidationResult.Error("Title is too long (max $MAX_TITLE_LENGTH characters)")
            else -> ValidationResult.Success
        }
    }

    /**
     * Валидирует описание.
     *
     * Проверяет:
     * - Максимальная длина (описание может быть пустым)
     *
     * @param description Описание для проверки
     * @return ValidationResult
     */
    fun validateDescription(description: String): ValidationResult {
        return when {
            description.length > MAX_DESCRIPTION_LENGTH ->
                ValidationResult.Error("Description is too long (max $MAX_DESCRIPTION_LENGTH characters)")
            else -> ValidationResult.Success
        }
    }

    /**
     * Валидирует номер телефона.
     *
     * Проверяет базовый формат номера телефона.
     *
     * @param phone Номер телефона для проверки
     * @return ValidationResult
     */
    fun validatePhone(phone: String): ValidationResult {
        if (phone.isBlank()) {
            return ValidationResult.Error("Phone number cannot be empty")
        }

        // Удаляем все не-цифры для проверки
        val digitsOnly = phone.replace(Regex("[^0-9]"), "")

        return when {
            digitsOnly.length < 10 ->
                ValidationResult.Error("Phone number is too short")
            digitsOnly.length > 15 ->
                ValidationResult.Error("Phone number is too long")
            !phone.matches(Regex("^[+]?[0-9\\s()-]+$")) ->
                ValidationResult.Error("Phone number contains invalid characters")
            else -> ValidationResult.Success
        }
    }

    /**
     * Валидирует URL.
     *
     * @param url URL для проверки
     * @return ValidationResult
     */
    fun validateUrl(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult.Error("URL cannot be empty")
        }

        return when {
            !Patterns.WEB_URL.matcher(url).matches() ->
                ValidationResult.Error("Invalid URL format")
            else -> ValidationResult.Success
        }
    }

    /**
     * Валидирует возраст.
     *
     * @param age Возраст для проверки
     * @param minAge Минимальный возраст (по умолчанию 0)
     * @param maxAge Максимальный возраст (по умолчанию 18)
     * @return ValidationResult
     */
    fun validateAge(age: Int, minAge: Int = 0, maxAge: Int = 18): ValidationResult {
        return when {
            age < minAge -> ValidationResult.Error("Age cannot be less than $minAge")
            age > maxAge -> ValidationResult.Error("Age cannot be greater than $maxAge")
            else -> ValidationResult.Success
        }
    }

    /**
     * Валидирует обязательное поле (не пустое).
     *
     * @param value Значение для проверки
     * @param fieldName Название поля для сообщения об ошибке
     * @return ValidationResult
     */
    fun validateRequired(value: String, fieldName: String = "Field"): ValidationResult {
        return when {
            value.isBlank() -> ValidationResult.Error("$fieldName is required")
            else -> ValidationResult.Success
        }
    }

    /**
     * Валидирует длину строки.
     *
     * @param value Значение для проверки
     * @param minLength Минимальная длина (включительно)
     * @param maxLength Максимальная длина (включительно)
     * @param fieldName Название поля для сообщения об ошибке
     * @return ValidationResult
     */
    fun validateLength(
        value: String,
        minLength: Int? = null,
        maxLength: Int? = null,
        fieldName: String = "Field"
    ): ValidationResult {
        return when {
            minLength != null && value.length < minLength ->
                ValidationResult.Error("$fieldName must be at least $minLength characters")
            maxLength != null && value.length > maxLength ->
                ValidationResult.Error("$fieldName must not exceed $maxLength characters")
            else -> ValidationResult.Success
        }
    }

    /**
     * Выполняет несколько валидаций и возвращает первую ошибку.
     *
     * Использование:
     * ```kotlin
     * val result = ValidationUtils.validateMultiple(
     *     validateEmail(email),
     *     validatePassword(password),
     *     validateName(name)
     * )
     * ```
     *
     * @param validations Массив результатов валидации
     * @return ValidationResult.Success если все валидации прошли успешно, иначе первая ValidationResult.Error
     */
    fun validateMultiple(vararg validations: ValidationResult): ValidationResult {
        return validations.firstOrNull { it is ValidationResult.Error } ?: ValidationResult.Success
    }

    /**
     * Расширение для String для быстрой валидации email.
     */
    fun String.isValidEmail(): Boolean {
        return validateEmail(this) is ValidationResult.Success
    }

    /**
     * Расширение для String для быстрой валидации номера телефона.
     */
    fun String.isValidPhone(): Boolean {
        return validatePhone(this) is ValidationResult.Success
    }
}

