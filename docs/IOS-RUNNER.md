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
- the repository variable `IOS_RUNNER_READY` is `true` (step 8 below);
- the code is this repository's own: a push, or a pull request from a branch of this repository.

**That last condition is defence in depth, not the lock.** A self-hosted runner executes whatever
a job's workflow and build scripts say, on your machine, as the macOS user the runner runs as. The
repository is public, and a pull request runs the workflow file of its own merge commit — so a
fork can delete the `if:` on `shared-ios`, or add a job of its own with
`runs-on: [self-hosted, macOS, ARM64, coplanly]`, and either would run on the Mac. The `if:` only
stops a fork that left the workflow as it is.

**The boundary is a repository setting a fork cannot edit**: Settings → Actions → General →
"Approval for running fork pull request workflows from contributors", set to **"Require approval
for all external contributors"** (step 3). With it, no fork's workflow runs anywhere — on GitHub's
machines or on the Mac — until you approve that run. Approving is running: **never approve a fork
run whose diff touches `.github/`, a Gradle build file (`*.gradle.kts`, `gradle/`,
`settings.gradle.kts`, `gradle.properties`) or `tools/`**, because those decide what executes on
the Mac. Read the diff first; when in doubt, do not approve, and ask the contributor to split the
change or let a maintainer bring it in on a branch of this repository.

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
   needs Kotlin 2.5.0, which is not stable yet (ROADMAP MON-24). **Xcode 27.2 works in practice**:
   on 1 November 2026 it ran this job's two tasks on the owner's Mac — 818 tests, 0 failures, and
   the framework linked. The link prints eight `warning: could not open …/klib/cache/…-cache.a`
   lines about Kotlin/Native's prebuilt platform caches; they are harmless. So use the Mac's own
   Xcode and skip the rest of this paragraph. **Only if a later build fails on the newer Xcode**,
   install **Xcode 26.0.1 beside it** for the runner (from
   developer.apple.com/download/all, unpacked as `/Applications/Xcode-26.0.1.app`). Run the
   commands above once more against it, so its licence is accepted and its simulator runtime is
   installed. `sudo` does not pass your environment on, so the licence command calls that Xcode's
   own `xcodebuild` by its path; the others run as you and read the exported variable:

   ```bash
   export DEVELOPER_DIR=/Applications/Xcode-26.0.1.app/Contents/Developer
   sudo "$DEVELOPER_DIR/usr/bin/xcodebuild" -license accept
   xcodebuild -runFirstLaunch
   xcodebuild -downloadPlatform iOS
   xcrun simctl list devices available   # at least one iPhone must be listed
   ```

   Leave out the `xcode-select --switch` line this time: it would make Xcode 26 the Mac's default
   in place of Xcode 27.

   Then give the runner that Xcode alone: after step 5, add the line
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

   Steps 1 and 2 need an administrator, so do them from your own account. The cask installs the
   JDK for every account on the Mac.

