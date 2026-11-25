# Revora design rules

Every screen and every UI pull request follows these rules. The goal is a dashboard a driver can read
in under a second, at arm's length, at night.

## 1. Driving comes first
- One glance, under one second. Each page has one hero value and at most 3–4 tiles. Detail lives on later pages.
- Sizes: the hero value is ≥ 48 sp, other values ≥ 32 sp, labels ≥ 14 sp. Nothing important is smaller than 12 sp.
- Touch targets are ≥ 48 dp, and ≥ 64 dp on head units (from 1024 dp wide in landscape).
- No paragraphs or dense lists on screens used while driving. Editing and settings are for when the car is parked.

## 2. One design system (`ui/Theme.kt`)
- Colour tokens: background, surface, surface-raised, text, text-muted, accent (Revora red), and `ok`/`warn`/`alarm`.
  Never use a colour literal inside a screen.
- Type scale: display (hero), headline, title, body, label. Use only these.
- Spacing on a 4/8 dp grid: 4, 8, 12, 16, 24, 32. Corner radii are 12 dp for tiles and 20 dp for sheets.
- Dark is the default (night driving, OLED). Light and high-contrast are options.
- Text contrast meets WCAG AA (4.5:1 for body, 3:1 for large values). Colour is never the only signal:
  a warning always also has an icon and a word ("HOT", "LOW").

## 3. Gauges
- Custom Canvas, flat and clean, with no skeuomorphic chrome. There are four styles:
  digital (number), arc, bar, sparkline (last ~60 s).
- Each metric has a sensible full scale and a warning zone (`Metric.range`, `Warnings`).
  Units always come from the user's settings.
- Values animate smoothly (spring or tween ≤ 250 ms) and never jump the layout. Digits use a tabular figure style.

## 4. Customisation (Faz 4b)
- Presets are Minimal, Sport, Eco and Diagnostic. The user then reorders tiles, picks a style per tile,
  resizes tiles (1×1, 2×1, 2×2), adds pages and swipes between them. There is always a "Reset to preset".
- Saved in DataStore (`DashboardCodec`). It survives restarts and updates; unknown tiles from newer versions are skipped.
- The same dashboard adapts to phone portrait (2 columns), landscape and tablet (4 columns), and
  head units 1024×600 and 1280×720 (4 columns, larger type).

## 5. States
Every screen designs these states, not just the happy path:
- empty / not connected
- connecting
- no data, or PID not supported (shown as "—" with a hint, never a fake 0)
- error
- warning (tile turns `alarm` red, shows an icon and a word, with a short haptic, plus a sound if the user turned it on)

## 6. Accessibility
- Every gauge has a TalkBack description, for example "Coolant 89 degrees, normal".
- At 1.3× font scale nothing is clipped or overlaps (checked in the screenshot tests).

## 7. Proof
- Screenshot tests cover every preset and every main screen at 360×640, 800×1280, 1024×600 and 1280×720,
  plus font scale 1.3×.
- A UI pull request links its screenshots, so the owner sees the design before merging.
- We take patterns from Tesla/Polestar, Torque Pro, OBDeleven and Car Scanner, but we copy no layout,
  artwork or names from them.
