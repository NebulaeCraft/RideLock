**RideLock**

RideLock is a lightweight client-side Forge mod for Minecraft 1.12.2 designed to help keep your camera perspective in line with minecarts and trains.

It was heavily inspired by the camera-locking logic in SmoothCoasters. While modern versions of Minecraft have great options for this, 1.12.2 was lacking an option for this.

**What it does**

When you're riding a minecart (including Railcraft carts and locomotives), RideLock records the last 1.5 seconds of your movement and fits a smooth curve through those positions. The fit is not forced through the latest block-stepped position, and your camera points directly along the curve's tangent at the current time instead of reacting to every individual rail segment.

The distance included in the fit depends on cart speed. At 20 ticks per second, Railcraft locomotive speeds of 0.2, 0.3, and 0.4 blocks per tick produce approximately 6, 9, and 12 blocks of sampled track respectively.

**Features**

* Toggleable: Hit F9 (default) to turn the lock on or off instantly.
* Client-Side Only: This does not need to be installed on the server.
* Minecart Only: Boats and other non-minecart vehicles are ignored.
* Large-Curve Smoothing: A fixed 1.5-second trajectory fit reduces camera oscillation on large, block-stepped curves.
* Live Configuration: The trajectory window and sample interval can be changed in Mods > Ride Lock > Config without restarting the game. The window keeps one decimal place and the interval is stored as whole milliseconds; defaults are 1.5 seconds and 10 milliseconds.

Special thanks to Bergerhealer for the inspiration via SmoothCoasters (https://github.com/bergerhealer/SmoothCoasters/)
