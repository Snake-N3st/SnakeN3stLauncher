# Versioning

Every module carries its **own** version, in the form `MAJEUR.MOYEN.MINEUR`.

## Quand incrémenter quoi

| Partie | Exemple | Quand |
|---|---|---|
| **MAJEUR** | `2.0.0` → `3.0.0` | **Uniquement sur demande explicite.** Jamais de soi-même, quelle que soit l'ampleur du changement. |
| **MOYEN** | `2.0.0` → `2.1.0` | Grosse mise à jour : nouvelle fonctionnalité visible, refonte, changement de comportement notable. |
| **MINEUR** | `2.0.0` → `2.0.1` | Tout le reste : corrections de bugs, ajustements, nettoyage. |

## Pourquoi ça commence à 2.0.0

Le site a déjà publié des releases `1.0` à `1.8` pendant la mise au point. Repartir à
`2.0.0` évite qu'une nouvelle version entre en collision avec une de celles-là, aussi bien
dans la table `launcher_releases` (contrainte d'unicité sur `(type, version)`) que dans la
comparaison que fait le bootstrap pour décider s'il doit se mettre à jour.

## Un module = une version

Les trois modules évoluent séparément. Corriger un bug d'interface dans `launcher`
n'oblige pas à publier un nouveau `bootstrap` ni un nouveau `common` :

- **`launcher`** — bouge souvent. C'est la version que le joueur voit dans
  *Paramètres > À propos*, et celle à publier comme release sur le site.
- **`bootstrap`** — bouge rarement (c'est tout l'intérêt de ce module : le joueur ne le
  remplace presque jamais).
- **`common`** — bouge seulement quand son propre code change.

Le `pom.xml` racine, lui, **n'est pas une version produit** : il ne versionne que la
configuration de build partagée (versions des plugins, niveau de compilation, BOM JUnit).
Il ne bouge pas à chaque release.

## Où modifier la version

Deux endroits, à garder identiques, pour un module donné :

1. le `<version>` littéral dans `<module>/pom.xml` ;
2. la propriété correspondante (`common.version` / `bootstrap.version` /
   `launcher.version`) dans le `pom.xml` racine.

Maven n'accepte pas de propriété dans le `<version>` d'un module (elle doit être
constante), d'où la répétition. Ce n'est pas un piège silencieux : si les deux divergent,
la dépendance inter-modules devient introuvable dans le réacteur et le build échoue.

### Conséquence : construire un module seul

`common` n'ayant plus la même version que les autres, une commande qui ne construit qu'un
module (`mvn -pl launcher …`) ne trouve plus `common` : il n'est ni dans le réacteur ni
forcément dans le dépôt local. Utiliser `-am` (*also make*) ou lancer depuis la racine :

```bash
mvn clean package            # racine : tout le réacteur, toujours bon
mvn -pl launcher -am test    # un module + ses dépendances internes
```

## Le launcher connaît sa version

`launcher/src/main/resources/version.properties` est rempli au build par le filtrage de
ressources Maven, à partir du `<version>` du module. `util.AppVersion#current()` le lit.

Volontairement **pas** lu depuis le `Implementation-Version` du manifeste : celui-ci
n'existe qu'une fois le jar packagé, donc un lancement depuis l'IDE ou depuis
`target/classes` ne remonterait rien, et ça dépendrait en plus du plugin shade préservant
le manifeste. Une ressource filtrée est présente dans les deux cas et traverse le shading
sans changer. En l'absence de version exploitable, `AppVersion` renvoie `dev` plutôt que
d'échouer — ne pas connaître sa version est cosmétique, jamais bloquant.

## Publier

La version publiée sur le site doit être **exactement** celle du jar, pour que « ce que le
joueur exécute » et « ce que le site annonce comme dernière version » soient la même
chaîne :

```bash
php artisan launcher-auth:release:add launcher 2.0.0 /chemin/snaken3st-launcher-2.0.0.jar
php artisan launcher-auth:release:add bootstrap 2.0.0 /chemin/snaken3st-launcher-bootstrap-2.0.0.jar
```
