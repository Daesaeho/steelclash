# Steelclash assistant workflow

User-authorized arrangement, 2026-10-11. Keep BodyHealth/LSO deferred and continue the combat/animation plan.

| Worker | Assigned work | Boundaries |
|---|---|---|
| Main Codex agent | Architecture, combat rules, integration, verification, Git commit/push | Owns final decisions and reviews every proposal. |
| Luna (`gpt-6-luna`) | Contained client/dev changes, test helpers, documentation | Native Codex sub-agent; assign explicit files and preserve existing changes. First task: background Minecraft capture. |
| Mercury 2.5 | Small pure-Java implementations, alternative interpolation solutions, focused independent code review | External API assistant; receives an explicit subtask and selected files, returns a proposal. No direct repository writes or command execution. |
| Google AI Studio | Occasional animation contact-sheet/screenshot second opinions | External API assistant; at most two selected PNGs and two local requests per UTC day. No routine code work or automated retry loops. |

The native sub-agent tool offers Codex models. Mercury and Google are separate API assistants, not native
Codex sub-agents. Their proposals do not replace deterministic tests, onion-skin measurements or visual review.
Only one worker owns a given source file at a time. Run the single Minecraft test client serially.

## Credentials

Run this in your own PowerShell window:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\dev\steelclash\scripts\setup-agent-keys.ps1
```

The local window masks both inputs and saves `INCEPTION_API_KEY` and `GEMINI_API_KEY` in your Windows **user
environment**, outside this repository. These are user environment values, not an encrypted credential vault.
Blank fields preserve existing keys. The setup makes no API requests. The helper prefers saved Windows user keys,
so a stale Codex process key does not override a newly entered key. Restart Codex for other processes to inherit
the environment. Never paste keys into chat, prompts, source files or logs.

An Inception key uses `https://api.inceptionlabs.ai/v1` and `mercury-2.5`, matching the
[official Inception example](https://www.inceptionlabs.ai/models). If the Mercury key came from another provider,
confirm that provider before changing the nonsecret endpoint/model in `scripts/agent-assist.json`.
Google defaults to `gemini-3.5-flash-lite`, a current multimodal model with a
[free tier](https://ai.google.dev/gemini-api/docs/pricing). Google now limits 2.5 access to existing users.
Actual free-tier availability and limits are determined by the user's
[AI Studio project quota](https://ai.google.dev/gemini-api/docs/rate-limits), not by the helper's local request cap.

Check key presence without printing values or sending a request:

```powershell
python C:\dev\steelclash\scripts\agent-assist.py mercury --status
python C:\dev\steelclash\scripts\agent-assist.py google --status
```

## Bounded external tasks

Write a small task file under the external audit directory, naming the desired behavior and relevant constraints.
Then explicitly select repository source files:

```powershell
python C:\dev\steelclash\scripts\agent-assist.py mercury --prompt-file C:\dev\steelclash-beta-audit\agent-task.md --file src/main/java/com/steelclash/core/WeaponRig.java
python C:\dev\steelclash\scripts\agent-assist.py google --prompt-file C:\dev\steelclash-beta-audit\visual-task.md --image C:\dev\steelclash-beta-audit\20261010\step1-broad\mob-handover-comparison.png
```

The helper reads no directory tree automatically. It limits text to 40,000 characters, output to 2,048 tokens
(Mercury) or 1,024 (Google), and images to two PNGs below 5 MB each. No tools, automatic edits, retries or fallback
providers are enabled. Stop on quota errors. Local caps are 12 Mercury / 2 Google attempts per UTC day and count
failed attempts too; they are not provider billing limits. Invoke serially so the local ledger stays consistent.
Proposals and the nonsecret ledger go under ignored `build/agent-assist/`.

The main agent reads the proposal, applies any justified change, and performs repository-required checks before
commit/push. Use Google for rendered screenshots rather than private source/configuration; Google's free-tier
[data handling](https://ai.google.dev/gemini-api/docs/pricing) differs from its paid tier.

## Background animation captures

Add `-PliveCaptureBackground` to an opt-in `runClient -PliveCapture=...` invocation, or `-PposeSheetBackground`
to a `runClient -PposeSheet=...` invocation. The diagnostic hides its
own GLFW window, disables focus-loss pausing and avoids mouse grabbing; ordinary gameplay launches retain their
existing behavior. Captures continue through normal KeyMapping and combat paths into the existing render target.
Runtime verification is pending the approval-review reset. A loading-window flash may still precede the first
client event; this is background rendering, not a headless graphics backend. The test client must be windowed;
both modes verify that GLFW actually hid it. A sheet run that does not quit restores visibility with focus-on-show
disabled for that call. Normal Gradle pose-sheet runs quit at completion.

Verification, 2026-10-11: both saved API keys return `STEELCLASH_API_OK`. The first Mercury attempt used an old
process key and received 401; the current saved user key passes. Google successfully reviews the mob comparison
image. Mercury completes a focused Java review with documented low reasoning; its conditional double-rotation
warning assumes a body-space input, while the current caller supplies a view-space rig and the grip invariants pass.
Do not apply assistant proposals automatically. Google's two-request local allowance was used for these tests.

Compile and 22 fresh focused rig tests pass. A hidden live locomotion replay produces 139 real screenshots at
50.08 ms median spacing; a hidden frozen sheet produces three PNGs. The first hidden attempt correctly rejected
fullscreen; only the disposable QA client's options were changed to windowed. The initial startup-window flash
still remains outside the verified scope. The earlier approval-review limit has reset; broader step1 checks and
commit/push are still pending.
