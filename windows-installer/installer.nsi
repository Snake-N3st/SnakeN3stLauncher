; SnakeN3st Launcher - native Windows installer.
;
; Deliberately built on ONLY what ships in the official NSIS package (nsDialogs, FileFunc,
; StrFunc) plus Windows' own PowerShell/.NET (installer-helper.ps1) - no third-party NSIS plugin
; is fetched or bundled. See windows-installer/README.md for why.
;
; ALL network I/O (JSON fetches and file downloads alike) goes through installer-helper.ps1, not
; NSIS's own bundled NSISdl plugin - NSISdl's own documentation says "HTTPS is not supported,
; only plain HTTP", confirmed for real with a Wine test where it hung indefinitely against the
; (HTTPS-only) real site. PowerShell's Invoke-RestMethod/WebClient handle TLS correctly. JSON
; responses come back from PowerShell as simple `key=value` manifest text files (NSIS has no JSON
; parser without a third-party plugin; see ExtractManifestValue below) instead of raw JSON.
;
; No client_id anywhere in this script on purpose: the bootstrap jar this installer downloads
; already has its own client_id baked into jvm.prop at publish time
; (BootstrapJvmPropInjector, site-side), and the bootstrap release/download endpoints below are
; deliberately public+unauthenticated - see LAUNCHER_INTEGRATION.md section 8 in the sibling
; SnakeN3stLogin repo.

Unicode true

!include "nsDialogs.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "WinMessages.nsh"
!include "StrFunc.nsh"

${Using:StrFunc} StrLoc

; Pulls in the larger Modern-UI dialog template (custom page id 1018 resolves against whichever
; UI is included) - without this, NSIS falls back to its small classic-UI page frame, which is
; nowhere near tall enough for InstallPageCreate's full layout: everything below the banner
; (title, location field, space labels, install button) was being created past the bottom edge
; of that smaller window and never actually rendered, leaving only the banner + default
; "Nullsoft Install System" footer visible.
!include "MUI2.nsh"
!insertmacro MUI_LANGUAGE "French"

Name "SnakeN3st Launcher"
OutFile "SnakeN3stLauncherInstaller.exe"
InstallDir "$LOCALAPPDATA\Programs\SnakeN3st Launcher"
; Per-user install, no UAC prompt - matches how the launcher's own data already lives under
; %APPDATA%, and how Discord/VS Code/Slack install by default.
RequestExecutionLevel user
ShowInstDetails hide
XPStyle on

; ----------------------------------------------------------------------------------------------
; Configuration
; ----------------------------------------------------------------------------------------------

