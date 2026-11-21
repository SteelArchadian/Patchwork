# Draft upstream issue (NOT posted) - C2ME (RelativityMC/C2ME-fabric), for Upstream Liaison

**Title:** OpenCL accel: server thread dies with "No OpenCL devices found" on GPUs without cl_khr_fp64 (fallback is off by default)

**Version:** c2me-neoforge-opts-accel-opencl 0.4.0-alpha.0.122+1.21.1, NeoForge 21.1.252, MC 1.21.1

**What happens:** On a laptop with Intel UHD Graphics (OpenCL 3.0 NEO, no `cl_khr_fp64`), OpenCLDeviceLocator rejects the only device, so `preRunServer` finds no devices. With `openclAccel.allowIncompatibilityFallback` at its default (false), it throws `IllegalStateException: No OpenCL devices found`, and you get "Uncaught exception in server thread". The world never loads, and the client sits on the loading screen.

**Expected:** A machine that simply has no usable OpenCL device should fall back to normal world generation, with a warning. The single consumer (`MixinThreadedAnvilChunkStorage`) already handles a null context. Hard failure makes sense when a device was found but failed to initialize. It doesn't make sense when the hardware just lacks the feature.

**Suggestion:** Either default `allowIncompatibilityFallback` to true, or always fall back in the "no devices found" branch, keeping the throw only for real init errors. Also consider showing the reason in the client progress text for that branch, as the init-failure branch already does.

**Workaround:** In `config/c2me.toml`, set `[openclAccel] allowIncompatibilityFallback = true`.

Logs: latest.log excerpt (device line, WARN no fp64, ERROR + stack) attached.
