# IME update repair in 1.8.8

Compose 1.10.1 converts one `TextEditingScope` block into individual `onEditCommand` calls in `LegacyPlatformTextInputServiceAdapter.skiko.kt`. A preedit update executes `CommitText("")` then `SetComposingText(remaining)`. The first callback exposes a cleared preedit and null composition to the application before the second callback restores it.

The upstream implementation now collects commands and calls `onEditCommand(commands)` once. Hermes backports that batching through the public `InterceptPlatformTextInput` API, scoped to its three macOS composers. No framework classes or native libraries are replaced. The native input method continues to own deletion; no pinyin is reconstructed in app code.

Upstream source examined:
- https://github.com/JetBrains/compose-multiplatform-core/blob/v1.10.1/compose/foundation/foundation/src/skikoMain/kotlin/androidx/compose/foundation/text/input/internal/LegacyPlatformTextInputServiceAdapter.skiko.kt
- https://github.com/JetBrains/compose-multiplatform-core/blob/568b08af72b029e2622dd47c727de50aa547a68b/compose/foundation/foundation/src/skikoMain/kotlin/androidx/compose/foundation/text/input/internal/LegacyPlatformTextInputServiceAdapter.skiko.kt

Verification distinguishes three things:
1. Batch semantics: one observable state per native edit, partial commits, explicit cancellation and aborted edits.
2. AWT integration: real Compose window, transient states checked during every input-method event; disable only batching to prove the regression check fails against 1.8.7 behavior.
3. Actual macOS input: opt-in Robot -> system Pinyin -> Cocoa -> AWT tests. WeType is a separate test and is explicitly skipped if macOS cannot select it. A passed system Pinyin test does not mean WeType passed.

The user reported that 1.8.6/1.8.7 did not fix whole-preedit deletion with WeType. CI macOS 15.7.9 has so far refused WeType selection with OSStatus -50 after verified WeType 2.2.3 installation. The batching defect is confirmed independently; equivalence to the user's exact failure remains unconfirmed until WeType can be exercised in a working session.

The Help > Input method diagnostics window is opt-in. It retains at most 500 metadata events in memory, only from its own window. It records lengths, ranges, key codes for editing keys, runtime versions and timing; no typed text. Nothing is uploaded. Copying requires a button click; closing removes the listener and discards the buffer.
