# ui.account

- **`AccountPopover`** — a `JPopupMenu`-based popover (not a full page/dialog,
  matching the mockup's "small panel anchored under the account icon"
  framing): username, role, email (from the signed `player/*` endpoints,
  see `auth.LauncherAuthApiClient`), and a logout button. `show(...)` takes
  the account button itself as the anchor - see `ui.TopBar`/`ui.LauncherFrame`,
  whose `onOpenAccount` callback receives that button for exactly this
  reason.
