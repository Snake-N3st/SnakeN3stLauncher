# Installateur natif Windows

Un `.exe` NSIS autonome qui installe le launcher sur une machine Windows qui n'a **rien**
d'installé au préalable : ni Java, ni le launcher, ni le bootstrap. Il télécharge tout à
l'installation (JRE 21, jar du bootstrap, branding du site) et écrit un `.bat` de lancement.

Aucun `client_id` : l'installateur n'appartient à aucun `LauncherClient` du plugin, il n'utilise
que des endpoints publics (`/site-info`, `/releases/bootstrap/latest` + `download`, l'API
publique d'Adoptium). Le jar bootstrap publié embarque déjà son `client_id` dans `jvm.prop` au
moment de la publication (`BootstrapJvmPropInjector`, côté site) - l'installateur n'a donc jamais
besoin d'en connaître un.

## Fichiers

- `installer.nsi` - le script NSIS principal (UI personnalisée, téléchargements, désinstalleur).
- `installer-helper.ps1` - tout ce que NSIS ne peut pas faire lui-même : requêtes HTTPS
  (JSON + téléchargements de fichiers), conversion PNG → ICO multi-résolution, redimensionnement
  "cover" de la bannière, extraction du ZIP du JRE. Invoqué par `installer.nsi` via `nsExec`, une
  fois par action, avec un code de sortie 0/1 net.

## Pourquoi PowerShell fait TOUT le réseau, pas NSIS

Le premier jet utilisait le plugin `NSISdl` (fourni de base avec NSIS) pour les téléchargements.
Sa propre documentation dit noir sur blanc : *"HTTPS is not supported, only plain HTTP!"* -
confirmé pour de vrai (pas juste lu) avec un test Wine isolé où `NSISdl::download` restait bloqué
indéfiniment sur `https://` **et même sur du HTTP simple local**, alors qu'un script de
diagnostic minimal séparé a reproduit le même blocage sur trois URLs différentes. Le site réel
étant exclusivement en HTTPS, `NSISdl` n'était en réalité jamais utilisable ici - pas un problème
Wine, un vrai bug de conception à corriger avant tout test plus poussé.

Solution : tout le réseau (JSON **et** téléchargements de fichiers) passe par
`Invoke-RestMethod`/`System.Net.WebClient` dans `installer-helper.ps1`, qui gèrent TLS
correctement via .NET Framework (présent par défaut sur toute machine Windows 8+/10/11).
`[System.Net.ServicePointManager]::SecurityProtocol` est forcé à TLS 1.2 explicitement, certaines
anciennes builds de Windows 10 ne le négociant pas par défaut.

NSIS n'a pas de parseur JSON sans plugin tiers (voir plus bas pourquoi aucun plugin tiers n'est
utilisé) : `installer-helper.ps1` écrit donc des petits fichiers manifeste `clé=valeur` (une ligne
par clé, UTF-8 **sans BOM** - `Set-Content -Encoding UTF8` de PowerShell 5.1 ajoute un BOM qui
aurait corrompu la première clé lue) que `installer.nsi` relit avec de simples instructions de
recherche de texte (`ExtractManifestValue`), pas un vrai parseur JSON.

## Pourquoi aucun plugin NSIS tiers

Uniquement ce qui est fourni dans le paquet officiel `nsis` (`nsDialogs`, `FileFunc`, `StrFunc`,
`nsExec`, `System.dll`) plus PowerShell/.NET pour tout le reste (réseau, conversion d'images,
dézippage via `System.IO.Compression.ZipFile`). Une tentative de récupérer des plugins tiers
(`INetC`, `nsis-json`) a été bloquée par le contrôle de permissions de l'environnement de
développement ("code externe sans source de confiance explicite") - plutôt que de contourner ce
blocage, la conception a été changée pour ne plus en avoir besoin du tout.

## Déroulé

1. `.onInit` : résout `powershell.exe` (chemin `System32\WindowsPowerShell\v1.0`, avec repli sur
   le PATH), appelle `installer-helper.ps1 -Action SiteInfo` (récupère nom/logo/bannière du
   site), convertit le logo en `.ico` (icône de la fenêtre de l'installateur, posée via
   `WM_SETICON`) et la bannière en `.bmp` recadré "cover", puis récupère les métadonnées de la
   dernière version du bootstrap et du JRE 21 (Adoptium) - tailles réelles utilisées pour "Espace
   nécessaire", pas de valeur codée en dur.
