# Android Tunnel App

Android app that exposes a small TCP tunnel server for RouterTunnel.

## Features

- Foreground service stays active when the phone screen is off
- Simple password-based auth
- TCP forwarding for RouterTunnel
- PING/PONG discovery so RouterTunnel can scan for nearby devices

## Usage

1. Install the APK on the phone.
2. Open the app, set a password and port (default 45454), and tap Start.
3. Note the phone IP shown in the app.
4. On the Mac, run RouterTunnel with:

```bash
./router_tunnel --phone-host <PHONE_IP> --phone-port 45454 --phone-password-file ./router_tunnel.password
```

The password file should contain a single line with the same password used in the app.

## Build (GitHub Actions)

The repository includes a GitHub Actions workflow that builds an unsigned release APK.