; /D SITE_BASE_URL=... on the makensis command line overrides this - used to point a locally
; built test .exe at /server/azuriom instead of production (see README's testing section).
!define /ifndef SITE_BASE_URL "https://snake-n3st.fr"
; The site's ECDSA P-256 public key (hex, the 65-byte uncompressed point: 130 characters), baked in at build
; time with /D SITE_SIGNING_PUBKEY_P256=... - `launcher-auth:signing:init` prints it. A build that has one
; REFUSES a bootstrap whose signature is wrong, and also one that has none: building with a key is itself the
; statement that this site signs, and an installer keeps no memory between runs that could tell it otherwise.
; It is deliberately a build-time constant and NOT fetched from the site, which would prove only that the site
; agrees with itself. Empty (the default) skips verification - a dev or test build, or a site that does not
; sign yet. Protects a *legitimately obtained* installer against a tampered bootstrap served later; the
; installer .exe itself stays trust-on-first-use, like every first download.
!define /ifndef SITE_SIGNING_PUBKEY_P256 ""
!define SITE_INFO_URL "${SITE_BASE_URL}/api/launcher-auth/site-info"
!define BOOTSTRAP_LATEST_URL "${SITE_BASE_URL}/api/launcher-auth/releases/bootstrap/latest"
; No client_id needed for {type}=bootstrap - verified against the real controller code, not
; assumed (see LauncherReleaseController::rejectUnlessKnownClient's own docblock).
!define ADOPTIUM_JRE21_API "https://api.adoptium.net/v3/assets/latest/21/hotspot?image_type=jre&os=windows&architecture=x64&vendor=eclipse"
; See the "Espace necessaire" comment where this is used - Adoptium's API only ever gives the
; compressed download size, never the extracted-on-disk size.
!define JRE_EXTRACTED_SIZE_MULTIPLIER 4

; Must match the dialog's real pixel width set in ResizeAndCenterInstallerWindow (both here and
; there are real pixels, not nsDialogs "u" units) - the banner bitmap is generated at exactly this
; size and displayed at exactly this size, so there is no stretch/distortion in either direction.
!define BANNER_WIDTH 560
!define BANNER_HEIGHT 170
!define DIALOG_WIDTH 560
!define DIALOG_HEIGHT 335

; Bottom-anchored primary action buttons (Installer / Terminer) - taller than a default button
; and always flush with the bottom margin regardless of what's above them, per explicit feedback.
!define ACTION_BUTTON_HEIGHT 42
!define ACTION_BUTTON_MARGIN 16
!define /math INSTALL_BUTTON_Y_TMP ${DIALOG_HEIGHT} - ${ACTION_BUTTON_MARGIN}
!define /math INSTALL_BUTTON_Y ${INSTALL_BUTTON_Y_TMP} - ${ACTION_BUTTON_HEIGHT}
!define INSTALL_BUTTON_HEIGHT ${ACTION_BUTTON_HEIGHT}
!define /math INSTALL_STATUS_Y ${INSTALL_BUTTON_Y} - 22

; The progress bar (shown in place of the Install button mid-install, see InstallButtonClick)
; is shorter than the button itself and vertically centered in that same rect - a full-height
; progress bar looked too heavy/thick, per explicit feedback.
!define PROGRESS_BAR_HEIGHT 18
!define /math PROGRESS_BAR_OFFSET_TMP ${ACTION_BUTTON_HEIGHT} - ${PROGRESS_BAR_HEIGHT}
!define /math PROGRESS_BAR_OFFSET ${PROGRESS_BAR_OFFSET_TMP} / 2
!define /math PROGRESS_BAR_Y ${INSTALL_BUTTON_Y} + ${PROGRESS_BAR_OFFSET}

; ----------------------------------------------------------------------------------------------
; State
; ----------------------------------------------------------------------------------------------

Var ManifestText      ; scratch: whatever key=value manifest text ExtractManifestValue reads from
Var PowerShellExe      ; resolved once in .onInit, reused for every later nsExec call

Var SiteName
Var LogoPngPath
Var BannerPngPath
Var LogoIcoPath
Var BannerBmpPath
Var SuccessBannerBmpPath

Var BootstrapVersion
Var BootstrapDownloadUrl
Var BootstrapSha256
Var BootstrapSize
Var BootstrapSigP256   ; hex, empty when the site sent none

Var JreDownloadUrl
Var JreSha256
Var JreSize

Var TotalBytesNeeded  ; JRE + bootstrap, computed once both are known - drives "Espace necessaire"

; Install page controls
Var Dialog
Var BannerCtl
Var LocationCtl
Var BrowseCtl
Var SpaceNeededCtl
Var SpaceFreeCtl
Var InstallButtonCtl
Var ProgressBarCtl
Var CancelLabelCtl
Var StatusLabelCtl

; Success page controls
Var ShortcutCheckboxCtl
Var LaunchNowCheckboxCtl

Page custom InstallPageCreate InstallPageLeave
Page custom SuccessPageCreate

; Sections (the actual file removal below) only ever execute during an instfiles-style page - our
; custom confirm page collects the checkbox choice and hands off to this standard one, which then
; actually runs Section "Uninstall".
UninstPage custom un.ConfirmPageCreate un.ConfirmPageLeave
UninstPage instfiles

Var un.DeleteDataCtl
Var un.DeleteData

Section "-Core"
  ; Everything real happens from the Install page's button handler (InstallButtonClick), not
  ; here - File/WriteRegStr/WriteUninstaller etc. all work from any Function, not only from a
  ; Section. This section exists only because NSIS requires at least one to produce a valid
  ; installer at all; it deliberately does nothing.
SectionEnd

; ----------------------------------------------------------------------------------------------
; Manifest helpers - installer-helper.ps1 writes plain `key=value` lines (one per line, UTF-8, no
; BOM - see its own Write-Manifest) instead of raw JSON, since NSIS has no JSON parser without a
; third-party plugin (ruled out, see README). Both assume $ManifestText already holds the file's
; text (via ReadFileToText below).
; ----------------------------------------------------------------------------------------------

; Reads an entire (small) file into $ManifestText. Push the path first.
Function ReadFileToText
  Exch $0
  Push $1
  Push $2
  FileOpen $1 $0 r
  StrCpy $ManifestText ""
  ReadFileToText_loop:
    FileRead $1 $2
    IfErrors ReadFileToText_done
    StrCpy $ManifestText "$ManifestText$2"
    Goto ReadFileToText_loop
  ReadFileToText_done:
  FileClose $1
  Pop $2
  Pop $1
  Pop $0
FunctionEnd

; Push "key" ; Call ExtractManifestValue ; Pop $0 -> the value, or "" if the key isn't present.
; $ManifestText is padded with a leading/trailing CRLF first so every key - including the very
; first and very last line of the file - has a real "$\r$\n<key>=" delimiter to search for and a
; real "$\r$\n" to terminate the value on, without special-casing either end.
Function ExtractManifestValue
  Exch $0 ; key
  Push $1
  Push $2
  Push $3
  Push $4

  StrCpy $1 "$\r$\n$ManifestText$\r$\n"
  StrCpy $2 "$\r$\n$0="
  ${StrLoc} $3 "$1" "$2" ">"
  StrCmp $3 "" ExtractManifestValue_notfound

  StrLen $4 $2
  IntOp $3 $3 + $4
  StrCpy $1 "$1" "" $3
  ${StrLoc} $4 "$1" "$\r$\n" ">"
  StrCmp $4 "" ExtractManifestValue_notfound
  StrCpy $0 "$1" $4
  Goto ExtractManifestValue_done

  ExtractManifestValue_notfound:
  StrCpy $0 ""

  ExtractManifestValue_done:
  Pop $4
  Pop $3
  Pop $2
  Pop $1
  Exch $0
FunctionEnd

; ----------------------------------------------------------------------------------------------
; Fails the whole install with a clear message - used after any download/verification/conversion
; step that didn't produce what it was supposed to. Never leaves a half-written install directory
; silently looking successful.
; ----------------------------------------------------------------------------------------------
Function FailInstall
  Exch $0
  MessageBox MB_ICONSTOP "Installation impossible : $0"
  Quit
FunctionEnd

; Locates a real powershell.exe without assuming it's on PATH under every locale/config -
; System32's own copy is present on every Windows 7+ install regardless of PATH. Sets
; $PowerShellExe directly (resolved once in .onInit, reused everywhere else).
Function LocatePowerShell
  StrCpy $PowerShellExe "$WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe"
  IfFileExists "$PowerShellExe" LocatePowerShell_found
  StrCpy $PowerShellExe "powershell.exe"
  LocatePowerShell_found:
FunctionEnd

; ----------------------------------------------------------------------------------------------
; .onInit - runs before any page is shown. Fetches everything small/fast (site branding, the
; bootstrap release's metadata, the JRE release's metadata) and converts the two images, so the
; custom page below can render the real banner/icon immediately and already know real sizes for
; "Espace necessaire" - matches "l'installateur charge la page d'accueil du site... et convertit
; le logo en ico" happening as part of startup, before the interactive page.
; ----------------------------------------------------------------------------------------------
Function .onInit
  InitPluginsDir
  ; installer-helper.ps1 must be a real file on disk for "-File" to run it, and $PLUGINSDIR is
  ; wiped automatically when the installer exits - so it's embedded into the compiled .exe here
  ; (not left to ship as a second file alongside it, which a first real Windows test caught: a
  ; freshly-built .exe run on its own, with nothing else copied next to it, failed instantly at
  ; the very first PowerShell call because $PLUGINSDIR\installer-helper.ps1 simply didn't exist there)
  ; and re-extracted to $PLUGINSDIR on every run, exactly like the images/manifests already are.
  File "/oname=$PLUGINSDIR\installer-helper.ps1" "installer-helper.ps1"
  StrCpy $LogoPngPath "$PLUGINSDIR\logo.png"
  StrCpy $BannerPngPath "$PLUGINSDIR\banner.png"
  StrCpy $LogoIcoPath "$PLUGINSDIR\logo.ico"
  StrCpy $BannerBmpPath "$PLUGINSDIR\banner.bmp"
  StrCpy $SuccessBannerBmpPath "$PLUGINSDIR\banner-success.bmp"

  Call LocatePowerShell

  ; --- site-info: one PowerShell call fetches the JSON, saves both images, and writes a
  ; manifest with the site's name - see installer-helper.ps1's SiteInfo action. ---
  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action SiteInfo -Url "${SITE_INFO_URL}" -OutputPath "$PLUGINSDIR\site-info.manifest" -LogoPath "$LogoPngPath" -BannerPath "$BannerPngPath"'
  Pop $0
  StrCmp $0 "0" 0 SiteInfoFailed

  Push "$PLUGINSDIR\site-info.manifest"
  Call ReadFileToText
  Push "name"
  Call ExtractManifestValue
  Pop $SiteName
  StrCmp $SiteName "" 0 SiteInfoDone
  StrCpy $SiteName "SnakeN3st Launcher"
  Goto SiteInfoDone

  SiteInfoFailed:
  Push "impossible de contacter ${SITE_BASE_URL} (site-info). Verifie ta connexion et reessaie."
  Call FailInstall

  SiteInfoDone:

  ; --- convert the two images now, once, up front (see installer-helper.ps1's own header) ---
  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action Icon -InputPath "$LogoPngPath" -OutputPath "$LogoIcoPath"'
  Pop $1
  StrCmp $1 "0" 0 ConvertFailed

  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action Banner -InputPath "$BannerPngPath" -OutputPath "$BannerBmpPath" -Width ${BANNER_WIDTH} -Height ${BANNER_HEIGHT} -Title "$SiteName Launcher" -Subtitle "Installation"'
  Pop $1
  StrCmp $1 "0" 0 ConvertFailed

  ; A second banner variant, same background, different overlay text - used by the success page
  ; so it looks like a real finish screen instead of a plain white page with a couple of labels.
  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action Banner -InputPath "$BannerPngPath" -OutputPath "$SuccessBannerBmpPath" -Width ${BANNER_WIDTH} -Height ${BANNER_HEIGHT} -Title "Installation reussie !" -Subtitle "$SiteName Launcher est pret"'
  Pop $1
  StrCmp $1 "0" 0 ConvertFailed
  Goto ConvertDone

  ConvertFailed:
  Push "la conversion des images du site a echoue (PowerShell/.NET)."
  Call FailInstall

  ConvertDone:

  ; Use the freshly-converted site logo as this running installer's own window/taskbar icon -
  ; it can't be baked in at compile time since it comes from the site, not a static asset.
  System::Call 'user32::LoadImage(p 0, t "$LogoIcoPath", i ${IMAGE_ICON}, i 32, i 32, i ${LR_LOADFROMFILE}) p .r0'
  SendMessage $HWNDPARENT ${WM_SETICON} 1 $0
  System::Call 'user32::LoadImage(p 0, t "$LogoIcoPath", i ${IMAGE_ICON}, i 16, i 16, i ${LR_LOADFROMFILE}) p .r0'
  SendMessage $HWNDPARENT ${WM_SETICON} 0 $0

  ; --- bootstrap release metadata (no client_id - see the top-of-file note) ---
  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action BootstrapInfo -Url "${BOOTSTRAP_LATEST_URL}" -OutputPath "$PLUGINSDIR\bootstrap.manifest"'
  Pop $0
  StrCmp $0 "0" 0 BootstrapMetaFailed

  Push "$PLUGINSDIR\bootstrap.manifest"
  Call ReadFileToText

  Push "version"
  Call ExtractManifestValue
  Pop $BootstrapVersion
  Push "sha256"
  Call ExtractManifestValue
  Pop $BootstrapSha256
  Push "url"
  Call ExtractManifestValue
  Pop $BootstrapDownloadUrl
  Push "size"
  Call ExtractManifestValue
  Pop $BootstrapSize
  Push "signature_p256"
  Call ExtractManifestValue
  Pop $BootstrapSigP256

  StrCmp $BootstrapDownloadUrl "" BootstrapMetaFailed
  StrCmp $BootstrapSha256 "" BootstrapMetaFailed
  Goto BootstrapMetaDone

  BootstrapMetaFailed:
  Push "impossible de recuperer la derniere version du launcher depuis ${SITE_BASE_URL}."
  Call FailInstall

  BootstrapMetaDone:

!if "${SITE_SIGNING_PUBKEY_P256}" != ""
  ; --- authenticate the release BEFORE a single byte of it is downloaded. The statement is rebuilt by the
  ; helper from the version/digest/size read above, never taken from the site as a string. ---
  StrCmp $BootstrapSigP256 "" BootstrapSignatureMissing
  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action VerifySignature -Type bootstrap -Version "$BootstrapVersion" -Sha256 "$BootstrapSha256" -Size "$BootstrapSize" -Signature "$BootstrapSigP256" -PublicKey "${SITE_SIGNING_PUBKEY_P256}"'
  Pop $0
  StrCmp $0 "0" BootstrapSignatureOk
  Push "la signature du launcher publie par ${SITE_BASE_URL} n'est pas valide (code $0). Ce fichier ne sera pas telecharge : il n'a pas ete signe par la cle que cet installateur connait."
  Call FailInstall

  BootstrapSignatureMissing:
  Push "le launcher publie par ${SITE_BASE_URL} n'est pas signe, alors que cet installateur a ete construit pour un site qui signe. Ce fichier ne sera pas telecharge."
  Call FailInstall

  BootstrapSignatureOk:
!endif

  ; --- JRE 21 metadata (Adoptium, entirely independent of the site) ---
  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action JreInfo -Url "${ADOPTIUM_JRE21_API}" -OutputPath "$PLUGINSDIR\jre.manifest"'
  Pop $0
  StrCmp $0 "0" 0 JreMetaFailed

  Push "$PLUGINSDIR\jre.manifest"
  Call ReadFileToText

  Push "url"
  Call ExtractManifestValue
  Pop $JreDownloadUrl
  Push "sha256"
  Call ExtractManifestValue
  Pop $JreSha256
  Push "size"
  Call ExtractManifestValue
  Pop $JreSize

  StrCmp $JreDownloadUrl "" JreMetaFailed
  StrCmp $JreSha256 "" JreMetaFailed
  Goto JreMetaDone

  JreMetaFailed:
  Push "impossible de recuperer Java 21 (Eclipse Temurin/Adoptium). Verifie ta connexion et reessaie."
  Call FailInstall

  JreMetaDone:

  ; $JreSize is the Adoptium *download* (compressed zip) size, not what actually ends up on disk
  ; after Unzip extracts it into jre-21 - real installs on the test VM landed around ~195 Mo total
  ; against a ~47 Mo download, a ~4x expansion factor (typical for a JRE: lots of already-compact
  ; class/native files that still compress reasonably well as a zip). Adoptium's API has no
  ; "installed size" field to read instead, so this is an estimate multiplier, not a stat we
  ; already know - "Espace necessaire" was showing the download size, not the real footprint.
  ; $BootstrapSize is used as-is since that jar is just copied, never extracted.
  IntOp $TotalBytesNeeded $JreSize * ${JRE_EXTRACTED_SIZE_MULTIPLIER}
  IntOp $TotalBytesNeeded $TotalBytesNeeded + $BootstrapSize
FunctionEnd

; ----------------------------------------------------------------------------------------------
; Install page - the sketch: green banner, location + browse, space needed/free, and an
; Install button that becomes a progress bar (background) with an "Annuler" label on top once
; clicked.
; ----------------------------------------------------------------------------------------------
Function InstallPageCreate
  Call HideWizardButtons

  nsDialogs::Create 1018
  Pop $Dialog
  StrCmp $Dialog "error" InstallPageCreate_end
  Call ResizeAndCenterInstallerWindow

  ; Plain pixel coordinates throughout this page (not nsDialogs "u" units) - the banner bitmap is
  ; generated at an exact pixel size (BANNER_WIDTH x BANNER_HEIGHT) by installer-helper.ps1, and
  ; mixing "u" units for surrounding controls with real pixels for the banner was what caused the
  ; banner to render visibly stretched in the first real-machine test (a 130-real-pixel-tall image
  ; stretched into a ~211-pixel-tall "130u" control). One consistent unit avoids that entirely.
  ${NSD_CreateBitmap} 0 0 ${BANNER_WIDTH} ${BANNER_HEIGHT} "" $BannerCtl
  Pop $BannerCtl
  ${NSD_SetStretchedBitmap} $BannerCtl "$BannerBmpPath" $0

  ; The title/subtitle text is baked directly into the banner bitmap itself (see .onInit's Banner
  ; call and installer-helper.ps1's ConvertTo-BannerBitmap) rather than a separate overlaid label -
  ; a real-machine test showed a "transparent" nsDialogs label never actually composites over a
  ; sibling bitmap control, so the text was invisible. No separate title control needed here.

  ${NSD_CreateLabel} 16 178 200 14 "Emplacement"
  Pop $0

  ${NSD_CreateText} 16 194 370 22 "$InstDir"
  Pop $LocationCtl
  ${NSD_OnChange} $LocationCtl OnLocationChanged

  ${NSD_CreateBrowseButton} 392 194 152 22 "Parcourir..."
  Pop $BrowseCtl
  ${NSD_OnClick} $BrowseCtl OnBrowseClicked

  ${NSD_CreateLabel} 16 222 520 14 "Espace necessaire : ..."
  Pop $SpaceNeededCtl

  ${NSD_CreateLabel} 16 240 520 14 "Espace libre : ..."
  Pop $SpaceFreeCtl

  ${NSD_CreateLabel} 16 ${INSTALL_STATUS_Y} 528 16 ""
  Pop $StatusLabelCtl

  ; Install button and its progress-bar/cancel-label stand-in share the exact same rect - only
  ; one pair is ever visible at a time (see InstallButtonClick). Anchored to the bottom of the
  ; window (not a fixed offset from the top) and taller than a plain default-height button, per
  ; explicit feedback after the first real-machine test. The progress bar itself is shorter and
  ; vertically centered within that rect (see PROGRESS_BAR_HEIGHT/_Y) rather than matching the
  ; button's full height, which looked too heavy.
  ${NSD_CreateButton} 16 ${INSTALL_BUTTON_Y} 528 ${INSTALL_BUTTON_HEIGHT} "Installer"
  Pop $InstallButtonCtl
  ${NSD_OnClick} $InstallButtonCtl InstallButtonClick

  ${NSD_CreateProgressBar} 16 ${PROGRESS_BAR_Y} 528 ${PROGRESS_BAR_HEIGHT} ""
  Pop $ProgressBarCtl
  ShowWindow $ProgressBarCtl ${SW_HIDE}

  ${NSD_CreateLabel} 16 ${INSTALL_BUTTON_Y} 528 ${INSTALL_BUTTON_HEIGHT} "Annuler"
  Pop $CancelLabelCtl
  ShowWindow $CancelLabelCtl ${SW_HIDE}
  ${NSD_AddStyle} $CancelLabelCtl ${SS_CENTER}
  ${NSD_OnClick} $CancelLabelCtl CancelButtonClick

  Call RefreshSpaceLabels

  nsDialogs::Show
  InstallPageCreate_end:
FunctionEnd

Function InstallPageLeave
FunctionEnd

Function OnLocationChanged
  Pop $0
  ${NSD_GetText} $LocationCtl $InstDir
  Call RefreshSpaceLabels
FunctionEnd

Function OnBrowseClicked
  Pop $0
  nsDialogs::SelectFolderDialog "Choisir l'emplacement d'installation" "$InstDir"
  Pop $0
  StrCmp $0 "error" OnBrowseClicked_end
  StrCpy $InstDir $0
  ${NSD_SetText} $LocationCtl "$InstDir"
  Call RefreshSpaceLabels
  OnBrowseClicked_end:
FunctionEnd

; Recomputes both "Espace necessaire"/"Espace libre" labels from real numbers - the required
; figure from the actual JRE+bootstrap sizes fetched in .onInit, the free figure from the real
; drive under whatever path is currently typed/picked.
Function RefreshSpaceLabels
  Push $0
  Push $1
  Push $2

  System::Int64Op $TotalBytesNeeded / 1048576
  Pop $0
  ${NSD_SetText} $SpaceNeededCtl "Espace necessaire : $0 Mo"

  ; ${DriveSpace} requires its argument to already exist as a real directory (it internally does
  ; IfFileExists "<path>\NUL") - $InstDir itself doesn't exist yet at this point (CreateDirectory
  ; only runs once you click Installer), so passing it directly always errored and fell back to
  ; the "?" placeholder, confirmed against FileFunc.nsh's own DriveSpace_ implementation. The
  ; drive root ("C:\") always exists regardless of whether the chosen subfolder does yet, and is
  ; all that's actually needed for a free-space figure.
  ${GetRoot} "$InstDir" $2
  ${DriveSpace} "$2" "/D=F /S=M" $1
  IfErrors 0 +2
  StrCpy $1 "?"
  ${NSD_SetText} $SpaceFreeCtl "Espace libre : $1 Mo"

  Pop $2
  Pop $1
  Pop $0
FunctionEnd

; Hides the standard wizard Back/Next/Cancel chrome (control IDs 3/1/2 - verified against
; NSIS's own Modern UI 2 source, `Interface.nsh`, which reads Next as 1 / Back as 3 the same
; way) so every visible control on these two pages is one this script built itself, matching
; the fully custom look in the sketch instead of NSIS's standard wizard frame.
Function HideWizardButtons
  GetDlgItem $0 $HWNDPARENT 1
  ShowWindow $0 ${SW_HIDE}
  GetDlgItem $0 $HWNDPARENT 3
  ShowWindow $0 ${SW_HIDE}
  GetDlgItem $0 $HWNDPARENT 2
  ShowWindow $0 ${SW_HIDE}
FunctionEnd

; Advances from a custom page to the next one - the "click Next" a visible wizard would do, since
; HideWizardButtons hides that button rather than removing it. A real-machine test showed the
; previously-used `SendMessage $HWNDPARENT "0x408" 0 1` (an undocumented raw message some other
; NSIS scripts use for this) simply does nothing here: the install genuinely completed (every
; file was written to disk, verified independently via SSH) but the page stayed put, which is
; exactly what the user saw as "le bouton terminer ne fais rien". Actually clicking the hidden
; Next button (control ID 1, same one HideWizardButtons hides) is the standard, documented way
; nsDialogs custom pages advance and reliably works.
Function GoToNextPage
  GetDlgItem $0 $HWNDPARENT 1
  SendMessage $0 ${BM_CLICK} 0 0
FunctionEnd

; NSIS's own page frame (even under Modern UI, once the title/subtitle strip and Back/Next/Cancel
; row are accounted for) is only tall enough for a normal single-question page - nowhere near this
; page's ~250-dialog-unit content height (banner + title + location picker + space labels +
; install button). Since HideWizardButtons already strips every bit of default chrome, this page
; owns 100% of its own look, so the fix is to resize+center the whole installer window itself
; rather than compress the sketch's layout to fit a frame sized for something much smaller.
;
; Resizing $HWNDPARENT alone (tried first) wasn't enough: the actual control surface controls get
; created on is $Dialog, a fixed-size child window embedded inside $HWNDPARENT by NSIS's page
; template - it doesn't stretch just because the outer frame does. Both have to be resized, and
; $Dialog must be resized BEFORE any control is created on it, since nsDialogs' own "100%"/"90%"
; width arguments are resolved against $Dialog's size at the moment of each ${NSD_Create...} call.
; Must be called right after nsDialogs::Create/Pop $Dialog, before creating any control on it.
Function ResizeAndCenterInstallerWindow
  System::Call 'user32::GetSystemMetrics(i 0) i .r0' ; SM_CXSCREEN
  System::Call 'user32::GetSystemMetrics(i 1) i .r1' ; SM_CYSCREEN
  IntOp $4 ${DIALOG_WIDTH} + 20
  IntOp $5 ${DIALOG_HEIGHT} + 40
  IntOp $2 $0 - $4
  IntOp $2 $2 / 2
  IntOp $3 $1 - $5
  IntOp $3 $3 / 2
  System::Call 'user32::MoveWindow(p $HWNDPARENT, i $2, i $3, i $4, i $5, i 1)'
  System::Call 'user32::MoveWindow(p $Dialog, i 0, i 0, i ${DIALOG_WIDTH}, i ${DIALOG_HEIGHT}, i 1)'
  Call ForceWindowToForeground
FunctionEnd

; Windows can silently refuse a plain SetForegroundWindow call from a process that doesn't
; currently hold the foreground-activation right (its own anti-focus-stealing protection) - a
; real-machine test showed the installer window opening behind whatever else had focus, needing
; a manual click, most likely because the .onInit network calls (site-info/bootstrap/JRE, all
; blocking) run long enough for that right to lapse before this page ever shows. Toggling
; HWND_TOPMOST briefly forces a restack Windows honors unconditionally, then dropping back to
; HWND_NOTOPMOST stops it from staying pinned above every other window afterward.
Function ForceWindowToForeground
  System::Call 'user32::SetWindowPos(p $HWNDPARENT, p -1, i 0, i 0, i 0, i 0, i 0x0001|0x0002)' ; HWND_TOPMOST
  System::Call 'user32::SetWindowPos(p $HWNDPARENT, p -2, i 0, i 0, i 0, i 0, i 0x0001|0x0002)' ; HWND_NOTOPMOST
  System::Call 'user32::SetForegroundWindow(p $HWNDPARENT)'
FunctionEnd

Var Cancelled

; The "Installer" click: swaps the button for the progress bar + "Annuler" label (same rect,
; see InstallPageCreate), then actually does the work - download JRE+bootstrap with checksum
; verification, extract the JRE, write the .bat, register the uninstaller - updating the
; progress bar for real as each step completes rather than a fake/timed animation.
;
; Each network/conversion step below runs via a single blocking nsExec::ExecToLog call to
; installer-helper.ps1, so the progress bar advances in discrete jumps at each step boundary
; rather than continuously mid-download, and a click on "Annuler" is only actually acted on once
; the current step's PowerShell call returns control to NSIS (checked via $Cancelled right after
; each step) - true instant mid-download cancellation isn't achievable here without either a
; third-party plugin or a much larger async/polling rewrite; see README's testing-limitations
; section for the honest version of this tradeoff.
Function InstallButtonClick
  Pop $0
  StrCpy $Cancelled "0"

  ShowWindow $InstallButtonCtl ${SW_HIDE}
  ShowWindow $ProgressBarCtl ${SW_NORMAL}
  ShowWindow $CancelLabelCtl ${SW_NORMAL}
  EnableWindow $LocationCtl 0
  EnableWindow $BrowseCtl 0

  SendMessage $ProgressBarCtl ${PBM_SETRANGE32} 0 100
  SendMessage $ProgressBarCtl ${PBM_SETPOS} 5 0

  CreateDirectory "$InstDir"
  IfErrors InstallFailed_generic

  ; --- bootstrap jar ---
  ${NSD_SetText} $StatusLabelCtl "Telechargement du launcher..."
  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action Download -Url "$BootstrapDownloadUrl" -OutputPath "$InstDir\snaken3st-bootstrap.jar"'
  Pop $0
  StrCmp $Cancelled "1" InstallCancelled
  StrCmp $0 "0" 0 InstallFailed_download
  SendMessage $ProgressBarCtl ${PBM_SETPOS} 35 0

  Push "$InstDir\snaken3st-bootstrap.jar"
  Push $BootstrapSha256
  Call VerifySha256
  Pop $0
  StrCmp $0 "1" 0 InstallFailed_checksum

  ; --- JRE 21 ---
  ${NSD_SetText} $StatusLabelCtl "Telechargement de Java 21..."
  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action Download -Url "$JreDownloadUrl" -OutputPath "$PLUGINSDIR\jre21.zip"'
  Pop $0
  StrCmp $Cancelled "1" InstallCancelled
  StrCmp $0 "0" 0 InstallFailed_download
  SendMessage $ProgressBarCtl ${PBM_SETPOS} 75 0

  Push "$PLUGINSDIR\jre21.zip"
  Push $JreSha256
  Call VerifySha256
  Pop $0
  StrCmp $0 "1" 0 InstallFailed_checksum

  ${NSD_SetText} $StatusLabelCtl "Extraction de Java 21..."
  nsExec::ExecToLog '"$PowerShellExe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\installer-helper.ps1" -Action Unzip -InputPath "$PLUGINSDIR\jre21.zip" -OutputPath "$InstDir\jre-21-raw"'
  Pop $1
  StrCmp $1 "0" 0 InstallFailed_generic
  SendMessage $ProgressBarCtl ${PBM_SETPOS} 90 0

  ; Adoptium zips contain one top-level "jdk-21.x.x+y-jre" folder - flatten it so the .bat's
  ; fixed "jre-21\bin\javaw.exe" path never has to guess the exact build string.
  Call FlattenJreFolder

  ; --- launch script: bootstrap always runs under THIS downloaded JRE. Bootstrap's own
  ; javaBinary() then reuses its own java.home to relaunch the full launcher jar - already
  ; correct with zero Java-side changes, see windows-installer/README.md. ---
  FileOpen $0 "$InstDir\Lancer SnakeN3st Launcher.bat" w
  FileWrite $0 '@echo off$\r$\n'
  FileWrite $0 'start "" "%~dp0jre-21\bin\javaw.exe" -jar "%~dp0snaken3st-bootstrap.jar"$\r$\n'
  FileClose $0

  CopyFiles /SILENT "$LogoIcoPath" "$InstDir\icon.ico"

  Call WriteUninstallEntry

  SendMessage $ProgressBarCtl ${PBM_SETPOS} 100 0
  ${NSD_SetText} $StatusLabelCtl "Installation terminee."

  Call GoToNextPage
  Goto InstallButtonClick_end

  InstallFailed_download:
  ${NSD_SetText} $StatusLabelCtl "Echec du telechargement."
  MessageBox MB_ICONSTOP "Le telechargement a echoue. Verifie ta connexion et reessaie."
  Goto InstallButtonClick_reset

  InstallFailed_checksum:
  ${NSD_SetText} $StatusLabelCtl "Fichier telecharge invalide (empreinte incorrecte)."
  MessageBox MB_ICONSTOP "Le fichier telecharge ne correspond pas a ce qui etait attendu (SHA-256 different). Rien n'a ete installe a partir de ce fichier - reessaie."
  Goto InstallButtonClick_reset

  InstallFailed_generic:
  ${NSD_SetText} $StatusLabelCtl "Echec de l'installation."
  MessageBox MB_ICONSTOP "L'installation a echoue."
  Goto InstallButtonClick_reset

  InstallCancelled:
  ${NSD_SetText} $StatusLabelCtl "Annule."

  InstallButtonClick_reset:
  ShowWindow $ProgressBarCtl ${SW_HIDE}
  ShowWindow $CancelLabelCtl ${SW_HIDE}
  ShowWindow $InstallButtonCtl ${SW_NORMAL}
  EnableWindow $LocationCtl 1
  EnableWindow $BrowseCtl 1

  InstallButtonClick_end:
FunctionEnd

Function CancelButtonClick
  Pop $0
  StrCpy $Cancelled "1"
FunctionEnd

; Adoptium's zip extracts as "$InstDir\jre-21-raw\jdk-21.x.x+y-jre\..." - move that single
; child folder's contents up to the fixed "$InstDir\jre-21" the .bat script expects.
Function FlattenJreFolder
  Push $0
  FindFirst $0 $1 "$InstDir\jre-21-raw\*"
  FlattenJreFolder_loop:
    StrCmp $1 "" FlattenJreFolder_notfound
    StrCmp $1 "." FlattenJreFolder_next
    StrCmp $1 ".." FlattenJreFolder_next
    Rename "$InstDir\jre-21-raw\$1" "$InstDir\jre-21"
    Goto FlattenJreFolder_done
    FlattenJreFolder_next:
    FindNext $0 $1
    Goto FlattenJreFolder_loop
  FlattenJreFolder_notfound:
  ; Nothing but the JRE's own bin/lib/etc at the top level already - use as-is.
  Rename "$InstDir\jre-21-raw" "$InstDir\jre-21"
  FlattenJreFolder_done:
  FindClose $0
  RMDir "$InstDir\jre-21-raw"
  Pop $0
FunctionEnd

; Push filepath, push expectedSha256Hex ; Call VerifySha256 ; Pop "1"/"0". Never accept a
; download whose checksum doesn't match - same "verify before you trust it" discipline as
; AtomicFiles#writeVerified on the Java side.
Function VerifySha256
  Exch $0 ; expected
  Exch
  Exch $1 ; filepath
  Push $2

  ; SHA-256 via CertUtil is the most robust zero-dependency way to hash a file on stock Windows
  ; (present since XP SP3+/Vista) without loading extra crypto plugins.
  nsExec::ExecToStack 'certutil -hashfile "$1" SHA256'
  Pop $2
  Pop $2
  ; certutil's first line echoes back the full file path being hashed ("Hachage SHA256 de
  ; C:\...\SnakeN3st Launcher\...jar:" on a French install) - a real-machine test caught that an
  ; install path can itself contain hex-valid letters (a-f/A-F show up constantly in ordinary
  ; words), so blindly scanning the WHOLE captured output for hex characters (the original
  ; approach) pulled stray letters from that path line into what was supposed to be a pure hash,
  ; silently corrupting an otherwise-correct download's verification. Isolate the actual hash line
  ; (always the second line) first, then normalize only that.
  Push $2
  Call ExtractCertutilHashLine
  Pop $2
  Push $2
  Call NormalizeHash
  Pop $2

  ${If} $2 == $0
    StrCpy $0 "1"
  ${Else}
    StrCpy $0 "0"
  ${EndIf}

  Pop $2
  Pop $1
  Exch $0
FunctionEnd

; Push certutil's full captured output ; Call ExtractCertutilHashLine ; Pop the second line (the
; hash itself, still with whatever whitespace certutil put around it - NormalizeHash cleans that
; up next) - isolated BEFORE any hex-filtering so the first line's file path can't contaminate it.
Function ExtractCertutilHashLine
  Exch $0
  Push $1
  Push $2
  Push $3

  ${StrLoc} $1 "$0" "$\r$\n" ">"
  StrCmp $1 "" ExtractCertutilHashLine_fail
  IntOp $2 $1 + 2
  StrCpy $3 "$0" "" $2
  ${StrLoc} $1 "$3" "$\r$\n" ">"
  StrCmp $1 "" ExtractCertutilHashLine_fail
  StrCpy $0 "$3" $1
  Goto ExtractCertutilHashLine_done

  ExtractCertutilHashLine_fail:
  StrCpy $0 ""

  ExtractCertutilHashLine_done:
  Pop $3
  Pop $2
  Pop $1
  Exch $0