3. **Require approval for every fork run — before the runner exists.** This is required, not
   optional, and it comes first because it is the only boundary a fork cannot edit (see "What runs
   on it"). On GitHub: the repository → Settings → Actions → General → "Approval for running fork
   pull request workflows from contributors" → **"Require approval for all external
   contributors"** (or the strictest option under its current name) → Save. The default for a
   public repository, "first-time contributors", lets anyone who has had one pull request merged
   run any workflow without asking, the Mac's included.

   From then on, a fork's run waits under the pull request's checks with **"Approve and run"**.
   Approve it only after reading the diff, and **never when it touches `.github/`, a Gradle build
   file (`*.gradle.kts`, `gradle/`, `settings.gradle.kts`, `gradle.properties`) or `tools/`**.

4. **Give the runner a macOS account of its own** (recommended). System Settings → Users & Groups
   → Add User: a **Standard** account, not an Administrator, for example `ci-runner`. Every job
   runs as the account the runner runs as, so under your own account a job could read your whole
   home folder — your files, your keychain once unlocked, your SSH keys and browser profiles. Under
   a standard account it reads that account's home and what the Mac lets every user read, and it
   cannot use `sudo`. Turn on automatic login for it (System Settings → Users & Groups →
   "Automatically log in as"), because the simulator needs a logged-in session (step 6). macOS
   offers no automatic login while FileVault is on; then log the account in by hand after each
   restart, and switch back to yours with fast user switching — its session keeps running.

   Log in as that account once and check that it sees a simulator:
   `xcrun simctl list devices available` (with `export DEVELOPER_DIR=…` first when the runner uses
   a second Xcode, step 1). Do steps 5 and 6 logged in as it, so the runner's folder is in its
   home.

   If you keep the runner under your own account instead, know what that means: a job that got
   past step 3 runs with everything you can reach.

5. **Register the runner, for this repository only.** On GitHub: the repository → Settings →
   Actions → Runners → **New self-hosted runner** → macOS, ARM64. Follow the download and
   `./config.sh` steps it shows, in a folder of its own (for example `~/actions-runner-coplanly`).
   When `config.sh` asks:
   - runner group: the default;
   - name: anything, for example `coplanly-mac`;
   - **additional labels: `coplanly`**. GitHub adds `self-hosted`, `macOS` and `ARM64` by itself.
   - work folder: the default `_work`.

   Register it from the **repository's** settings, not an organisation's, so no other repository
   can send it a job.

6. **Run it as a launch agent**, so it starts when the runner's account logs in and keeps running:

   ```bash
   cd ~/actions-runner-coplanly
   ./svc.sh install
   ./svc.sh start
   ./svc.sh status
   ```

   A launch agent runs in its account's login session, which is what the iOS simulator needs: it
   does not boot without one. Keep that account logged in, and stop the Mac from sleeping while it
   should take jobs (System Settings → Energy, "Prevent automatic sleeping when the display is
   off"). The runner page on GitHub should now show the runner as **Idle**.

7. **Make sure nothing else uses this runner.**
   - It is listed under this repository's Settings → Actions → Runners, and under no
     organisation's.
   - No other repository has a runner registered from this Mac with the label `coplanly`.
   - Settings → Actions → General still says "Require approval for all external contributors"
     (step 3). Check it again whenever the repository's settings change hands.

8. **Turn the job on.** Settings → Secrets and variables → Actions → **Variables** → New
   repository variable: name `IOS_RUNNER_READY`, value `true`.

   Until this variable exists the `shared-ios` job is **skipped**, so pull requests do not wait for
   a runner that is not there. Set it to anything else (or delete it) to skip the job again, for
   example while the Mac is away for a long time.

9. **Check that it fails loudly, not silently.** With the variable set:
   - Stop the runner (`./svc.sh stop`) and push a change under `shared/` to a pull request. The
     `shared-ios` job shows as **Queued** ("Waiting for a runner"), and the "CI report" job waits
     for it. Nothing turns green.
   - Start it again (`./svc.sh start`). The queued job is picked up within a minute and runs.

   A job nobody picks up is cancelled by GitHub after 24 hours and shows as cancelled, never as
   passed.

10. **Run it green once.** The pull request that added the `:shared` module (phase 1 of the
    iPhone work) merges only after "Shared tests (iOS simulator)" has passed on this runner at
    least once.

## Day to day

- **Disk.** The runner keeps `~/.gradle` and `~/.konan` (the Kotlin/Native compiler, a few GB)
  between runs, in the runner account's home, which is what makes a run take minutes rather than
  tens of minutes. The job also installs Android's command-line SDK tools there (Gradle
  configures the Android modules before it runs an iOS task, and they look for an SDK). Delete
  any of these if the disk fills; the next run downloads them again.
- **Updating Xcode.** After an update, run `xcodebuild -runFirstLaunch` again and check that
  `xcrun simctl list devices available` still lists an iPhone. An Xcode newer than the Kotlin
  version supports (see "Which Xcode" in step 1) stays beside the runner's, not in its place.
- **Updating the runner.** It updates itself. If GitHub shows it **Offline**, check
  `./svc.sh status` and the log in `~/Library/Logs/actions.runner.*`.
- **Removing it.** `./svc.sh stop`, `./svc.sh uninstall`, then remove it on the Runners page (or
  `./config.sh remove`). Delete the `IOS_RUNNER_READY` variable first, so pull requests stop
  queuing for it.
