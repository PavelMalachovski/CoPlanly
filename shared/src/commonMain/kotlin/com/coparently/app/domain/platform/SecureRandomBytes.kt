package com.coparently.app.domain.platform

/**
 * [size] bytes from the platform's cryptographically secure generator: `SecureRandom` on Android,
 * `SecRandomCopyBytes` on iOS.
 *
 * A pairing code is a bearer credential (CLAUDE.md item 37), so it is drawn from the source a
 * password would be, never from a seeded `kotlin.random.Random` whose next value can be predicted.
 */
expect fun secureRandomBytes(size: Int): ByteArray