FunctionEnd

; Strips whitespace/newlines and lowercases certutil's raw output down to a bare hex string,
; and pulls out just the hash line (certutil also prints a leading/trailing status line).
Function NormalizeHash
  Exch $0
  Push $1
  Push $2
  Push $3
  StrCpy $1 ""
  StrLen $2 $0
  StrCpy $3 0
  NormalizeHash_loop:
    IntCmp $3 $2 NormalizeHash_done
    StrCpy $4 $0 1 $3
    ${If} $4 == '0'
    ${OrIf} $4 == '1'
    ${OrIf} $4 == '2'
    ${OrIf} $4 == '3'
    ${OrIf} $4 == '4'
    ${OrIf} $4 == '5'
    ${OrIf} $4 == '6'
    ${OrIf} $4 == '7'
    ${OrIf} $4 == '8'
    ${OrIf} $4 == '9'
    ${OrIf} $4 == 'a'
    ${OrIf} $4 == 'b'
    ${OrIf} $4 == 'c'
    ${OrIf} $4 == 'd'
    ${OrIf} $4 == 'e'
    ${OrIf} $4 == 'f'
    ${OrIf} $4 == 'A'
    ${OrIf} $4 == 'B'
    ${OrIf} $4 == 'C'
    ${OrIf} $4 == 'D'
    ${OrIf} $4 == 'E'
    ${OrIf} $4 == 'F'
      StrCpy $1 "$1$4"
    ${EndIf}
    IntOp $3 $3 + 1
    Goto NormalizeHash_loop
  NormalizeHash_done:
  ; A SHA-256 hex digest is always exactly 64 characters - certutil's leading/trailing status
  ; lines contribute no hex characters at all, so this is already exactly the hash, lowercased
  ; for a case-insensitive-safe compare against the site/Adoptium-provided checksum.
  StrCpy $0 $1
  Pop $3
  Pop $2
  Pop $1
  Exch $0