2. Page personnalisée unique (`nsDialogs`, pas l'assistant standard) : bannière, titre, champ
   d'emplacement + "Parcourir", espace nécessaire/libre recalculés en direct, bouton "Installer".
3. Au clic sur "Installer" : le bouton est remplacé par une barre de progression (même
   rectangle, juste `ShowWindow` basculé) avec un label "Annuler" par-dessus - téléchargement du
   jar bootstrap, vérification SHA-256, téléchargement du JRE, vérification SHA-256, extraction,
   écriture du `.bat`, enregistrement de la clé de désinstallation.
4. Page de succès : case "Créer un raccourci sur le bureau" (cochée par défaut), bouton
   "Terminer".

Le `.bat` écrit dans le dossier d'installation :

```bat
@echo off
start "" "%~dp0jre-21\bin\javaw.exe" -jar "%~dp0snaken3st-bootstrap.jar"
```

`BootstrapMain#javaBinary()` (vérifié dans le code, pas supposé) utilise déjà
`System.getProperty("java.home")` pour relancer le launcher complet sous le même JRE que celui
sous lequel le bootstrap tourne - donc si le bootstrap tourne sous ce JRE téléchargé, le launcher
aussi, sans aucune modification côté Java.

## Installation par utilisateur, sans UAC

`RequestExecutionLevel user`, dossier par défaut `%LOCALAPPDATA%\Programs\SnakeN3st Launcher`,
clés de désinstallation sous `HKCU\...\Uninstall\SnakeN3stLauncher` (pas `HKLM`) - comme
Discord/VS Code/Slack, cohérent avec les données du launcher qui vivent déjà sous `%APPDATA%`.
Décision confirmée explicitement par l'utilisateur (pas d'admin nécessaire).

## Limite de conception connue : annulation

Chaque appel réseau/conversion passe par un seul appel bloquant `nsExec::ExecToLog`. Le résultat :
la barre de progression avance par sauts nets à chaque étape plutôt que en continu pendant un
téléchargement, et un clic sur "Annuler" n'est vérifié (`$Cancelled`) qu'**entre** deux étapes, pas
pendant. Une vraie annulation instantanée en plein téléchargement demanderait soit un plugin tiers
(exclu, voir plus haut), soit une réécriture bien plus lourde en lancement asynchrone + minuterie
de sondage (`${NSD_CreateTimer}` + un process Windows non bloquant + fichier marqueur de fin) -
non tentée faute de pouvoir la tester réellement sur cette machine (PowerShell indisponible sous
Wine ici, voir plus bas). Assumé comme compromis raisonnable plutôt que caché.

## Build

```bash
export NSISDIR=/chemin/vers/nsis/share/nsis   # si NSIS n'est pas installé système
makensis installer.nsi
```

Pour pointer un build de test vers le serveur de test (`/server/azuriom`) au lieu de la
production :

```bash
makensis -DSITE_BASE_URL=http://127.0.0.1 installer.nsi
```

(sur Linux, `makensis` utilise des options à tiret simple : `-DXXX`, `-VERSION`, etc.)

## Ce qui a été réellement vérifié, et ce qui ne l'a pas été

Cette machine est Linux - un dernier test sur une vraie machine Windows (ou VM) reste nécessaire
avant publication.

**Vérifié pour de vrai** :
- Le endpoint `/api/launcher-auth/site-info` (nouveau, plugin `launcher-toolkit-plugin`) : requête
  `curl` réelle contre `/server/azuriom`, JSON exact confirmé (`{"name":...,"logo":...,
  "background":...}`), image PNG réelle confirmée derrière l'URL du logo.
- `installer.nsi` compile sans erreur avec `makensis` (un seul avertissement bénin, attendu :
  aucune section `instfiles` n'est utilisée puisque tout le vrai travail se fait dans des
  fonctions déclenchées par clic, pas dans une Section).
- Les réponses réelles de `/releases/bootstrap/latest` et de l'API Adoptium (forme du JSON,
  présence de `checksum`/`link`/`size`) - vérifiées par de vrais appels `curl`, pas supposées.
- Sous Wine isolé (`WINEPREFIX` dédié, jamais le vrai `~/.wine` de l'utilisateur, affichage Xvfb
  dédié, jamais le vrai `:0`) : l'installateur se lance, construit sa page personnalisée, et
  invoque `powershell.exe` avec les bons arguments dans le bon ordre (`SiteInfo` → `Icon` →
  `Banner` → `BootstrapInfo` → ...) - confirmé via le vidage réel des `argv` que Wine affiche
  pour son propre stub `powershell.exe`. Le stub Wine ne fait rien de réel (pas de vraie
  conversion/téléchargement) mais retourne sans produire de manifeste valide - ce qui a permis de
  vérifier que le chemin d'erreur (`BootstrapMetaFailed`) se déclenche correctement et affiche une
  boîte de dialogue claire plutôt que de continuer silencieusement avec des données vides.
- La vérification SHA-256 (`certutil -hashfile`) et le format du manifeste (`clé=valeur`, UTF-8
  sans BOM) sont corrects par relecture attentive du code, pas par exécution réelle (voir
  ci-dessous).

**Non vérifiable sur cette machine** :
- L'exécution réelle de `installer-helper.ps1` (PowerShell n'est pas réellement présent sous
  Wine ici, seulement le stub `powershell.exe` de Wine lui-même) - donc ni la conversion
  PNG → ICO/BMP, ni les téléchargements réels, ni l'extraction du ZIP du JRE n'ont pu être
  exécutés pour de vrai.
- L'inscription réelle dans "Applications installées" (Wine n'émule le registre Windows que
  partiellement) et un raccourci bureau réellement fonctionnel.
- La réactivité réelle du bouton "Annuler" pendant un appel PowerShell bloquant (voir la section
  "Limite de conception connue" ci-dessus).

Un test complet sur une vraie machine Windows (installation propre, désinstallation, raccourci,
"Applications installées") reste à faire avant toute publication.
