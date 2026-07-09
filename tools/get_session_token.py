#!/usr/bin/env python3
"""Runs the device-auth flow against a SnakeN3st Azuriom site (see the
sibling SnakeN3stLogin repo's site-plugin/LAUNCHER_INTEGRATION.md) and
prints the resulting -Dsn3.token=<hex> JVM argument, without going through
the Java launcher's own UI - e.g. to bootstrap a second/alternate launcher
that has no device-auth flow of its own yet, or to run the game directly
against an already-approved session.

Usage: fill in URL/CLIENT_ID below, then `python3 get_session_token.py`.
No third-party dependencies - standard library only.

Prints a secret (a raw Ed25519 private key seed, in hex) to the terminal.
Treat it the same as a password: don't paste it anywhere untrusted, and
don't commit a shell history/log containing it.
"""

import json
import sys
import time
import urllib.error
import urllib.request
import webbrowser

# --- Configure these two ---
URL = "https://snake-n3st.fr"
CLIENT_ID = "rX07rprvBaRZPYd8eHYupZpgYHipiwOS"
# ----------------------------

POLL_INTERVAL_SECONDS = 3
# Matches the server's own challenge TTL (LAUNCHER_INTEGRATION.md,
# ChallengeController::TTL_MINUTES) - no point polling past that.
TIMEOUT_SECONDS = 600


def post_json(url: str, payload: dict) -> dict:
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url, data=data,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.loads(response.read().decode("utf-8"))


def get_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    if CLIENT_ID == "REPLACE_ME":
        sys.exit("Set CLIENT_ID (and URL, if not snake-n3st.fr) at the top of this script first.")

    base = URL.rstrip("/")

    try:
        challenge = post_json(f"{base}/api/launcher-auth/challenge", {"client_id": CLIENT_ID})
    except urllib.error.HTTPError as e:
        sys.exit(f"Could not create a challenge ({e.code}): {e.read().decode('utf-8', 'replace')}")
    except urllib.error.URLError as e:
        sys.exit(f"Could not reach {base}: {e.reason}")

    token = challenge["challenge"]
    approve_url = f"{base}/launcher-login?challenge={token}"

    print("Open this URL and approve the login (opening your browser now):")
    print(f"  {approve_url}\n")
    webbrowser.open(approve_url)

    poll_url = f"{base}/api/launcher-auth/challenge/{token}"
    print("Waiting for approval", end="", flush=True)

    deadline = time.monotonic() + TIMEOUT_SECONDS
    try:
        while time.monotonic() < deadline:
            try:
                approved = get_json(poll_url)
            except urllib.error.HTTPError as e:
                if e.code == 403:
                    # Still pending - the player hasn't confirmed yet.
                    print(".", end="", flush=True)
                    time.sleep(POLL_INTERVAL_SECONDS)
                    continue
                if e.code == 404:
                    sys.exit("\nChallenge expired or already used - run the script again.")
                sys.exit(f"\nUnexpected error polling the challenge ({e.code}): {e.read().decode('utf-8', 'replace')}")
            else:
                break
        else:
            sys.exit("\nTimed out waiting for approval.")
    except KeyboardInterrupt:
        sys.exit("\nCancelled.")

    print()
    print(f"Approved - player id {approved['playerId']}\n")
    print("JVM argument for the game process (in-game passwordless auth):")
    print(f"  -Dsn3.token={approved['privateKey']}\n")
    print("For the launcher itself, if this session is meant to back a whole launcher rather than just one game launch:")
    print(f"  -Dsn3.baseUrl={URL} -Dsn3.clientId={CLIENT_ID}")


if __name__ == "__main__":
    main()