FunctionEnd

Function WriteUninstallEntry
  WriteUninstaller "$InstDir\Uninstall.exe"

  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher" "DisplayName" "$SiteName Launcher"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher" "DisplayIcon" "$InstDir\icon.ico"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher" "UninstallString" '"$InstDir\Uninstall.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher" "InstallLocation" "$InstDir"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher" "Publisher" "$SiteName"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher" "DisplayVersion" "$BootstrapVersion"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher" "NoRepair" 1

  ${GetSize} "$InstDir" "/S=0K" $0 $1 $2
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher" "EstimatedSize" $0
FunctionEnd

; ----------------------------------------------------------------------------------------------
; Success page
; ----------------------------------------------------------------------------------------------
Function SuccessPageCreate
  Call HideWizardButtons

  nsDialogs::Create 1018
  Pop $Dialog
  StrCmp $Dialog "error" SuccessPageCreate_end
  Call ResizeAndCenterInstallerWindow

  ; Same banner treatment as the install page (real pixel size, title baked into the bitmap
  ; itself - see .onInit) rather than the plain top-anchored text label this page used to have,
  ; per explicit feedback that it looked too bare.
  ${NSD_CreateBitmap} 0 0 ${BANNER_WIDTH} ${BANNER_HEIGHT} "" $0
  Pop $0
  ${NSD_SetStretchedBitmap} $0 "$SuccessBannerBmpPath" $1

  ${NSD_CreateCheckBox} 16 190 528 20 "Creer un raccourci sur le bureau"
  Pop $ShortcutCheckboxCtl
  ${NSD_Check} $ShortcutCheckboxCtl

  ${NSD_CreateCheckBox} 16 218 528 20 "Lancer $SiteName Launcher maintenant"
  Pop $LaunchNowCheckboxCtl
  ${NSD_Check} $LaunchNowCheckboxCtl

  ; Same bottom-anchored, taller button treatment as the Install page's own primary action.
  ${NSD_CreateButton} 16 ${INSTALL_BUTTON_Y} 528 ${INSTALL_BUTTON_HEIGHT} "Terminer"
  Pop $0
  ${NSD_OnClick} $0 FinishButtonClick

  nsDialogs::Show
  SuccessPageCreate_end:
