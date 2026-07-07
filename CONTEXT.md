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
compile, ses 68 tests passent, et le flux complet a été vérifié en vrai
(pas juste en tests unitaires) contre l'instance Azuriom locale : login réel,
liste/téléchargement de modpacks réels, et même le vrai jar packagé
`bootstrap` téléchargeant et lançant le vrai jar packagé `launcher` depuis le
serveur. Rien n'a été testé en conditions réelles pour l'installation/lancement
d'un vrai Minecraft (ça demanderait de télécharger un vrai jeu) — c'est la
principale zone non vérifiée en pratique, clairement signalée dans le code
(`game/README.md`).

**Rien n'a été commité dans git.** Tout le travail est dans l'arbre de travail
(untracked). Ne jamais committer sans demande explicite.

## 2. Architecture : reactor Maven à 3 modules

```
SnakeN3stLauncher/
├── common/      utilitaires zéro-dépendance partagés (AppDirs, Hex, Sha256, AtomicFiles, Log)
├── bootstrap/   petit stub distribué aux joueurs, AUCUN code GPL
└── launcher/    l'application complète, licence GPL-3.0 (voir launcher/LICENSE)
```

**Pourquoi cette séparation** : `launcher` embarque FlowUpdater et
OpenLauncherLib (installation/lancement de Minecraft), tous deux confirmés
**GPL-3.0** (pas "dual GPL/LGPL" comme je l'avais cru au début — vérifié en
lisant leurs vrais fichiers LICENSE). `bootstrap` ne doit jamais dépendre de
`launcher` ni embarquer une lib GPL : à l'exécution, il télécharge le jar
complet du launcher (déjà buildé avec ses dépendances GPL) depuis le site, le
lance comme nouveau processus, puis se termine immédiatement — jamais deux
JVM actives en même temps. `common` existe uniquement parce que `bootstrap`
et `launcher` doivent absolument être d'accord sur l'emplacement des dossiers
de données (sinon le hand-off entre les deux casse).

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
mvn test                          # 68 tests (common=17, bootstrap=3, launcher=48)

java -Dsn3.baseUrl=http://127.0.0.1 -Dsn3.clientId=<id> \
     -jar launcher/target/snaken3st-launcher-*.jar

java -Dsn3.baseUrl=http://127.0.0.1 -Dsn3.clientId=<id> \
     -jar bootstrap/target/snaken3st-launcher-bootstrap-*.jar
```

QA manuelle sans réseau/auth : lancer `mc.snakenest.launcher.devpreview.FullShellPreview`
directement depuis l'IDE (argument `light` pour le thème clair, sinon sombre).

Deux run configurations IntelliJ existent déjà sous `.idea/runConfigurations/`
(`Launcher (test local)` et `Launcher (prod)`, module `launcher`,
`mc.snakenest.launcher.Main`) — la première pointe vers
`-Dsn3.baseUrl=http://127.0.0.1`, la seconde vers l'URL de prod. Pratique
pour lancer/déboguer depuis l'IDE sans reconstruire la ligne de commande à
la main.

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
- Ne jamais committer sans demande explicite (rien n'est commité à ce jour).
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
