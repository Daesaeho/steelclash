# Steelclash assistant workflow

Keep BodyHealth/LSO deferred and continue the combat and animation plan.

| Worker | Use | Boundaries |
|---|---|---|
| Main Codex agent | Architecture, combat rules, integration, verification and Git decisions | Owns final decisions and reviews every proposal. |
| Luna (`gpt-6-luna`) | Contained client/dev changes, test helpers and documentation | Native Codex sub-agent. Assign explicit files and preserve existing changes. |
| Mercury 2.5 | Small pure-Java implementations, interpolation alternatives and focused code review | External API assistant. Give a bounded subtask and selected files; it does not write to the repository or run commands. |
| Google AI Studio | Occasional animation screenshot second opinions | External API assistant. Send at most two selected PNGs per request and make at most two local requests per UTC day. No routine code work or automatic retries. |

Native Codex sub-agents are distinct from the Mercury and Google API assistants. Their proposals do not replace deterministic tests, onion-skin measurements or visual review. Assign each source file to one worker at a time, and run the single Minecraft test client serially.

## Credentials

Run this in your own PowerShell window:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\dev\steelclash\scripts\setup-agent-keys.ps1
```

The local window masks both inputs and saves `INCEPTION_API_KEY` and `GEMINI_API_KEY` in your Windows **user environment**, outside this repository. These are user environment values, not an encrypted credential vault. Blank fields preserve existing keys. The setup makes no API requests. The helper prefers saved Windows user keys, so a stale Codex process key does not override a newly entered key. Restart Codex for other processes to inherit the environment. Never paste keys into chat, prompts, source files or logs.

An Inception key uses `https://api.inceptionlabs.ai/v1` and `mercury-2.5`, matching the [official Inception example](https://www.inceptionlabs.ai/models). If the Mercury key came from another provider, confirm that provider before changing the nonsecret endpoint/model in `scripts/agent-assist.json`.

Google defaults to `gemini-3.5-flash-lite`, a multimodal model with a [free tier](https://ai.google.dev/gemini-api/docs/pricing). Google limits 2.5 access to existing users. Actual free-tier availability and limits depend on the user's [AI Studio project quota](https://ai.google.dev/gemini-api/docs/rate-limits), not the helper's local request cap.

Check key presence without printing values or sending a request:

```powershell
python C:\dev\steelclash\scripts\agent-assist.py mercury --status
python C:\dev\steelclash\scripts\agent-assist.py google --status
```

## Bounded external tasks

Write a small task file under the external audit directory, describing the desired behavior and constraints. Explicitly select source files or images to share:

```powershell
python C:\dev\steelclash\scripts\agent-assist.py mercury --prompt-file C:\dev\steelclash-beta-audit\agent-task.md --file src/main/java/com/steelclash/core/WeaponRig.java
python C:\dev\steelclash\scripts\agent-assist.py google --prompt-file C:\dev\steelclash-beta-audit\visual-task.md --image C:\dev\steelclash-beta-audit\20261010\step1-broad\mob-handover-comparison.png
```

The helper reads no directory tree automatically. It limits text to 40,000 characters, output to 2,048 tokens (Mercury) or 1,024 (Google), and images to two PNGs below 5 MB each. No tools, automatic edits, retries or fallback providers are enabled. Stop on quota errors. Local caps are 12 Mercury and 2 Google attempts per UTC day, including failed attempts; these are not provider billing limits. Invoke requests serially so the local ledger stays consistent. Proposals and the nonsecret ledger go under ignored `build/agent-assist/`.

Review proposals, apply only justified changes, and run repository-required checks before commit or push. Use Google for rendered screenshots rather than private source or configuration; Google's free-tier [data handling](https://ai.google.dev/gemini-api/docs/pricing) differs from its paid tier.

## Background animation captures

Add `-PliveCaptureBackground` to an opt-in `runClient -PliveCapture=...` invocation, or `-PposeSheetBackground` to a `runClient -PposeSheet=...` invocation. The diagnostic hides its own GLFW window, disables focus-loss pausing and avoids mouse grabbing. Captures continue through normal KeyMapping and combat paths into the existing render target. This is background rendering, not a headless graphics backend. The test client must be windowed; both modes verify that GLFW actually hid it. A sheet run that does not quit restores visibility with focus-on-show disabled for that call. Normal Gradle pose-sheet runs quit at completion.

## Verified baseline

Capture evidence and its limits are maintained in [testing](testing.md#current-verified-checkpoints). The initial loading-window flash remains unverified. API credentials were previously configured and exercised successfully; verify them when needed rather than treating this workflow as a current provider-status report.