FunctionEnd

Function FinishButtonClick
  Pop $0
  ${NSD_GetState} $ShortcutCheckboxCtl $0
  ${If} $0 == ${BST_CHECKED}
    CreateShortCut "$DESKTOP\$SiteName Launcher.lnk" "$InstDir\Lancer SnakeN3st Launcher.bat" "" "$InstDir\icon.ico"
  ${EndIf}

  ${NSD_GetState} $LaunchNowCheckboxCtl $0
  ${If} $0 == ${BST_CHECKED}
    ; Same java invocation as the .bat (see InstallButtonClick) run directly rather than through
    ; the .bat/cmd.exe - one less process hop, and Exec (fire-and-forget) doesn't block this
    ; installer from actually closing afterward.
    Exec '"$InstDir\jre-21\bin\javaw.exe" -jar "$InstDir\snaken3st-bootstrap.jar"'
  ${EndIf}

  Call GoToNextPage
FunctionEnd

; ----------------------------------------------------------------------------------------------
; Uninstaller
; ----------------------------------------------------------------------------------------------

; Offers to also wipe the launcher's own data directory - config, cached jars, installed
; modpacks, the device auth key (see common's AppDirs/EncryptedFileKeyStorage). Off by default:
; removing the *program* shouldn't silently also remove a player's installed modpacks/config
; unless they explicitly ask for it.
; NSIS requires a function called from an "un."-prefixed page/section context to itself be
; "un."-prefixed (even though the actual logic is identical to the installer-side versions above -
; both binaries are compiled from this one script, but the installer/uninstaller halves are kept
; strictly separate). Thin duplicates rather than a bigger restructure, since the logic itself
; doesn't reference anything installer-only.
Function un.HideWizardButtons
  GetDlgItem $0 $HWNDPARENT 1
  ShowWindow $0 ${SW_HIDE}
  GetDlgItem $0 $HWNDPARENT 3
  ShowWindow $0 ${SW_HIDE}
  GetDlgItem $0 $HWNDPARENT 2
  ShowWindow $0 ${SW_HIDE}
FunctionEnd

Function un.ResizeAndCenterInstallerWindow
  System::Call 'user32::GetSystemMetrics(i 0) i .r0'
  System::Call 'user32::GetSystemMetrics(i 1) i .r1'
  IntOp $4 ${DIALOG_WIDTH} + 20
  IntOp $5 ${DIALOG_HEIGHT} + 40
  IntOp $2 $0 - $4
  IntOp $2 $2 / 2
  IntOp $3 $1 - $5
  IntOp $3 $3 / 2
  System::Call 'user32::MoveWindow(p $HWNDPARENT, i $2, i $3, i $4, i $5, i 1)'
  System::Call 'user32::MoveWindow(p $Dialog, i 0, i 0, i ${DIALOG_WIDTH}, i ${DIALOG_HEIGHT}, i 1)'
  System::Call 'user32::SetWindowPos(p $HWNDPARENT, p -1, i 0, i 0, i 0, i 0, i 0x0001|0x0002)'
  System::Call 'user32::SetWindowPos(p $HWNDPARENT, p -2, i 0, i 0, i 0, i 0, i 0x0001|0x0002)'
  System::Call 'user32::SetForegroundWindow(p $HWNDPARENT)'
FunctionEnd

Function un.GoToNextPage
  GetDlgItem $0 $HWNDPARENT 1
  SendMessage $0 ${BM_CLICK} 0 0
FunctionEnd

Function un.ConfirmPageCreate
  Call un.HideWizardButtons

  nsDialogs::Create 1018
  Pop $Dialog
  StrCmp $Dialog "error" un.ConfirmPageCreate_end
  Call un.ResizeAndCenterInstallerWindow

  ${NSD_CreateLabel} 16 16 528 40 "Desinstallation de $(^Name)$\r$\nCeci va supprimer le programme installe dans $\"$InstDir$\"."
  Pop $1

  ${NSD_CreateCheckBox} 16 70 528 20 "Supprimer aussi les donnees du launcher (config, modpacks installes, cle de connexion) dans %APPDATA%\snake-n3st"
  Pop $un.DeleteDataCtl

  ${NSD_CreateButton} 16 ${INSTALL_BUTTON_Y} 528 ${INSTALL_BUTTON_HEIGHT} "Desinstaller"
  Pop $2
  ${NSD_OnClick} $2 un.ConfirmButtonClick

  nsDialogs::Show
  un.ConfirmPageCreate_end:
FunctionEnd

Function un.ConfirmButtonClick
  Pop $0
  Call un.GoToNextPage
FunctionEnd

Function un.ConfirmPageLeave
  ${NSD_GetState} $un.DeleteDataCtl $un.DeleteData

  ; un.HideWizardButtons hid the wizard's Back/Next/Cancel controls (IDs 1/2/3) for this custom
  ; page - but those are the SAME physical controls the next page (the standard "instfiles"
  ; progress page, declared right after this one) reuses and repurposes as its own "Fermer"/
  ; "Annuler" buttons. Leaving them hidden meant the uninstaller reached "Desinstallation
  ; terminee" with no visible way to close it at all - a real-machine test caught this. Show them
  ; again on the way out so the standard instfiles page behaves normally.
  GetDlgItem $0 $HWNDPARENT 1
  ShowWindow $0 ${SW_SHOW}
  GetDlgItem $0 $HWNDPARENT 3
  ShowWindow $0 ${SW_SHOW}
  GetDlgItem $0 $HWNDPARENT 2
  ShowWindow $0 ${SW_SHOW}
FunctionEnd

Section "Uninstall"
  RMDir /r "$InstDir\jre-21"
  Delete "$InstDir\snaken3st-bootstrap.jar"
  Delete "$InstDir\Lancer SnakeN3st Launcher.bat"
  Delete "$InstDir\icon.ico"
  Delete "$InstDir\Uninstall.exe"
  RMDir "$InstDir"

  Delete "$DESKTOP\*Launcher.lnk"

  ${If} $un.DeleteData == ${BST_CHECKED}
    RMDir /r "$APPDATA\snake-n3st"
  ${EndIf}

  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SnakeN3stLauncher"
SectionEnd
