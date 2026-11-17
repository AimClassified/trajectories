# Trajectories (Minecraft 1.8.9 Forge, client-side)

Predicts where bows, ender pearls, snowballs, eggs, splash potions and XP bottles will land,
using the vanilla 1.8.9 physics line for line. Client-only visual mod: works on any server.

* **Bow** follows your current draw power; HUD shows power, speed, flight time, range, damage, target.
* Block hits get a marker on the exact face; entity hits get a red hitbox.
* Key **J** toggles it. `/traj` command for options. Config: `config/trajectories.cfg`.

## Getting the .jar

**Option A – let GitHub build it (no setup):**
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder and `gradle/` folder).
2. Open the repo's **Actions** tab -> *Build mod jar* -> when it finishes, download the **Trajectories-mod-jar** artifact.
3. Unzip it. `trajectories-1.0.0.jar` is the mod.

**Option B – build locally:**
1. Install a JDK 17 (Gradle runs on it; it fetches Java 8 for the game code by itself).
2. In this folder run `./gradlew build` (Windows: `gradlew.bat build`).
3. The mod is `build/libs/trajectories-1.0.0.jar`.

## Installing
Install Forge 1.8.9 (11.15.1.2318), drop the jar in `.minecraft/mods`, launch the Forge profile.

## Dev
`./gradlew runClient` launches Minecraft with the mod from your IDE.
Template build setup adapted from nea89o/Forge1.8.9Template (see LICENSE).
