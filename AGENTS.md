# Love Doves change contract

## Required checks

Run these commands from the repository root before handing off a change:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME=/Users/ayalpani/Library/Android/sdk ./gradlew testDebugUnitTest
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME=/Users/ayalpani/Library/Android/sdk ./gradlew assembleDebug
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME=/Users/ayalpani/Library/Android/sdk ./gradlew lintDebug
go test ./...
go vet ./...
```

Run Go commands from `relay/`. Use a disposable Android emulator for
instrumentation tests. Never run an uninstalling or app-data-clearing test task
on Arash's personal Galaxy A54.

libsignal's JVM classes require Java 21. On Arash's Mac, use the JDK bundled
with Android Studio as shown above. Android bytecode still targets Java 17.

## Security invariants

- Never persist message text, decoded photos, unencrypted videos, vault keys,
  capabilities, push tokens, or pairing payloads in logs, screenshots,
  temporary files, backups, crash reports, or fixtures.
- Keep Android backup disabled and keep sensitive screens behind `FLAG_SECURE`.
- The relay treats object bodies as opaque bytes and deletes acknowledged or
  expired objects. It never receives message or media keys.
- Do not invent cryptographic primitives. Use Android Keystore, AES-GCM,
  SQLCipher, and libsignal for their documented roles.
- Pairing, recovery, camera, storage, and background work require a real-device
  smoke test at the next available milestone.

## Product invariants

- One installation connects to exactly one partner.
- The app locks whenever it leaves the foreground.
- Notifications never contain a partner name, message preview, or media detail.
- Love Doves is warm and calm, not styled like an enterprise security vault.
- Keep onboarding guided and linear: welcome/name, choose inviter or invitee,
  then show only that role's next action. Never mix invite creation, QR scanning,
  link input, and the relay freischalt code on one screen.
- Prefer Lucide icons and a small number of focused screens.

## Spur reuse contract

- Before designing or implementing a Love Doves interaction that already exists in Spur,
  inspect the corresponding Spur source and port it as the baseline. Adapt only where the
  Love Doves product or security model genuinely differs; do not independently redesign it.
- Keep the Love Doves photo camera aligned with Spur's `CameraScreen`, `CameraChrome`, and
  `MediaConfirmationPanel`: system-bar insets, full-sensor rotation, matching CameraX
  preview/capture viewport, camera switching, capture controls, confirmation motion, and
  manual photo rotation. Tapping the live preview must set CameraX autofocus and exposure
  metering at that exact point and show brief visual feedback. Center the captured photo
  inside the space not occupied by the confirmation panel and keep pinch zoom, double-tap
  zoom, and panning active there. Love Doves must retain its stricter memory-only plaintext
  policy.
- Keep the full-screen photo view aligned with Spur's `PhotoDetail`: use Telephoto for native
  pinch zoom, double-tap zoom, and panning while keeping decrypted image data memory-only.
- Decode chat thumbnails away from the UI thread, cap them at 1024 pixels, and cache them only
  in RAM for the lifetime of the unlocked vault. Full-resolution decoding belongs only in the
  full-screen photo view, and every decoded cache entry must become unreachable on lock.
- Settings use exactly two text sizes: the page header and one shared content size. Express the
  hierarchy among section labels, setting names, and descriptions through weight, color, and
  spacing rather than additional font sizes.
- When video or emoji selection enters scope, start from Spur's `VideoCameraScreen` and
  `EmojiPicker` rather than creating parallel implementations.
- In the conversation, present emoji selection as a fixed bottom-attached keyboard panel, not
  a draggable sheet. Keep the composer stationary and give vertical scroll exclusively to the
  emoji grid. Keep eight compact, generously spaced emojis per row, place search above the grid,
  match the panel to the last measured system-keyboard height, and turn the composer toggle into
  a keyboard icon while the emoji panel is open. During keyboard/picker changes, grow one by the
  exact amount the other shrinks so the conversation does not jump. Preserve and advance the
  composer selection when inserting emojis, and fully close the IME before opening media capture.
- Keep video plaintext in anonymous RAM-backed file descriptors only. Strip container metadata,
  encrypt the video and its thumbnail separately, and enforce the shared 20 MiB object limit.
- Keep the red recording-stop square visibly inset inside the circular capture ring; it must
  never reach or protrude through that ring.
