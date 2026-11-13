# Item drops and magnet

Items now descend diagonally, choosing a new downward direction at regular
intervals and reflecting off the left and right walls. They pass the player's
height and disappear once fully below the screen; they no longer land or wait
for an eight-second ground lifetime. Pickup checks follow every movement
segment, including diagonal travel and wall reflections.

An equipped magnet attracts drops within a center-to-center radius. Attraction
takes priority over random descent and remains active after leaving the initial
radius. Attracted drops follow the player's current position, including upward
movement, and are collected only on contact. A drop that cannot be collected on
magnet contact converts to the configured score. Without the magnet, failed
pickups remain available until they leave the screen.

Normal/special drop probabilities and weighted item selection are unchanged.

## Balance settings

Edit `res/item-balance.properties`, then start a new game run. All settings below
must be positive; invalid values fail at load time.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `drop.fallSpeed` | 80 | Base downward speed in pixels/second; each direction uses 0.75–1.25 times this value. |
| `drop.horizontalSpeed` | 65 | Horizontal speed in pixels/second; each direction uses 0.5–1 times this value, left or right. |
| `drop.directionChangeMillis` | 700 | Time between random direction choices. |
| `equip.magnet.range` | 140 | Center-to-center attraction radius in pixels. |
| `equip.magnet.speed` | 260 | Attraction speed in pixels/second. |
| `equip.magnet.convertScore` | 50 | Score for an attracted drop that cannot be kept on contact. |

`drop.groundLifetimeMillis` remains only for compatibility with the old rules
constructor and has no effect on drop removal.

## Automated checks

Use JDK 17+ from the repository root. Keep build and save files outside the
checkout; the game stores progress beside its classes directory.

```sh
check_dir=$(mktemp -d)
mkdir -p "$check_dir/classes" "$check_dir/screenshots"
find src tests -name '*.java' > "$check_dir/sources.txt"
javac --release 17 -Xlint:all -d "$check_dir/classes" @"$check_dir/sources.txt"
java -Djava.awt.headless=true -cp "$check_dir/classes:res" item.ItemDropMagnetTest
java -Djava.awt.headless=true -cp "$check_dir/classes:res" TwoHitEnemyTest
java -Djava.awt.headless=true -cp "$check_dir/classes:res" FinalBossAttackTest
java -Djava.awt.headless=true -cp "$check_dir/classes:res" EffectIntegrationTest
xvfb-run -a java -cp "$check_dir/classes:res" GameplayStarfieldTest "$check_dir/screenshots"
java -Djava.awt.headless=true -cp "$check_dir/classes:res" item.ItemDropMagnetDemo "$check_dir/screenshots/item-drop-magnet.png"
```

The item regression checks cover downward random motion, direction changes,
wall reflections, screen exit, swept contact, magnet radius and persistent
tracking, upward attraction, and score conversion when pickup fails. The
existing game suites cover enemies, bosses, effects, rendering and pause/resume.

Validated with JDK 21 compiling for Java 17:

| Suite | Checks passed |
| --- | ---: |
| `ItemDropMagnetTest` | 904 |
| `TwoHitEnemyTest` | 166 |
| `FinalBossAttackTest` | 124 |
| `EffectIntegrationTest` | 77 |
| `GameplayStarfieldTest` (real window under Xvfb) | 29 |
| **Total** | **1,300** |

The item suite also verifies the real game's score hook: no award before
contact, default +50 and configured +73, each awarded once. Two existing
`this-escape` compilation warnings remain in `Frame` and `EnemyShip`.

The reproducible demo below records production item positions and uses the
real item renderer, ship sprite and score hook. Trails and the range circle
are diagnostic overlays, not new in-game UI. It shows random descent, nearby
attraction before contact, and full-inventory conversion after contact.

![Item drop and magnet test scenes](screenshots/item-drop-magnet.png)

## Manual checks

1. Start `engine.Core`, select Play, and skip the tutorial with S if desired.
2. Destroy enemies and watch drops drift left/right while continuing downward.
3. Let a drop miss the ship: it should pass the ship and leave the screen.
4. Equip Magnet before a new run. Approach a drop until it starts following,
   then move away; it should continue following until contact.
5. Move above an attracted drop and confirm it follows upward.
6. Fill the manual-item inventory, then attract another manual item: contact
   should remove it and award the configured conversion score once.
