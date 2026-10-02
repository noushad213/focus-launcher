# Focus modes

## Behavior

- Focus offers an open-ended timer that counts up and can be stopped and saved, preserving the original flow.
- Focus starts a fixed 25-minute countdown. Up to three installed apps can be chosen before starting; the choices are saved for the next session.
- The sandbox countdown is shown in the middle of a circular progress ring.
- The active session and its end time are stored before the countdown appears. Leaving the screen or locking the phone does not end it. The Focus screen has no early-end control.
- During a session, Focus opens only the chosen apps from its session screen. Its accessibility service returns other foreground apps to that screen. Android system UI, Settings, and the default dialer remain available for notifications, device recovery, and emergency calls.
- When 25 minutes pass, the session is saved in Analysis and the guard stops. Expiry is checked on UI ticks and app switches; no exact alarm permission is needed.

## Platform boundary

This is a voluntary focus guard. An ordinary launcher and accessibility service cannot suspend packages, stop every launch path, prevent disabling accessibility, or provide a tamper-proof lock. Android's lock task allowlist requires device/profile owner management, and notifications in that mode have restricted actions. The UI must describe this limit honestly.

## Verification

- Unit test expiry and allowed-package rules.
- Build and run JVM tests.
- On device, verify notification shade, Settings, dialer, selected apps, blocked apps via notification/recents, screen lock, and automatic end after 25 minutes.
