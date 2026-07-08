# Contexte du projet SnakeN3stLauncher — fichier de reprise

Ce fichier existe parce qu'une session de travail très longue (implémentation
complète du launcher) a rempli la fenêtre de contexte d'une IA. Il résume
tout ce qu'il faut savoir pour reprendre le projet sans avoir à relire
l'historique complet. À lire en premier, avant de toucher au code.

## 1. C'est quoi, ce projet

Un launcher Minecraft desktop (Java/Swing, PAS de JavaFX/Electron/WebView —
consigne explicite de l'utilisateur) pour un serveur semi-public : login sans
mot de passe (device-flow Ed25519, pas de compte Mojang/Microsoft), dépôt de
modpacks versionnés synchronisés par hash, installation + lancement de
Minecraft. Deux repos liés :

- **`/home/rgauthier/IdeaProjects/SnakeN3stLauncher`** — ce repo, le launcher Java.
- **`/home/rgauthier/SnakeN3stLogin`** — le site Azuriom (Laravel) + plugins
  + le mod Minecraft. `site-plugin/LAUNCHER_INTEGRATION.md` y est le contrat
  API de référence (à jour, inclut les ajouts faits pendant cette session).

**État global : les 15 tâches du plan initial sont terminées.** Le launcher
compile, ses tests passent, et le flux complet a été vérifié en vrai
(pas juste en tests unitaires) contre l'instance Azuriom locale : login réel,
liste/téléchargement de modpacks réels, le vrai jar packagé `bootstrap`
téléchargeant et lançant le vrai jar packagé `launcher` depuis le serveur, et
— confirmé par l'utilisateur — un vrai modpack Forge installé et lancé de
bout en bout avec succès. Le doute qui restait sur ce point précis
(`game/README.md` le signalait comme "jamais exercé en vrai") est donc levé.

**Le repo est commité et poussé sur GitHub** (`origin/main`,
`git@github.com:Snake-N3st/SnakeN3stLauncher.git`), publiquement visible —
condition nécessaire puisque `launcher`/`common` sont GPL-3.0 (voir section 2).
Continuer à ne jamais committer/pousser sans demande explicite de
l'utilisateur ; c'est lui qui gère ses propres commits/releases.

## 2. Architecture : reactor Maven à 3 modules

```
SnakeN3stLauncher/
├── common/      utilitaires zéro-dépendance partagés (AppDirs, Hex, Sha256, AtomicFiles, Log)
│                → licence GPL-3.0 (voir common/LICENSE)
├── bootstrap/   petit stub distribué aux joueurs, AUCUN code GPL
│                → propriétaire, All Rights Reserved (voir bootstrap/LICENSE)
└── launcher/    l'application complète, licence GPL-3.0 (voir launcher/LICENSE)
```

**Pourquoi cette séparation** : `launcher` embarque FlowUpdater,
OpenLauncherLib, et la dépendance transitive de FlowUpdater
`flowmultitools` (installation/lancement de Minecraft), tous les trois
confirmés **GPL-3.0** (pas "dual GPL/LGPL" comme je l'avais cru au début —
vérifié en lisant leurs vrais fichiers LICENSE/pom). `bootstrap` ne doit
jamais dépendre de `launcher` ni embarquer une lib GPL : à l'exécution, il
télécharge le jar complet du launcher (déjà buildé avec ses dépendances GPL)
depuis le site, le lance comme nouveau processus, puis se termine
immédiatement — jamais deux JVM actives en même temps. `common` existe
uniquement parce que `bootstrap` et `launcher` doivent absolument être
d'accord sur l'emplacement des dossiers de données (sinon le hand-off entre
les deux casse).

**Décision de licence finale (prise explicitement par l'utilisateur)** :
- `launcher` reste GPL-3.0 — remplacer FlowUpdater/OpenLauncherLib pour
  sortir de la GPL a été jugé disproportionné (le pipeline "processors" de
  l'installeur Forge/NeoForge à lui seul serait des semaines de travail),
  et l'alternative "le bootstrap télécharge les dépendances GPL depuis
  Maven Central à la volée + les lie via `-cp`" a été explicitement écartée
  après analyse (n'aurait probablement pas évité l'obligation GPL selon la
  position habituelle de la FSF, en plus de complexifier `BootstrapMain`
  pour rien).
- `common` est GPL-3.0 lui aussi : pas parce qu'il dépend de code GPL (il
  n'en dépend pas), mais parce qu'une copie compilée de son code se
  retrouve embarquée dans le jar combiné `launcher`, dont le Corresponding
  Source (obligatoire pour tout destinataire du jar GPL) inclut de fait
  `common`.
- `bootstrap` reste propriétaire (All Rights Reserved, Snake N3st) : il ne
  combine jamais de code GPL (aucune dépendance vers FlowUpdater/
  OpenLauncherLib), donc aucune obligation de copyleft ne s'applique à lui
  — même s'il dépend de `common`, qui est GPL. La raison : `common` est du
  code entièrement écrit par l'utilisateur (copyright détenu à 100%), et un
  auteur peut toujours réutiliser son propre code sous des licences
  différentes selon le produit dans lequel il l'intègre (dual-licensing) —
  contrairement à FlowUpdater/OpenLauncherLib, dont l'utilisateur n'est pas
  l'auteur et dont les conditions GPL s'imposent telles quelles.
- Les plugins Azuriom (`SnakeN3stLogin/site-plugin`) restent privés sans
  problème : ils tournent côté serveur et communiquent par HTTP, jamais
  liés/compilés dans le launcher, donc totalement hors du périmètre GPL.
- Conséquence pratique de "launcher/common publics" : le repo est
  maintenant public sur GitHub (voir section 1), et l'appli elle-même
  pointe vers ce dépôt et liste les licences tierces embarquées depuis
  Paramètres > "A propos" (`ui.about.LicensesDialog`) — voir
  `ui/about/README.md` et `THIRD-PARTY-NOTICES.md`.

### Dossiers de données (`util.AppDirs`)

Racine `snake-n3st` (pas `SnakeN3stLauncher`) :
- Linux : `$HOME/.local/share/snake-n3st`
- macOS : `~/Library/Application Support/snake-n3st`
- Windows : `%APPDATA%\snake-n3st`

Sous-dossiers : `cache/launcher/` (jars téléchargés par bootstrap),
`instances/<slug>/` (un dossier de jeu complet et indépendant par modpack),
`secure/` (clé privée chiffrée), `logs/`, `config.json`.

**Piège vérifié en pratique** : la variable d'env `HOME=...` ne change PAS
`user.home` pour la JVM sur ce système. Pour tester avec un dossier isolé,
utiliser `-Duser.home=/chemin/isolé` sur la ligne de commande Java, jamais
`HOME=...` en préfixe de commande.

### Propriétés système JVM

`sn3.baseUrl` (défaut `https://snake-n3st.fr`, pas encore déployé — utiliser
`http://127.0.0.1` en local) et `sn3.clientId` (obligatoire, pas de défaut).
Lues par `Main` ET `BootstrapMain` ; bootstrap les retransmet telles quelles
au jar complet qu'il lance.

`sn3.dataDir` (optionnel) — remplace entièrement l'emplacement par défaut
des données du launcher (`util.AppDirs`, normalement
`~/.local/share/snake-n3st` sur Linux) par un dossier arbitraire. Sert
précisément à séparer les données d'un run de test de celles d'une
installation réelle sur la même machine (config, clé chiffrée, jars
`bootstrap` en cache, modpacks installés) — sans ça, lancer un `launcher`
de test écrase/mélange les données réelles. Lu par `common.util.AppDirs`,
donc automatiquement valable pour `bootstrap` ET `launcher` sans dupliquer
la logique ; `BootstrapMain#spawnLauncher` le retransmet explicitement au
jar complet qu'il lance (un process enfant n'hérite PAS des `-D` du parent
tout seul, contrairement aux variables d'environnement — piège vérifié en
pratique, `sn3.baseUrl`/`sn3.clientId` doivent déjà être retransmis pour la
même raison). La run configuration IntelliJ "Launcher (test local)" (voir
section 7) l'utilise pour pointer vers
`~/.local/share/snake-n3st-test` — "Launcher (prod)" n'a volontairement
pas cette propriété, donc utilise le vrai dossier par défaut.

**Alternative sans ligne de commande** : `bootstrap/BootstrapMain#loadPropertiesFileNextToJar`
charge un fichier `bootstrap.properties` s'il est présent à côté du jar
`bootstrap` au démarrage, et pose chacune de ses clés comme propriété
système JVM (sauf si déjà définie via `-D`, qui reste prioritaire) —
pensé pour un jar à double-clic ou un raccourci bureau/association de
fichier, qui n'ont justement pas de ligne de commande où ajouter des `-D`.
Vérifié en conditions réelles (vrai jar, zéro argument `-D`, les trois
propriétés lues depuis le fichier et correctement retransmises au
`launcher` lancé). Un vrai `bootstrap.properties` (avec un vrai
`client_id`) ne doit jamais être commité — même logique que `.clientId`,
`.gitignore` l'exclut déjà (`/bootstrap.properties` à la racine, et
`bootstrap/target/` de toute façon ignoré).

## 3. Décisions techniques et pièges à connaître

- **Ed25519** : `net.i2p.crypto:eddsa` (licence **CC0**, pas Apache comme je
  l'avais cru au début — corrigé dans `THIRD-PARTY-NOTICES.md`), PAS l'API
  native `java.security` : le JDK peut signer avec un seed brut mais n'expose
  aucun moyen portable de dériver la clé publique à partir d'un seed externe.
  Cette lib est déjà utilisée et cross-validée côté mod
  (`SnakeN3stLogin/mod/common/.../Ed25519Signer.java`).
- **UUID offline** : `UUID.nameUUIDFromBytes(("OfflinePlayer:"+username))` —
  convention vanilla standard, vérifiée contre une implémentation Python
  indépendante. Le username utilisé pour le lancement doit être **exactement**
  celui renvoyé par `/api/launcher-auth/player/username` (le plugin serveur
  `AuthMeBridge.java` fait le lien via `player.getName()`).
- **OpenLauncherLib** : le vrai package Java est `fr.theshark34.openlauncherlib.*`
  (PAS `fr.flowarg.openlauncherlib`, malgré le groupId Maven `fr.flowarg`) —
  plus une classe compagnon `fr.flowarg.openlauncherlib.NoFramework`, l'API de
  lancement moderne utilisée ici (gère NeoForge explicitement, lit le JSON de
  version déjà installé plutôt que d'exiger un `GameType` par version). Voir
  `game/openlauncherlib/OpenLauncherLibGameLaunchService.java`.
- **Vérification de hash** : `util.AtomicFiles.writeVerified()` écrit dans un
  fichier temporaire, vérifie le hash, et NE déplace vers la destination
  finale QUE si ça correspond (jamais l'inverse — j'ai trouvé et corrigé un
  vrai bug là-dessus pendant l'implémentation, voir l'historique de
  `ModpackFileDownloader`).
- **Path traversal** : `modpack.InstancePaths.resolveSafely()` protège contre
  un chemin serveur malveillant (`../../..`) avant toute écriture/suppression
  liée à un modpack — défense en profondeur, le serveur est présumé de
  confiance mais ça ne coûte rien.
- **VANILLA n'est PAS rejeté** (revert d'une décision précédente) : une
  première passe avait fait échouer `FlowUpdaterGameInstallService.install()`
  explicitement pour `ModLoader.VANILLA` (raisonnement : sans le mod
  `universalpacket`, l'auth in-game via `-Dsn3.token` ne fonctionne pas).
  L'utilisateur a explicitement demandé de revenir dessus : "on ne doit
  retirer le support vanilla que si il pose problème" — vanilla est donc de
  nouveau installé/lancé normalement comme n'importe quel loader ; un client
  vanilla n'aura simplement pas le login in-game passwordless, ce qui n'est
  pas la responsabilité du launcher à bloquer.
- **Vrai bug corrigé : crash `Could not install X.X.X (FORGE)`** —
  `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` dans
  `fr.flowarg.flowupdater.versions.forge.ForgeVersion`. Cause : FlowUpdater
  attend `loaderVersion` au format `"<mcVersion>-<forgeBuild>"` (ex.
  `"1.20.1-47.2.20"`, il fait un `split("-")` et indexe `data[1]`), alors que
  le manifest ne fournit que le numéro de build brut (ex. `"47.2.20"`).
  Corrigé dans `FlowUpdaterGameInstallService` (`mcVersionPrefixed`) en
  préfixant nous-mêmes `mcVersion + "-"` avant d'appeler FlowUpdater, plutôt
  que d'exiger que le site stocke la chaîne combinée. Même traitement pour
  NeoForge, mais seulement pour sa toute première version (1.20.1) qui
  partageait cet ancien format — les versions NeoForge modernes (ex.
  `"20.4.237"`) sont autonomes et passées telles quelles. Fabric n'a jamais
  eu ce problème (sa version est toujours autonome).
- **Téléchargement des fichiers de modpack parallélisé** : `ModpackSyncEngine`
  téléchargeait les fichiers un par un (très lent pour un modpack avec
  beaucoup de petits fichiers, chaque fichier payant un aller-retour complet
  avant que le suivant démarre). Il utilise maintenant un pool de threads
  borné (6 téléchargements simultanés max) via `downloadAllInParallel` ;
  `awaitAll` attend tout puis relance la première erreur rencontrée avec son
  type d'origine (`IOException`/`ModpackApiException`/`InterruptedException`).
- **Logo de modpack enfin affiché** : `ModpackSummary`/le manifest portaient
  déjà un `image` (URL), mais ni `ModpackCardView` ni `ModpackDetailPage` ne
  le chargeaient jamais — toujours le placeholder-lettre de `AvatarPanel`.
  `AvatarPanel` sait maintenant afficher une vraie image (`setImage`, même
  logique que `LogoPanel`) ; `LauncherApp` la pré-télécharge (en parallèle
  pour la liste, `fetchModpackLogos`) avant de construire l'UI, comme pour
  le logo client/avatar compte.
- **`LauncherApp.start()` ne tourne plus sur l'EDT** (session UI/UX) :
  `Main.main()` appelle `app.start()` directement (thread principal, pas
  `SwingUtilities.invokeLater`), pour que le fetch bloquant du logo/nom du
  client (et, si une session existe déjà, des infos joueur) puisse se faire
  *avant* la construction de `LoginFrame`/`LauncherFrame` sans geler l'EDT —
  la fenêtre n'affiche donc jamais un placeholder puis un flash vers le
  vrai logo. `LauncherApp` bascule ensuite sur l'EDT via `invokeLater` pour
  construire les fenêtres. `PlayerInfo` (username/role/email/avatar) est
  fetché une seule fois, au login et au démarrage — jamais au clic sur
  l'icône de profil (`AccountPopover` ne fait plus aucune requête réseau).
- **Logout** : reconstruit et réaffiche `LoginFrame` (`showLogin()`) au lieu
  de `System.exit(0)` — bug signalé, le bouton ne rouvrait pas la page de
  connexion.
- **Bug corrigé récemment** : `DeviceAuthServiceTest` appelait le vrai
  `Desktop.browse()` à chaque exécution des tests (pas mocké), ouvrant des
  onglets de navigateur réels vers des tokens de test bidons → 404 dans le
  vrai navigateur de l'utilisateur, plusieurs dizaines de fois pendant la
  session. Corrigé en extrayant `auth.BrowserOpener` (interface à une
  méthode), injectable via un 4e constructeur de `DeviceAuthService`. **Toute
  nouvelle classe qui touche `Desktop`/réseau/fichiers doit être conçue pour
  rester mockable dans les tests dès le départ.**
- **Logo réel du client** (nouveau) : le launcher affiche maintenant le vrai
  nom/logo du `LauncherClient` (au lieu d'un placeholder générique "SN") dans
  `LoginFrame` et `LauncherFrame`. Nouveau endpoint site non signé
  `GET /api/launcher-auth/client?client_id=...` → `{"name":..., "image":...}`
  (`ClientInfoController` côté site). Côté launcher :
  `auth.ClientInfo` (record), `LauncherAuthApiClient#fetchClientInfo`,
  `ui.common.RemoteImages.tryLoad(url)` (best-effort, ne lève jamais,
  `null` si échec — appelant doit rester sur le placeholder),
  `ui.LogoPanel#setImage(BufferedImage)` (bascule logo réel/placeholder),
  `LauncherApp#fetchClientBranding(...)` (fetch en arrière-plan + callback
  EDT), câblé dans `showLogin()` et `showShell()`.
- **Bug CSS "le logo prend la moitié de la page" (site, corrigé deux fois)** :
  la vraie cause racine est la règle globale d'Azuriom
  `img { max-width: 100%; height: auto; }` dans
  `/server/azuriom/public/assets/css/base.css` (fichier core, pas touché) —
  un attribut HTML brut `height="64"` sur une balise `<img>` a la PLUS BASSE
  priorité de toute la cascade CSS, donc n'importe quelle règle externe même
  non qualifiée (`img{}`) l'écrase silencieusement. Un premier correctif
  (session précédente) n'avait patché que les formulaires admin
  (`_form.blade.php` ×2) — insuffisant, le bug existait ailleurs. Corrigé
  partout en remplaçant tout `height="N"`/`width="N"` par un `style="max-height:
  Npx; max-width: 100%; object-fit: contain;"` inline (qui, lui, gagne
  toujours contre un sélecteur `img` non qualifié). Fichiers touchés (les 5
  seuls `<img>` de tout le repo `SnakeN3stLogin`, vérifié par grep) :
  `site-plugin/resources/views/admin/_form.blade.php`,
  `modpacks-plugin/resources/views/admin/_form.blade.php`,
  `site-plugin/resources/views/confirm.blade.php` (la vraie page de
  consentement utilisateur — probablement celle que l'utilisateur regardait
  en signalant que le bug persistait "pas que sur la page admin"),
  `site-plugin/resources/views/admin/index.blade.php` (vignette liste
  clients), `modpacks-plugin/resources/views/admin/index.blade.php`
  (vignette liste modpacks). **Règle à respecter pour tout futur `<img>`
  ajouté n'importe où dans `SnakeN3stLogin`** : ne jamais utiliser
  `height=`/`width=` bruts, toujours un `style="max-height: ...; max-width:
  100%; object-fit: contain;"` inline.

## 4. Structure des packages (module `launcher`)

Chaque package a son propre `README.md` et `package-info.java` — **toujours
les lire avant de modifier un package**, ils expliquent le "pourquoi", pas
juste le "quoi".

```
mc.snakenest.launcher            Main.java (composition root minimal) + LauncherApp.java (orchestration réelle)
.config                          LauncherConfig (thème + playerId), ConfigStore
.crypto                          Ed25519KeyPair, KeyStorage/EncryptedFileKeyStorage, SecureFilePermissions
.net                              HttpJsonClient, JsonResponse, RawResponse, Uris
.auth                            LauncherAuthApiClient, SignedRequestSigner/SignedParams, PlayerSession,
                                  PollResult, DeviceAuthState/Listener/Service, BrowserOpener
.modpack                          ModpackApiClient, DTOs, ManifestDiffer (pur), LocalManifestStore,
                                  ModpackFileDownloader, ModpackSyncEngine, InstancePaths
.game                             ModLoader, InstallRequest/GameInstallService/GameInstallListener/InstallStep,
                                  LaunchRequest/GameLaunchService, OfflineUuids
.game.flowupdater                 FlowUpdaterGameInstallService — SEULE classe qui importe fr.flowarg.flowupdater.*
.game.openlauncherlib              OpenLauncherLibGameLaunchService — SEULE classe qui importe
                                  fr.theshark34.openlauncherlib.*/fr.flowarg.openlauncherlib.*
.news                             NewsApiClient, Post (Azuriom /api/posts, public, non signé)
.util                              HumanSize (le reste — AppDirs, Hex, Sha256, AtomicFiles, Log — est dans `common`)
.ui                                LauncherFrame, Sidebar (plus de Twitch/Discord), TopBar, ContentArea, LogoPanel
                                  (placeholder = parchemin, plus "SN"), ThemeController, NavTarget, LoginFrame
                                  (premier écran, avant toute session, message de bienvenue)
.ui.common                        Icons/VectorIcon (icônes dessinées en Java2D ; plus de twitch()/discord(), supprimées
                                  avec la fonctionnalité), IconButton, Buttons (flatIcon + iconButton), AvatarPanel
                                  (auto-centré), RemoteImages, RoundedImageIcon (avatar rond dans un bouton/label)
.ui.modpack                        ModpackSectionPage (CardLayout liste/détail), ModpackListPage, ModpackDetailPage
                                  (bouton Télécharger/Démarrer selon `installed`)
.ui.news                          NewsSectionPage, NewsListPage, NewsDetailPage
.ui.settings                      SettingsPage (thème, dossier données, logout)
.ui.account                        AccountPopover (username/role/email/avatar + logout — jamais de requête réseau
                                  au clic, tout est déjà en cache dans LauncherApp)
.devpreview                        FullShellPreview — QA manuelle, données factices, à lancer depuis l'IDE
```

Module `bootstrap` : `mc.snakenest.launcher.bootstrap` — `BootstrapMain`,
`LauncherReleaseClient`, `LauncherReleaseInfo`, `BootstrapException`.

## 5. Changements côté site (`SnakeN3stLogin`)

### 1a. `modpacks-plugin` — métadonnées Minecraft
Migration `2026_07_06_000006_...` ajoute `mc_version`/`loader`/`loader_version`
à `modpack_versions`. Exposé dans l'API manifest/versions. Formulaire admin
mis à jour. `LAUNCHER_INTEGRATION.md` section 7.2 documente les nouveaux champs.

### 1b. `site-plugin` (plugin `launcher-auth`) — versions du launcher
Nouvelle fonctionnalité pour l'auto-update du bootstrap : migration
`2026_07_06_000007_create_launcher_releases_table`, modèle `LauncherRelease`,
`LauncherReleaseStore` (stockage simple, pas de dédup comme les modpacks),
CRUD admin, API publique (non signée mais `client_id` obligatoire, throttlée) :
```
GET /api/launcher-auth/releases/latest?client_id=...
GET /api/launcher-auth/releases/{version}/download?client_id=...
```
Documenté dans `LAUNCHER_INTEGRATION.md` section 8.

### 1c. `site-plugin` — branding client + infos joueur consolidées (session UI/UX)
Deux endpoints ajoutés pour le travail de branding réel du launcher :
- `GET /api/launcher-auth/client?client_id=...` (non signé, throttlé) →
  `{"name":..., "image":...}` — `ClientInfoController`, utilisé pour afficher
  le vrai nom/logo du client sur l'écran de connexion et dans le shell.
- `GET /api/launcher-auth/player/info?...` (signé, même schéma que les 3
  endpoints `player/username|role|email` existants) →
  `{"username":..., "role":..., "email":..., "avatar":...}` en **un seul
  appel** — `PlayerInfoController::info()`, utilise `User::getAvatar()`
  (Azuriom core). Les 3 endpoints séparés restent pour compatibilité mais le
  launcher n'appelle plus que celui-ci. Documenté dans
  `LAUNCHER_INTEGRATION.md` sections 1bis et 6.

### 1d. Session "corrections diverses" (liste de 12 points de l'utilisateur, tâches #39-50)
Tout complété. Points marquants côté launcher Java :
- **Cancel/Stop** : `ModpackDetailPage.ButtonState` (IDLE/BUSY/RUNNING), le
  bouton unique change de libellé/action selon l'état ; installs/réparations
  soumis via `backgroundExecutor.submit()` (`Future` annulable, `cancel(true)`),
  jeu lancé arrêtable via `Process.destroy()`. Voir `ui/modpack/README.md`.
- **Erreurs visibles** : `ui.common.Colors.danger()` (texte rouge) partout où
  une erreur s'affiche ; le bouton d'action rapide de la liste ne cache plus
  d'erreur silencieuse (dialog + statut rouge) ; `showModpackDetail` affiche un
  `LoadingPanel` immédiatement puis un dialog d'erreur si le fetch échoue au
  lieu de logger en silence.
- **Discord Rich Presence** : `discord.DiscordPresenceService`, seule classe
  important `com.jagrosh.discordipc.*` (dépendance JitPack, commit épinglé -
  voir `pom.xml`). **Dépendance native acceptée** (transitively via
  junixsocket) pour cette fonctionnalité, décision utilisateur explicite car
  optionnelle et non bloquante si Discord absent (tout catché en
  `Throwable`). `discord_app_id` : champ optionnel sur `LauncherClient` côté
  site (admin `_form.blade.php`), exposé par `/api/launcher-auth/client`.
- **Logout invalide la clé côté site** : `LauncherAuthApiClient.revokeKey()` →
  nouvel endpoint `POST /api/launcher-auth/player/revoke` (signé), appelé en
  best-effort avant de nettoyer l'état local.
- **Traductions site** : les 2 plugins (`site-plugin`, `modpacks-plugin`) ont
  maintenant des fichiers `lang/en|fr/messages.php` complets sous le
  namespace `{plugin-id}::messages.*` ; toutes les vues blade admin
  converties de chaînes en dur vers `trans(...)`.
- **UI mineur** : centrage vertical du texte liste/détail modpack (glue
  BoxLayout), bouton refresh sur la liste (`ModpackListViewModel.onRefresh`),
  taille du cercle de sélection sidebar uniformisée
  (`IconButton(icon, tooltip, diameter)`, `Sidebar.SELECTION_DIAMETER`),
  bouton "Gérer le profil" dans `AccountPopover` (ouvre `/profile` du site).
- **Import modpack : pré-remplissage MC version/loader depuis le JSON du
  modpack** (tâche #46, `modpacks-plugin`, pas de code launcher Java) :
  `Support\ModpackManifestDetector` reconnaît `manifest.json` (CurseForge) et
  `mmc-pack.json` (MultiMC/PrismLauncher, aussi vu chez des modpacks perso
  TLauncher) dans l'archive uploadée, à la racine ou un niveau de dossier en
  dessous ; **jamais d'exception** (JSON tronqué/absent/inattendu → champs
  `null`, garde anti-zip-bomb à 2 Mo sur l'entrée lue). Appelé en AJAX
  (`fetch` + `FormData`) dès que le fichier est choisi dans
  `admin/versions/create.blade.php`, route
  `modpacks.admin.versions.detect-metadata` (`ModpackVersionController::
  detectMetadata()`) - pur confort de saisie, le formulaire reste soumis/
  validé normalement ensuite, l'admin peut toujours corriger les champs
  pré-remplis. Vérifié par un script PHP autonome (pas de suite PHPUnit dans
  ce plugin) exerçant 10 cas dont JSON corrompu/incomplet/absent/zip invalide
  /manifeste trop profond/trop volumineux - tous retournent des champs
  `null` sans jamais lever d'exception.

### 1e. Session suivante : topbar refresh partagé + états live de la liste + admin bypass
Après avoir pu retester le site local (bind mounts remontés, voir section 6),
l'utilisateur a demandé une deuxième vague de corrections :

- **Bouton "Actualiser" déplacé dans la topbar**, juste à gauche de l'avatar
  (`ui.TopBar` : `rightPanel` en `FlowLayout`, `refreshButton` + `accountButton`),
  plutôt que dans le toolbar propre à `ModpackListPage` (supprimé). Nouveau
  `LauncherFrame#setOnNavigate(Consumer<NavTarget>)`, déclenché à la fin de
  `LauncherFrame#navigate` (donc à chaque clic sidebar ou `navigateTo`), que
  `LauncherApp` utilise pour rebrancher `setOnRefresh` sur ce que la page
  affichée peut réellement rafraîchir : liste modpacks → `loadModpackList`,
  liste actualités → nouveau `loadNewsList` (extrait de l'ancien
  `buildNewsSection`), Paramètres → `null` (bouton caché). La sous-navigation
  (détail d'un modpack, détail d'une actualité) ne passe jamais par
  `navigateTo` donc ne déclenche pas ce hook - `showModpackDetail`/nouvelle
  `showNewsDetail` posent `setOnRefresh` directement (`showModpackDetail`
  se relance elle-même - idempotent ; le détail d'une actualité n'a rien à
  rafraîchir, juste caché). Bonus lié : le détail d'une actualité a
  maintenant un vrai bouton retour (`frame.showBackButton`), qui manquait
  avant.
- **Icône `refresh()` refaite, puis remplacée par un glyphe de police** :
  trois tentatives vectorielles successives (chevron ouvert en fin d'arc →
  triangle plein en fin d'arc → deux paires flèche+arc symétriques par
  point) ont toutes fini par rendre comme un blob/hameçon ambigu aux tailles
  de barre d'outils (~20-24px) - pas assez de résolution dans une poignée de
  segments de polyligne pour porter cette forme clairement à cette taille.
  Sur suggestion explicite de l'utilisateur, `Icons.refresh()` dessine
  maintenant le glyphe **"⭮" (U+2B6E)** via `Graphics2D#drawString` (police
  logique `SANS_SERIF`) plutôt qu'une forme Java2D - seul icône de la classe
  dans ce cas. Rendu net confirmé sur cette machine de dev (Linux, fallback
  vers Symbola/Noto Sans Symbols2 via les polices logiques Java) ; **pas
  vérifié sous Windows/macOS** - si le glyphe s'affiche en tofu box
  ailleurs, c'est le premier point à vérifier (couverture Unicode de la
  police système résolue par le fallback logique de Java à cet endroit).
- **`Icons.cancel()` (X)/`Icons.stop()` (carré plein)** ajoutées et câblées
  dans `ModpackDetailPage.applyButtonLabel()` pour les états BUSY/RUNNING du
  bouton principal (qui n'avaient aucune icône avant, texte seul).
- **Liste modpacks : icône d'action réactive** - `ModpackCardView` a
  maintenant le même state machine à trois états que `ModpackDetailPage`
  (IDLE/BUSY/RUNNING), au lieu d'une icône figée calculée une fois à la
  construction. `ModpackListViewModel` garde un registre `slug ->
  ModpackCardView` (`registerCard`, rempli par `ModpackListPage`) et expose
  `setCardBusy`/`setCardRunning`/`setCardInstalled` en public pour que
  `LauncherApp` pousse les changements d'état. `quickInstallAndLaunch` est
  passé de `.execute()` à `.submit()` (stocké dans le même
  `currentInstallTask` global que le bouton de la page détail) pour devenir
  réellement annulable - avant ce changement, l'action rapide de la liste
  n'était pas suivie du tout et `cancelInstall()` n'avait rien à annuler.
  Tous les points où `ModpackDetailPage` était mis à jour
  (`installAndLaunch`, `repairModpack`, `uninstallModpack`, `launchGame`,
  succès d'installation dans `doInstallAndLaunch`) mettent maintenant aussi
  à jour la carte correspondante de la liste (si elle est visible), donc une
  installation démarrée depuis la page détail se reflète aussi dans la
  liste et vice-versa - plus seulement l'endroit où l'action a été lancée.
- **Site : un admin voit tous les modpacks** - `Modpack::canAccess($user)`
  laisse passer `$user->isAdmin()` même sans rôle explicitement autorisé
  (`app/Models/User::isAdmin()`/`Role::$is_admin` d'Azuriom core, déjà
  utilisés ailleurs dans le launcher-auth). Un seul point de correction :
  `ModpackListController`, `ModpackManifestController` et
  `ModpackBlobController` passent tous par cette méthode.

Vérifié : `mvn -pl launcher -am clean test` vert, rendu visuel confirmé via
Xvfb à chaque itération de l'icône refresh (les 3 versions vectorielles
rejetées, puis le glyphe "⭮" final net à la fois zoomé et à taille réelle),
position topbar, icônes Annuler/Arrêter visibles sur un harnais de test
jetable qui force `setBusy(true)`/`setRunning(true)` directement puisque le
devpreview ne simule pas de vraie installation), bouton refresh confirmé
présent/cliquable sur Modpacks/Actualités et caché sur Paramètres. `php -l`
sur `Modpack.php`.

### 1f. Session suivante : mise à jour, JSON TLauncher réel, Discord, formulaires admin, splash bootstrap
Six demandes de l'utilisateur, dont une avec un fichier JSON réel joint
(`~/.minecraft/versions/Simple 1.20.1/Simple 1.20.1.json`) qui a servi de
cas de test bout-en-bout, pas juste de spécification :

- **"Mettre à jour" au lieu de "Démarrer"** : `ModpackDetailViewModel`/
  `ModpackCardView` ont un nouveau booléen `updateAvailable`, calculé par
  `LauncherApp#isUpdateAvailable(slug, latestVersion)` en comparant
  `StoredManifest.version()` (déjà enregistré localement à chaque sync,
  rien de nouveau à persister) contre `ModpackSummary.latestVersion()`
  (liste) / `ModpackManifest.version()` (détail). `onDemarrer` reste
  exactement la même action (sync+install(+launch)) dans les 3 états
  (Télécharger/Mettre à jour/Démarrer) - seuls le libellé et l'icône
  changent. Remis à `false` via `setUpdateAvailable`/
  `setCardUpdateAvailable` juste après un install/repair réussi, au même
  endroit que `setInstalled(true)` existant.
- **Détection JSON TLauncher corrigée** : la session précédente supposait
  à tort que les modpacks TLauncher embarquaient `mmc-pack.json` (format
  MultiMC). En réalité ils embarquent le **vrai profil de version d'un
  launcher Minecraft** (`versions/<nom>/<nom>.json`, écrit par
  l'installeur Forge/Fabric lui-même, pas un format pensé pour décrire un
  modpack) - confirmé avec le fichier réel joint (Forge 1.20.1, `mainClass
  cpw.mods.bootstraplauncher.BootstrapLauncher`, aucun `inheritsFrom`,
  version MC/Forge seulement récupérable via les arguments
  `--fml.mcVersion`/`--fml.forgeVersion` ou la lib
  `net.minecraftforge:fmlloader:1.20.1-47.4.0`).
  `Support\ModpackManifestDetector::parseMinecraftVersionProfile()`
  (nouveau) essaie dans l'ordre `inheritsFrom`, les arguments `--fml.*`,
  un scan des `libraries` (fmlloader/fabric-loader/neoforge), puis un `id`
  brut de type `"1.20.1"`. Recherche du fichier par forme de chemin
  (`.../versions/<nom>/<nom>.json`, `<nom>` identique des deux côtés),
  pas par nom fixe comme les deux autres formats. Testé avec le JSON réel
  emballé dans un zip synthétique → `1.20.1`/`forge`/`47.4.0` correct, plus
  8 autres cas (Fabric synthétique, NeoForge sans mc_version dérivable,
  vanilla nu, JSON tronqué, chemin non conforme, régression CF/MMC).
- **Discord "ne fonctionne pas"** : la chaîne de données (site → API →
  launcher) a été vérifiée correcte via un appel direct à l'API sur le
  client de test (`discord_app_id` bien renvoyé). Le vrai problème
  identifié : `connect()` ne tentait qu'**une seule fois** au démarrage -
  si Discord n'était pas encore lancé (ou redémarre en cours de session),
  plus aucune tentative n'était faite pour le reste de la session.
  `DiscordPresenceService` a maintenant son propre
  `ScheduledExecutorService` qui retente toutes les 15s
  (`IPCClient#getStatus() == PipeStatus.CONNECTED`) jusqu'à succès ou
  `close()`, et rejoue le dernier `setBrowsing()`/`setPlaying()` demandé
  dès qu'une (re)connexion réussit (`pendingActivity`). Vérifié avec un
  harnais jetable (Discord non disponible dans cet environnement) : échec
  à t=0, nouvelle tentative exactement à t=15s dans les logs, `close()`
  propre sans blocage. **Ajout demandé aussi fait** : case à cocher
  "Afficher mon statut sur Discord" dans Paramètres
  (`LauncherConfig#discordEnabled`, défaut `true`,
  `LauncherApp#setDiscordEnabled` ferme/relance la connexion à chaud).
- **Site : préremplissage version précédente** : `ModpackVersionController
  ::create()` passe `$modpack->latestVersion()` à la vue ; les 3 champs
  mc_version/loader/loader_version utilisent `old('champ',
  $previousVersion->champ ?? défaut)`. La détection JSON par upload
  (session précédente) reste prioritaire une fois un fichier choisi (elle
  écrase ces valeurs via le JS existant), c'est la valeur par défaut
  serveur qui change ici, pas l'ordre de priorité.
- **Site : redirection après création d'un modpack** : `ModpackController
  ::store()` redirige maintenant vers `modpacks.admin.versions.create` du
  modpack tout juste créé plutôt que vers la liste - un modpack à 0
  version n'est pas utilisable, l'étape suivante logique est toujours
  d'ajouter une version.
- **Bootstrap : popup de chargement** : `BootstrapSplash` (nouvelle
  classe, package-private) - fenêtre Swing non décorée minimaliste
  (titre + statut + barre de progression indéterminée), montrée dès le
  début de `BootstrapMain#main`, statut mis à jour à chaque étape
  ("Vérification...", "Téléchargement de la version X...", "Démarrage du
  launcher..."), fermée juste avant de spawn le vrai launcher. Sur erreur,
  fermée puis remplacée par une boîte de dialogue d'erreur bloquante
  (`BootstrapSplash.showFatalError`) avant `System.exit(1)` - vérifié en
  pointant vers le site local sans version de launcher enregistrée (404
  réel), la boîte d'erreur affiche bien le message. `showIfPossible()`/
  `showFatalError()` se dégradent silencieusement en no-op dans un
  environnement headless (`GraphicsEnvironment.isHeadless()`) - jamais un
  motif d'échec du vrai flux de mise à jour. Aucune dépendance nouvelle
  (Swing fait partie du JDK) donc la contrainte "bootstrap sans code GPL"
  du module reste respectée. Vérifié visuellement via Xvfb (rendu correct,
  changement de statut effectif) et via le vrai flux bout-en-bout contre
  le site local (boîte d'erreur 404 confirmée).

Vérifié globalement : `mvn clean test` vert sur les 3 modules du reactor
(`common`, `bootstrap`, `launcher`), `php -l` sur tous les fichiers PHP
touchés, `php artisan view:cache` (blade), et pour la partie site,
vérification directe en base/tinker que `latestVersion()` sur le vrai
modpack de test (`test`, 1.20.1/forge/47.4.0 - cohérent avec le JSON
TLauncher réel utilisé par ailleurs) renvoie les bonnes valeurs.

### 1g. Session suivante : vrai bug de taille sidebar, Discord toujours KO, centrage logo/avatar
- **Le rollover de la sidebar "n'était toujours pas corrigé" (à raison)** :
  la session précédente avait rendu `SELECTION_DIAMETER` partagé entre les
  3 boutons, mais ça n'a jamais réellement fonctionné. Cause racine trouvée
  en dumpant les vraies bounds des composants (pas en devinant) :
  `IconButton` appelait `setPreferredSize(diameter)` mais jamais
  `setMaximumSize`/`setMinimumSize` - dans le `BoxLayout.Y_AXIS` de
  `Sidebar`, sans maximum explicite, `getMaximumSize()` retombe sur la
  valeur calculée par le L&F (dérivée de l'icône, pas de `diameter`), ce
  qui rétrécissait silencieusement chaque bouton. Mesuré concrètement :
  un `diameter` de 64 rendait ~40px pour les icônes nav (34px) et ~32px
  pour Settings (26px) - d'où l'écart de taille constaté. Corrigé
  (`setMaximumSize`/`setMinimumSize` ajoutés) + `SELECTION_DIAMETER` monté
  à 68 ; les 3 boutons rendent maintenant identiquement à 68×68 (vérifié
  par dump direct des bounds, pas juste visuellement).
- **Discord RPC toujours KO (Linux, Discord natif .deb)** : la résolution
  du chemin du socket Unix a été vérifiée correcte en décompilant le vrai
  jar `com.jagrosh:DiscordIPC` (`Pipe.getPipeLocation()` teste
  `XDG_RUNTIME_DIR`/`TMPDIR`/`TMP`/`TEMP` dans cet ordre, retombe sur
  `/tmp`, puis `<dir>/discord-ipc-0..9`) - c'est exactement la convention
  standard, pas un bug là. Un vrai problème de robustesse trouvé et
  corrigé quand même : `IPCClient#connect()` fait un `read()` de handshake
  sans timeout ; si Discord accepte la socket mais répond lentement, cet
  appel peut bloquer indéfiniment, et comme il tournait sur l'unique
  thread du scheduler de reconnexion, un seul blocage tuait silencieusement
  toutes les tentatives futures pour le reste de la session. Chaque
  tentative tourne maintenant sur son propre thread (`connectWorker`),
  bornée à 5s. **Pas encore résolu/confirmé** : je n'ai aucun moyen de
  reproduire avec un vrai client Discord dans cet environnement - si ça ne
  fonctionne toujours pas après ce correctif, la prochaine étape est de
  lire la ligne `Log.warn` réelle dans `logs/launcher-*.log` (pas deviner
  davantage sans donnée concrète).
- **Logo global pas centré horizontalement** : `LogoPanel`/`RoundedImageIcon`
  faisaient `drawImage(image, x, y, size, size, null)` - un force-stretch
  qui déforme toute image source non carrée (un logo/wordmark client l'est
  très souvent) et donne l'impression que le sujet visuel n'est "pas
  centré" une fois écrasé, même si le carré englobant l'est. Corrigé pour
  les deux : mise à l'échelle en préservant le ratio + centrage dans le
  carré (même règle "contain, ne jamais étirer" déjà appliquée aux
  `<img>` du site - section CSS ci-dessous). Vérifié avec une image de
  test synthétique 300x100 (rouge/jaune) avant/après - rendu correctement
  centré sans déformation après coup.
- **Avatar légèrement trop petit et mal centré** : `ACCOUNT_ICON_SIZE`
  monté de 34 à 40, et le bouton compte (`TopBar`) reçoit maintenant une
  taille fixe explicite (`ACCOUNT_BUTTON_SIZE = 46`, `setPreferredSize`/
  `setMinimumSize`/`setMaximumSize`) au lieu de dépendre de la taille
  dérivée par le L&F à partir de l'icône seule - même raisonnement que le
  bug `IconButton` ci-dessus. `RoundedImageIcon` a aussi reçu le même
  correctif "contain + centre" que `LogoPanel` (les avatars sont
  conventionnellement carrés d'après le commentaire existant, mais rien ne
  garantit qu'une vraie source le soit pixel-parfait en pratique).

Vérifié : `mvn clean test` vert sur les 3 modules, dump direct des bounds
Swing réelles (pas juste captures d'écran) pour confirmer le bug sidebar
et sa correction, rendu visuel via Xvfb avec images de test synthétiques
non carrées pour logo/avatar (avant = déformé/étiré, après = ratio
préservé + centré).

### 1h. Session suivante : suite du round précédent (avatar/logo/sidebar/cercles) - le vrai bug trouvé cette fois
Feedback direct sur le round 1g : "l'avatar est mieux mais pas centré
verticalement", "le logo global toujours pas centré horizontalement",
"un peu trop augmenté le rollover", "les boutons d'action rapide de la
liste devraient être des cercles". Cette fois, chaque point a été vérifié
par dump direct des bounds Swing réelles (pas par capture d'écran seule) -
la leçon du round précédent (ne jamais se fier au raisonnement seul sur
les layouts Swing) a été appliquée systématiquement dès le départ.

- **Avatar vraiment centré verticalement** : `TopBar.rightPanel` utilisait
  `FlowLayout`, qui centre une ligne *dans sa propre hauteur naturelle*
  puis épingle cette ligne en **haut** de tout espace supplémentaire donné
  au conteneur (confirmé : `accountButton` était à `rightPanel`-relatif
  `y=0` avec 100% du jeu poussé en dessous, pas réparti haut/bas). Remplacé
  par `GridBagLayout` (contraintes par défaut = `anchor=CENTER`), qui
  centre correctement peu importe l'écart de taille - vérifié : le jeu de
  10px est maintenant réparti 5px haut / 5px bas.
- **Logo global : le vrai bug était ailleurs** - `LogoPanel` dans
  `LauncherFrame` (en-tête) rendait déjà parfaitement à 64x64 (vérifié à
  nouveau, aucun bug là), donc le round précédent (préserver le ratio
  d'aspect) était une amélioration réelle mais ne correspondait pas au
  bug persistant signalé. En testant spécifiquement `LoginFrame` (jamais
  vérifié directement avant), le vrai bug est apparu : `LogoPanel` n'avait
  jamais `setMaximumSize`/`setMinimumSize`, seulement `setPreferredSize` -
  exactement le même bug que `IconButton` (round 1g), mais dans le
  `BoxLayout.Y_AXIS` du `center` de `LoginFrame`. Un `LogoPanel(88)` y
  rendait à **452x189** au lieu de 88x88 (étiré sur toute la largeur de la
  colonne, et plus haut aussi) ! Corrigé (mêmes `setMinimumSize`/
  `setMaximumSize` ajoutés) - vérifié par dump : 88x88 exact, x=204
  (centré, à 2px près). C'est ce bug-ci, pas le rendu de l'image, qui
  causait le "toujours pas centré" - la leçon retenue : vérifier TOUS les
  points d'utilisation d'un composant partagé (`LogoPanel` est utilisé à
  la fois par `LauncherFrame` ET `LoginFrame`, dans deux layouts
  différents), pas seulement celui qu'on a déjà corrigé une fois.
- **Rollover sidebar un peu trop gros** : `SELECTION_DIAMETER` ramené de
  68 à 64 (le 64 était déjà la cible originale avant que le bug
  `IconButton` ne l'empêche de s'appliquer ; le +4 supplémentaire du round
  précédent était superflu).
- **Boutons d'action rapide de la liste : cercles, pas rectangles** -
  `ModpackCardView.actionButton` était un `JButton` avec le style
  "toolBarButton" de FlatLaf (survol carré arrondi). Remplacé par une
  sous-classe anonyme peignant son propre cercle en fond (même technique
  que `IconButton`, dupliquée volontairement - pas de "selected" à gérer
  ici, bouton à déclenchement unique, pas un toggle). A aussi fallu
  changer `actionWrapper` de `BorderLayout` à `GridBagLayout` :
  `BorderLayout.CENTER` étire son enfant pour remplir tout l'espace
  restant peu importe son max-size, ce qui aurait silencieusement annulé
  le diamètre fixe du bouton - même leçon que le bug `IconButton`, cette
  fois via un gestionnaire de layout différent. Vérifié en forçant l'état
  rollover directement sur le `ButtonModel` (le survol xdotool synthétique
  s'est révélé peu fiable dans cet Xvfb sans gestionnaire de fenêtres,
  déjà noté au round précédent).

Vérifié : `mvn clean test` vert sur les 3 modules, dump direct des bounds
réelles pour CHAQUE correctif (pas juste captures d'écran), rendu visuel
Xvfb pour confirmation finale (avatar centré, logo large centré dans
`LoginFrame`, cercle de survol visible sur le bouton d'action de la
liste).

### 1i. Session suivante : rollover à 60, et la vraie définition de "centré" pour le logo
- **`SELECTION_DIAMETER` essayé à 60** (sur demande explicite "pour voir ce
  que ça donne") - vérifié visuellement (rollover forcé sur le
  `ButtonModel` directement, le survol xdotool synthétique restant peu
  fiable dans cet Xvfb sans gestionnaire de fenêtres). Gardé à 60 (accepté
  par l'utilisateur).
- **Logo toujours "pas centré" après le fix du round précédent** - le
  fix précédent (LoginFrame, 452x189 → 88x88) était réel mais ne
  correspondait pas à ce que l'utilisateur entendait par "sa zone" dans
  `LauncherFrame` (l'en-tête). Clarification obtenue : "sa zone" = tout
  l'espace de la barre supérieure entre le bord gauche de la fenêtre et le
  bord gauche de la zone de titre, censé faire la **même largeur que la
  barre de gauche** (`Sidebar`, 82px) - pas juste le carré 64x64 du
  `LogoPanel` lui-même. Avant ce correctif, `logoPanel` était ajouté
  directement en `BorderLayout.WEST` du header, ce qui lui donnait
  exactement sa propre largeur préférée (64px), pas 82px - donc "centré
  dans son propre carré" (déjà vrai) mais pas "centré dans une zone de la
  largeur de la sidebar" (ce que l'utilisateur voulait réellement).
  Corrigé : `logoPanel` est maintenant enveloppé dans un `logoWrapper`
  (`GridBagLayout`, `setPreferredSize(Sidebar.WIDTH, ...)`) qui, lui, est
  placé en `BorderLayout.WEST` - `Sidebar.WIDTH` passé de `private` à
  package-visible pour que `LauncherFrame` puisse s'y référencer sans
  dupliquer la constante. Vérifié par dump de bounds réelles : le wrapper
  couvre `x=0..82`, exactement aligné avec `Sidebar` juste en dessous
  (`x=0..82` aussi), logo centré à `x=9` (`(82-64)/2`) - et visuellement,
  logo + sidebar forment maintenant une seule "colonne gauche" continue de
  largeur constante, comme demandé.

Vérifié : `mvn clean test` vert, dump de bounds réelles pour les deux
correctifs, capture Xvfb de la colonne gauche complète (logo + sidebar)
confirmant l'alignement visuel.

### 1j. Session suivante : 5 problèmes signalés (Gérer, largeurs de boutons, curseur texte, erreur 429)
- **Popup "Gérer" ne sauvegardait pas visuellement** : le fichier était en
  fait bien écrit sur disque (`ModpackSettingsStore`/`AtomicFiles` corrects,
  `launchGame` recharge toujours depuis le disque) - le vrai bug était que
  `ModpackDetailPage` relisait `viewModel.settings()` à chaque ouverture du
  menu "Gérer...", et `ModpackDetailViewModel` est un `record` immuable figé
  à la construction : réouvrir "Gérer" juste après avoir sauvegardé
  réaffichait donc les anciennes valeurs. Corrigé avec un champ mutable
  `currentSettings` sur `ModpackDetailPage`, mis à jour à chaque
  sauvegarde.
- **Boutons "Gérer le profil"/"Se déconnecter" de largeurs différentes** :
  chaque `JButton` se dimensionne sur son propre texte par défaut - rien ne
  les forçait à s'aligner. Corrigé (`AccountPopover.matchWidth`, aligne les
  deux sur la plus large des deux tailles naturelles) - vérifié par dump
  direct (`preferredSize` désormais identique : 134x26 pour les deux).
- **Curseur texte (caret) visible en cliquant sur la description d'un
  modpack** : `description.setEditable(false)` seul n'empêche pas le focus
  clavier ni le caret clignotant au clic. Ajouté `setFocusable(false)` -
  un texte en lecture seule n'a pas besoin d'être focusable du tout.
- **Bordure de la liste des modpacks "révélée" accidentellement** : pas
  identifié avec certitude. Hypothèse testée et **écartée** : un anneau de
  focus FlatLaf autour du bouton d'action circulaire (ajouté au round
  précédent) qui aurait débordé visuellement sur la carte/liste - vérifié
  par focus forcé directement sur le `ButtonModel` (pas de survol xdotool
  peu fiable), aucun anneau visible (`setBorderPainted(false)`/
  `setFocusPainted(false)` fonctionnent comme prévu). Reste à investiguer -
  besoin d'une capture d'écran ou d'étapes de reproduction plus précises la
  prochaine fois que ça se reproduit.
- **429 en spammant "Actualiser" sur la page d'un modpack laissait la page
  bloquée** : `LauncherApp#showModpackDetail`, en cas d'échec du fetch du
  manifeste, faisait `back.run()` (retour à la liste) + `JOptionPane` modal
  - lisait comme une page cassée sans possibilité évidente de réessayer.
  Nouveau `ui.common.ErrorPanel` (message d'erreur + boutons Retour/
  Réessayer, même logique que `LoadingPanel` mais pour l'état d'échec) qui
  remplace maintenant le contenu de la page in-place ; "Réessayer" relance
  simplement `showModpackDetail` (idempotent). Le bouton "Actualiser" de la
  topbar continue aussi de fonctionner comme réessai implicite (déjà lié à
  la même méthode dès le début de `showModpackDetail`). Message affiché en
  HTML échappé (`escapeHtml`) uniquement pour obtenir un retour à la ligne
  à largeur fixe - `JLabel` brut ne retourne jamais à la ligne.

Vérifié : `mvn clean test` vert sur les 3 modules, dump direct des bounds/
tailles réelles pour les boutons de `AccountPopover`, rendu Xvfb de
`ErrorPanel` (message + boutons Retour/Réessayer visibles et lisibles).

### Bug CSS répété "le logo prend la moitié de la page" (déjà corrigé, à ne pas réintroduire)
Cause racine : la règle globale d'Azuriom `img { max-width: 100%; height:
auto; }` (`base.css` core) écrase silencieusement tout attribut HTML brut
`height="..."` (priorité de cascade CSS la plus basse qui existe). Corrigé
partout où ça existait dans `SnakeN3stLogin` (`_form.blade.php` ×2,
`confirm.blade.php`, `index.blade.php` ×2 dans `site-plugin`/`modpacks-plugin`)
en remplaçant par un `style="max-height: Npx; max-width: 100%; object-fit:
contain;"` inline. **Règle pour tout futur `<img>` ajouté dans ce repo** :
jamais de `height=`/`width=` bruts, toujours ce `style=` inline.

## 6. Environnement de test local (confirmé fonctionnel)

- Azuriom local servi sur **`http://127.0.0.1`** (port 80), code source sous
  `/server/azuriom` (chemin disque, pas un préfixe d'URL).
- Plugins installés : `cloudflare`, `discord-login`, `launcher-auth`,
  `modpacks`, `ssh-manager`.
- Un `LauncherClient` de test existe déjà : nom `dev-test-launcher-client`,
  `client_id = QnKK3ntjDXHfQ5PhQCQxMJpR85LNQqd2`.
- Un modpack réel nommé **`test`** existe dans la DB locale actuelle (loader
  Forge 1.20.1 - `aventure-ultime`, mentionné dans une version antérieure de
  ce fichier, n'y existe plus/pas actuellement).
- Un vrai compte admin existe (`User::first()`, username `RaphaelGF11`).
- **`/server/azuriom/plugins/{launcher-auth,modpacks}` sont des bind mounts**,
  pas des copies : `server-azuriom-plugins-launcher\x2dauth.mount` et
  `server-azuriom-plugins-modpacks.mount` (unités systemd, `enable --now`,
  donc survivent à un reboot) montent respectivement
  `SnakeN3stLogin/site-plugin` et `SnakeN3stLogin/modpacks-plugin` en bind -
  éditer les fichiers dans `SnakeN3stLogin` se reflète directement sur le
  site local, aucune synchro manuelle nécessaire. `nginx-local.service` et
  `php8.3-fpm.service` ont un drop-in (`Wants=`+`After=` sur ces deux unités
  `.mount`) pour ne jamais démarrer avant que les mounts soient faits. Si le
  site local semble soudain vide/cassé après un reboot ou un `systemctl
  daemon-reload`, vérifier `systemctl is-active
  'server-azuriom-plugins-launcher\x2dauth.mount'
  server-azuriom-plugins-modpacks.mount` avant de chercher ailleurs -
  disparaître était le symptôme exact qui a motivé leur mise en place
  (aucune trace n'existait avant dans le dépôt/systemd, recréées via
  `pkexec` sur demande explicite de l'utilisateur).
- **Piège repéré (pas un bug launcher)** : `.env` du site local a
  `APP_URL=http://127.0.0.1:8087`, alors que le site est en pratique servi
  sur `http://127.0.0.1` (port 80). Ça n'affecte pas les vraies réponses API
  (Laravel dérive `url()` de la requête HTTP entrante, donc `/api/...` reste
  correct), mais **`php artisan tinker`** n'a pas de contexte requête et
  retombe sur `APP_URL` — un `$model->imageUrl()` inspecté depuis tinker
  affichera à tort le port 8087. Ne pas se fier à une URL d'image obtenue
  via tinker pour diagnostiquer un problème d'affichage d'image ; toujours
  vérifier via une vraie requête HTTP.
- Migrations : `cd /server/azuriom && php artisan migrate --path=plugins/<plugin>/database/migrations --force`
- Debug/fixtures : `php artisan tinker --execute="..."`
- Pour simuler l'approbation d'un challenge sans navigateur (comme fait
  pendant toute cette session) :
  ```php
  $challenge = \Azuriom\Plugin\LauncherAuth\Models\LauncherChallenge::where('token', '<token>')->first();
  $keyPair = \Azuriom\Plugin\LauncherAuth\Support\Ed25519::generateKeyPair();
  $challenge->update(['user_id' => 1, 'status' => 'approved',
      'private_key' => bin2hex($keyPair['seed']), 'public_key' => bin2hex($keyPair['publicKey'])]);
  ```
  Puis `GET /api/launcher-auth/challenge/<token>` renvoie la clé une fois.

## 7. Commandes utiles

```bash
mvn clean package                 # build les 3 modules + jars shadés
mvn test                          # tests (common, bootstrap, launcher)

java -Dsn3.baseUrl=http://127.0.0.1 -Dsn3.clientId=<id> -Dsn3.dataDir=~/.local/share/snake-n3st-test \
     -jar launcher/target/snaken3st-launcher-*.jar

java -Dsn3.baseUrl=http://127.0.0.1 -Dsn3.clientId=<id> -Dsn3.dataDir=~/.local/share/snake-n3st-test \
     -jar bootstrap/target/snaken3st-launcher-bootstrap-*.jar
```

`-Dsn3.dataDir` est optionnel - l'omettre revient au dossier par défaut de
l'OS (`util.AppDirs`). L'ajouter pour un run de test évite que ses données
(clé, config, modpacks installés) ne se mélangent avec une vraie
installation sur la même machine.

QA manuelle sans réseau/auth : lancer `mc.snakenest.launcher.devpreview.FullShellPreview`
directement depuis l'IDE (argument `light` pour le thème clair, sinon sombre).

Deux run configurations IntelliJ existent déjà sous `.idea/runConfigurations/`
(`Launcher (test local)` et `Launcher (prod)`, module `launcher`,
`mc.snakenest.launcher.Main`) — la première pointe vers
`-Dsn3.baseUrl=http://127.0.0.1` et `-Dsn3.dataDir=~/.local/share/snake-n3st-test`
(dossier de données séparé), la seconde vers l'URL de prod et le dossier de
données par défaut (pas de `-Dsn3.dataDir`, volontairement). Pratique pour
lancer/déboguer depuis l'IDE sans reconstruire la ligne de commande à la
main, et sans risquer d'écraser de vraies données en testant.

Des tests nommés `*LocalAzuriomSmokeTest` tapent le vrai serveur local si
joignable (s'auto-skip sinon via `Assumptions`) — normal, pas une erreur si
le serveur local n'est pas up sur une autre machine.

## 8. Ce qui n'est PAS fait / limites connues

- **Installation/lancement Minecraft réel jamais testé en pratique** (juste
  vérifié par lecture attentive de la vraie API des libs via `javap` — voir
  `game/README.md` pour le détail exact de ce qui est vérifié vs pas).
- Page de paramètres par modpack : implémentée (Gérer/Réparer/Désinstaller,
  voir `ui.modpack.ModpackDetailPage`/`modpack.ModpackSettings`), plus un
  callback vide.
- Icônes : dessinées en Java2D (voir `ui.common.Icons`), pas de vrais assets
  Lucide/Simple Icons bundlés. Les raccourcis Twitch/Discord de la sidebar
  ont été retirés (pas juste laissés en placeholder) pendant la session
  UI/UX — plus de glyphes twitch()/discord() dans `Icons` du tout.
- Sur l'instance locale, le modpack de test réel s'appelle **`test`** (pas
  `aventure-ultime` comme documenté dans une version précédente de ce
  fichier — vérifié via `php artisan tinker` : `aventure-ultime` n'existe
  plus/pas dans la DB actuelle). C'est ce modpack `test` qui a servi à
  reproduire et corriger le crash Forge (voir section 3).
- Comparaison "taille manifest" vs "taille réellement occupée sur le disque"
  (visible sur la maquette 3) non implémentée — seule la taille du manifest
  est affichée.
- Jamais testé sur Windows/macOS réels (uniquement raisonné : chemins
  `AppDirs`, permissions ACL Windows).
- Pas de mutualisation de fichiers entre instances de modpacks (choix
  délibéré : simplicité avant optimisation disque).

## 9. Consignes et préférences de l'utilisateur à respecter

- **Sécurité avant tout** : signaler toute faille repérée, même hors du
  périmètre direct de la tâche demandée.
- Code propre, orienté objet, pas de classes géantes, Javadoc plutôt que
  commentaires classiques, un `README.md` par package.
- Pas d'abstraction prématurée : "3 lignes similaires valent mieux qu'une
  abstraction pour un cas hypothétique."
- L'utilisateur veut tester l'UI lui-même : privilégier des points d'entrée
  de test manuel (`devpreview`) plutôt que des tests UI automatisés ; les
  tests JUnit restent bienvenus pour la logique pure.
- Multiplateforme réel Windows/macOS/Linux, aucun traité en citoyen de
  seconde zone.
- Très attentif aux droits d'auteur/licences des dépendances et assets — voir
  `THIRD-PARTY-NOTICES.md`, à maintenir à jour si une dépendance change.
- **Ne jamais faire de capture d'écran plein écran/root** (`import -window root`
  sans découpage précis) — un incident s'est produit où le bureau entier de
  l'utilisateur (autres fenêtres, onglets de navigateur, conversations) a été
  capturé par erreur. Toujours cibler/découper précisément la fenêtre de
  l'application testée.
- Ne jamais committer/pousser sans demande explicite (le repo est déjà
  commité/poussé sur GitHub par l'utilisateur lui-même — ça ne change pas
  la règle pour les futures modifications).
- L'utilisateur écrit parfois avec des soucis d'encodage (é/è/à remplacés par
  des chiffres comme "2"/"3") — c'est un problème de saisie, pas une
  préférence linguistique ; comprendre le message malgré ça.

## 10. Où trouver le reste

- Plan original détaillé (contexte/rationale complet, y compris les
  itérations de discussion avec l'utilisateur) :
  `/home/rgauthier/.claude/plans/mellow-meandering-nebula.md`
- Contrat API de référence : `SnakeN3stLogin/site-plugin/LAUNCHER_INTEGRATION.md`
- Licences tierces : `THIRD-PARTY-NOTICES.md` (racine de ce repo)
- `README.md` (racine de ce repo) : instructions de build/run pour un humain
