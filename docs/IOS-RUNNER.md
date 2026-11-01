# The Mac runner for iOS CI

The iPhone work (MON-24, `docs/superpowers/specs/2026-10-27-iphone-app-design.md` §9.3) builds and
tests on the owner's Mac, registered with GitHub as a **self-hosted runner**. Linux jobs stay on
GitHub's hosted runners. Everything on this page is the owner's to do (💻): a cloud session cannot
reach the Mac.

## What runs on it

One job in `.github/workflows/ci.yml`, `shared-ios` ("Shared — iOS simulator tests and framework
(Mac)"):

```bash
./gradlew :shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64
```

It runs the shared module's `commonTest` on an iOS simulator and links the `Shared` framework the
iPhone app will use. Its results appear as the check run **"Shared tests (iOS simulator)"** and in
the "CI summary" comment.

The job asks for a runner with the labels `self-hosted`, `macOS`, `ARM64` and `coplanly`. It runs
only when all three of these are true:

- the pull request changes `shared/`, the build files or the workflow (`tools/ci-changes.js`, the
  `shared` output), or it is a push to `main`;
- the repository variable `IOS_RUNNER_READY` is `true` (step 6 below);
- the code is this repository's own: a push, or a pull request from a branch of this repository.
  **A pull request from a fork never runs on the Mac.** A self-hosted runner executes whatever the
  pull request's workflow and build scripts say, as your user, on your machine.

## Setting it up

1. **Xcode.** Install it from the App Store, open it once, and let it install its components. Then,
   in Terminal:

   ```bash
   sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer
   sudo xcodebuild -license accept
   xcodebuild -runFirstLaunch
   xcodebuild -downloadPlatform iOS   # the iOS simulator runtime
   xcrun simctl list devices available  # at least one iPhone must be listed
   ```

   Kotlin picks the simulator from what Xcode lists, so there is no device name to configure.

   **Which Xcode.** Kotlin/Native builds against one Xcode and supports a range up to it. The
   project's Kotlin 2.3.21 supports Xcode up to **26.0**; Kotlin 2.4.21 reaches 26.4, and Xcode 27
   needs Kotlin 2.5.0, which is not stable yet (ROADMAP MON-24). If the Mac has Xcode 27 for your
   own work, keep it, and install **Xcode 26.0.1 beside it** for the runner (from
   developer.apple.com/download/all, unpacked as `/Applications/Xcode-26.0.1.app`). Run the four
   commands above once with `DEVELOPER_DIR` pointing at it, so its simulator runtime is installed:

   ```bash
   export DEVELOPER_DIR=/Applications/Xcode-26.0.1.app/Contents/Developer
   sudo xcodebuild -license accept && xcodebuild -runFirstLaunch && xcodebuild -downloadPlatform iOS
   ```

   Then give the runner that Xcode alone: after step 3, add the line
   `DEVELOPER_DIR=/Applications/Xcode-26.0.1.app/Contents/Developer` to the `.env` file in the
   runner's folder (`~/actions-runner-coplanly/.env`) and restart it (`./svc.sh stop`,
   `./svc.sh start`). The runner passes that file's variables to every job, so `xcode-select` can
   keep pointing at Xcode 27 for everything else. When the project moves to a Kotlin that supports
   a newer Xcode, change this one line.

2. **A JDK 17.** The job installs its own Temurin 17 through `actions/setup-java`, but Gradle
   from Terminal (for trying a command by hand) needs one too:

   ```bash
   brew install --cask temurin@17
   /usr/libexec/java_home -v 17   # prints its path
   ```

3. **Register the runner, for this repository only.** On GitHub: the repository → Settings →
   Actions → Runners → **New self-hosted runner** → macOS, ARM64. Follow the download and
   `./config.sh` steps it shows, in a folder of its own (for example `~/actions-runner-coplanly`).
   When `config.sh` asks:
   - runner group: the default;
   - name: anything, for example `coplanly-mac`;
   - **additional labels: `coplanly`**. GitHub adds `self-hosted`, `macOS` and `ARM64` by itself.
   - work folder: the default `_work`.

   Register it from the **repository's** settings, not an organisation's, so no other repository
   can send it a job.

4. **Run it as a launch agent**, so it starts when you log in and keeps running:

   ```bash
   cd ~/actions-runner-coplanly
   ./svc.sh install
   ./svc.sh start
   ./svc.sh status
   ```

   A launch agent runs in your login session, which is what the iOS simulator needs: it does not
   boot without one. Keep the user logged in, and stop the Mac from sleeping while it should take
   jobs (System Settings → Energy, "Prevent automatic sleeping when the display is off"). The
   runner page on GitHub should now show the runner as **Idle**.

5. **Make sure nothing else uses this runner.**
   - It is listed under this repository's Settings → Actions → Runners, and under no
     organisation's.
   - No other repository has a runner registered from this Mac with the label `coplanly`.
   - Settings → Actions → General → the approval setting for fork pull request workflows: choose
     the strictest option ("Require approval for all outside collaborators", or its current
     name). The job already skips fork pull requests; this is a second lock on the same door.

6. **Turn the job on.** Settings → Secrets and variables → Actions → **Variables** → New
   repository variable: name `IOS_RUNNER_READY`, value `true`.

   Until this variable exists the `shared-ios` job is **skipped**, so pull requests do not wait for
   a runner that is not there. Set it to anything else (or delete it) to skip the job again, for
   example while the Mac is away for a long time.

7. **Check that it fails loudly, not silently.** With the variable set:
   - Stop the runner (`./svc.sh stop`) and push a change under `shared/` to a pull request. The
     `shared-ios` job shows as **Queued** ("Waiting for a runner"), and the "CI report" job waits
     for it. Nothing turns green.
   - Start it again (`./svc.sh start`). The queued job is picked up within a minute and runs.

   A job nobody picks up is cancelled by GitHub after 24 hours and shows as cancelled, never as
   passed.

8. **Run it green once.** The pull request that added the `:shared` module (phase 1 of the iPhone
   work) merges only after "Shared tests (iOS simulator)" has passed on this runner at least once.

## Day to day

- **Disk.** The runner keeps `~/.gradle` and `~/.konan` (the Kotlin/Native compiler, a few GB)
  between runs, which is what makes a run take minutes rather than tens of minutes. The job also
  installs Android's command-line SDK tools in your home directory (Gradle configures the Android
  modules before it runs an iOS task, and they look for an SDK). Delete any of these if the disk
  fills; the next run downloads them again.
- **Updating Xcode.** After an update, run `xcodebuild -runFirstLaunch` again and check that
  `xcrun simctl list devices available` still lists an iPhone. An Xcode newer than the Kotlin
  version supports (see "Which Xcode" in step 1) stays beside the runner's, not in its place.
- **Updating the runner.** It updates itself. If GitHub shows it **Offline**, check
  `./svc.sh status` and the log in `~/Library/Logs/actions.runner.*`.
- **Removing it.** `./svc.sh stop`, `./svc.sh uninstall`, then remove it on the Runners page (or
  `./config.sh remove`). Delete the `IOS_RUNNER_READY` variable first, so pull requests stop
  queuing for it.
