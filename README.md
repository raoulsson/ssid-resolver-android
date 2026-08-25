# ssid-resolver-android - "Get my Wi-Fi Name"

A standalone Android app that resolves the SSID of the connected Wi-Fi network, or simply: "Get my
Wi-Fi Name". It uses the latest Android APIs as of January 2026.

The app shows exactly which permissions an SSID lookup needs, names the missing ones when a lookup
fails, and is the native proving ground for the
[Flutter plugin](https://github.com/raoulsson/ssid_resolver_flutter): run it here first, and the
platform layer is either cleared or convicted before you touch any Dart. Since 2.0 it also carries
`NetworkInterfaceResolver`, which reads the **real netmask** of every IPv4 interface - see
[the netmask and the broadcast address](#the-netmask-and-the-broadcast-address) below.

## What the SSID lookup needs

The permissions are defined in the `AndroidManifest.xml` file:

```xml
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

Only the two location permissions are runtime permissions the user is prompted for - Android, like
iOS, ties Wi-Fi identity to Location. The rest are normal permissions: granted at install, never
prompted. `ACCESS_NETWORK_STATE` is the one the `ConnectivityManager` fallback below needs, and the
one whose absence caused the 2.0 `SecurityException` fix.

### What it looks like

The full permission path on a physical Samsung, left to right, top to bottom.

<img src="res/android-1-nothing-granted.jpeg" alt="Nothing granted" width="400"/>

**1.** Location denied - it names the exact permissions that are missing

<img src="res/android-3-location-prompt.jpeg" alt="Location prompt" width="400"/>

**2.** The runtime prompt for fine and coarse location

<img src="res/android-4-granted-not-resolved.jpeg" alt="Granted" width="400"/>

**3.** All permissions granted, ready to resolve

<img src="res/android-5-ssid-resolved.jpeg" alt="SSID resolved" width="400"/>

**4.** Resolved: the network name appears

<img src="res/android-6-network-interfaces.jpeg" alt="Network interfaces" width="400"/>

**5.** Interfaces with real netmasks - no runtime permission needed

## The netmask and the broadcast address

While building the SSID part, a related thing turned out to be quietly broken almost everywhere,
and 2.0 solves it too: `NetworkInterfaceResolver` lists every IPv4 interface with its **real
netmask** and the broadcast address derived from it. It needs **no runtime permission** - not
Location, not `ACCESS_WIFI_STATE` - so the list works on a device where the user denied everything
and no SSID can be resolved.

Why it matters: without a netmask there is no way to compute a UDP broadcast address, and the usual
workaround - take the first three octets, append `.255` - is only correct on a `/24` and silently
wrong on anything **wider**, the `/20`, `/22` and `/16` that corporate, campus, guest and mesh
networks hand out. Wrong in the worst way: the send succeeds, nothing throws, and the packet
reaches nobody. On a `/20`, a host at `10.8.2.73` has its broadcast at `10.8.15.255`, while the
shortcut yields `10.8.2.255`: an ordinary unused host address that swallows everything sent to it,
with no error to go on. Screenshot 5 above shows the real thing: `wlan0` on a `/20` with broadcast
`10.8.15.255`, not `10.8.2.255`.

One caveat: this does **not** defeat client isolation or a separate IoT VLAN - those are
routing decisions no app can override. What it fixes is the case where the devices are reachable
and the broadcast address was simply pointing at nothing.

**Two implementations, because one is not enough.** `java.net.NetworkInterface` is tried first, but
on Android 11+ the `/proc/net` restrictions can make `getNetworkInterfaces()` return **null** -
observed on a Samsung running Android 15, where it returns null rather than throwing, and
`Collections.list(null)` then dies with an NPE. `ConnectivityManager`/`LinkProperties` is the
fallback and gives the prefix length directly. It needs `ACCESS_NETWORK_STATE`, declared in the
manifest: a normal permission, granted at install with no prompt.

**Cellular is not a broadcast target.** Screenshot 5 also shows `rmnet_data9`, cellular on a `/30` -
a tiny point-to-point subnet. On iOS, cellular typically carries a `/32` instead, where the derived
broadcast equals the interface's own address. Either way the derived address reaches no discovery
target, so any code picking broadcast targets has to exclude cellular rather than treat it as one
more network - otherwise discovery traffic goes out over mobile data to reach nothing at all.

## Related repositories

This app is the Android source for a three-repo family. `NetworkInterfaceResolver.kt` in `core` is
kept in lockstep with the copy in the Flutter plugin - a fix here belongs there too, and vice versa.
`CoreSSIDResolver.kt` and `PermissionHandler.kt` share their approach with the plugin's copies but
have diverged in the details, so a fix there needs a look rather than a blind paste.

| | |
|---|---|
| **Flutter plugin** (published) | [ssid_resolver_flutter](https://github.com/raoulsson/ssid_resolver_flutter) - [pub.dev](https://pub.dev/packages/ssid_resolver_flutter) |
| **iOS counterpart** | [ssid-resolver-ios](https://github.com/raoulsson/ssid-resolver-ios) |

Running the native app first is the fastest way to tell a platform bug from a Flutter channel bug:
if the value is right here and wrong in the plugin, the fault is in the Dart layer.

## Fixed in 2.0

Found by running on a physical Samsung on Android 15, not by reading the code.

- **The interface list was always empty.** `NetworkInterface.getNetworkInterfaces()` returned
  `null` on that device, `Collections.list(null)` threw `NullPointerException`, and the catch
  turned it into an empty list - a crash on every call presenting itself as "this device has no
  network interfaces". The `ConnectivityManager` fallback described above fixes it.
- **The fallback threw `SecurityException`.** `ConnectivityManager.getAllNetworks()` needs
  `ACCESS_NETWORK_STATE`, which was missing from the manifest. The Flutter plugin added exactly
  this permission in its 1.4.0; this app, which is the plugin's source, never got it.
- **`NetworkInterfaceResolver` added** - the netmask and broadcast API above.

# License

Copyright 2026 Raoul Marc Schmidiger (hello@raoulsson.com)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
