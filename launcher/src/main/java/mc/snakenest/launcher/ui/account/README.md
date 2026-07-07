# ui.account

- **`AccountPopover`** — a `JPopupMenu`-based popover (not a full page/dialog,
  matching the mockup's "small panel anchored under the account icon"
  framing): avatar, username, role, email, and a logout button. `show(...)`
  takes the account button itself as the anchor - see
  `ui.TopBar`/`ui.LauncherFrame`, whose `onOpenAccount` callback receives
  that button for exactly this reason.

  **Never fetches anything itself.** All of username/role/email/avatar are
  handed in already-resolved by the caller (`LauncherApp`), which fetches
  them once via the consolidated `auth.LauncherAuthApiClient#fetchPlayerInfo`
  call - at login and at every startup, never when the popover is opened.
  Opening the popover on click used to trigger 3 separate signed network
  calls every time; that's exactly the bug this was fixed for.

  Corner rounding comes from FlatLaf's `Popup.borderCornerRadius` (bumped to
  12 in `ui.ThemeController` - the 4px default was too subtle to read as
  rounded), not from anything in this class.

  "Gérer le profil" and "Se déconnecter" are matched to the same width
  (`matchWidth`, the wider of the two natural preferred widths) - each
  `JButton` otherwise sizes itself to its own text, and "Gérer le profil"/
  "Se déconnecter" aren't the same length, which read as an unintentional
  mismatch rather than a deliberate aligned button pair.
