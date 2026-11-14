# 0015. Emulator must boot with -gpu swangle

- **Status:** Accepted
- **Date decided:** 2026-08-19
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

The headless emulator on buildhost (itself a KVM guest with no GPU render node) segfaulted while compositing a hero backdrop under a gradient scrim. A core dump showed the fault inside the bundled deprecated SwiftShader GLES driver's JIT code (`-gpu swiftshader_indirect`), an upstream bug, not an app bug.

## Decision

Always boot with `-gpu swangle` on the command line (ANGLE onto SwiftShader Vulkan). Evidence from an identical scripted soak: swiftshader_indirect crashed on cycle 1 and 3 of two runs; swangle ran 60 cycles over 3 runs with no crash. Setting `hw.gpu.mode = swangle` in the AVD `config.ini` is a false positive and must not be relied on. `-gpu host` is impossible here. Do not use the old `-memory 3072 -skin 720x1600` recipe. `-gpu lavapipe` is the untested fallback. `PREVIEW.md` carries the boot command and a `dumpsys SurfaceFlinger | grep GLES:` check.

## Alternatives considered

- Reduced resolution workaround: never worked.
- `-gpu host`: no GPU on the host.
- A different system image or newer emulator: bug is host-side, nothing newer existed.

## Consequences

- Software rendering only; real devices were never affected.
- Anyone booting without the flag gets random whole-emulator crashes on hero screens.

## Source

[PLAN.md section 6](../../PLAN.md) and section 8 (root cause, evidence); [docs/PREVIEW.md](../PREVIEW.md) section 1 and troubleshooting table. Decided and verified by the `toolchain-setup` agent, 2026-08-19.
