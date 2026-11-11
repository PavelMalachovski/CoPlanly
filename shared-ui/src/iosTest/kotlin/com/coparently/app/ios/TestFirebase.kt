package com.coparently.app.ios

/**
 * Firebase for `:shared-ui`'s iPhone tests: configured through [IosFirebase] for the emulators,
 * once per test process, because the SDK aborts the process on a second `configure` of the default
 * app — and more than one test needs it (`IosAuthErrorsTest` signs in on the emulator,
 * `IosKoinGraphTest` builds the graph `IosApp.start` builds).
 */
internal object TestFirebase {

    /** The emulator host Firebase was configured for, or null before the first call. */
    private var configuredHost: String? = null

    /**
     * Configures Firebase for the emulators on [host] unless this process already has, as
     * `IosApp.start` does with fake `demo-coplanly` options no production service accepts.
     *
     * @throws IllegalStateException when an earlier call named another host.
     */
    fun configureOnce(host: String) {
        val configured = configuredHost
        if (configured != null) {
            check(configured == host) { "Firebase is configured for $configured, not $host" }
            return
        }
        IosFirebase.configure(
            IosAppConfig(
                emulatorHost = host,
                versionName = "test",
                privacyPolicyUrl = null,
                termsUrl = null,
                supportEmail = null,
            )
        )
        configuredHost = host
    }
}
