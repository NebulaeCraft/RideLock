**RideLock**

RideLock is a lightweight client-side Forge mod for Minecraft 1.12.2 designed to help keep your camera perspective in line with vehicles.

It was heavily inspired by the camera-locking logic in SmoothCoasters. While modern versions of Minecraft have great options for this, 1.12.2 was lacking an option for this.

**What it does**

When you're sitting in a minecart or train, RideLock looks up to 128 loaded rail blocks ahead and behind the vehicle and fits a smooth three-dimensional curve to the track. The camera is locked to smoothly interpolated tangent directions sampled every four blocks by default, preventing the repeated left/right camera movement caused by large voxel curves.

Railcraft turnouts and wyes are supported without making Railcraft a required dependency. RideLock follows the branch currently reported by the track on the client and remembers the route the vehicle has already travelled. It only scans chunks that are already loaded and falls back to the original motion-based smoothing for other vehicles or when there is not enough connected track to fit a curve.

**Features**

* Toggleable: Hit F9 (default) to turn the lock on or off instantly.
* Client-Side Only: This does not need to be installed on the server.
* Smooth Large Curves: Uses a continuous fitted rail path instead of frame-to-frame minecart movement.
* Railcraft Compatible: Follows the currently selected turnout or wye branch when Railcraft is present.
* Configurable Sampling: The tangent sample spacing can be changed from the Forge Mods config menu in 0.1-block increments (default 4.0, range 0.1–32.0) without restarting Minecraft.

Special thanks to Bergerhealer for the inspiration via SmoothCoasters (https://github.com/bergerhealer/SmoothCoasters/)
