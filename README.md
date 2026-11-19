# BeezzCraft

**Колесо крафта для Minecraft (Fabric).** Alt + ПКМ по предмету — и перед тобой всё, что из него можно скрафтить.
Не надо помнить рецепты и листать книгу рецептов.

![Колесо крафта](docs/wheel.jpg)

## Скачать

| Minecraft | Файл |
|---|---|
| 26.3 | [beezzcraft-1.0.1+26.3.jar](https://github.com/erkinpaw/beezzcraft/releases/download/v1.0.1/beezzcraft-1.0.1+26.3.jar) |
| 26.2 | [beezzcraft-1.0.1+26.2.jar](https://github.com/erkinpaw/beezzcraft/releases/download/v1.0.1/beezzcraft-1.0.1+26.2.jar) |
| 26.1 – 26.1.2 | [beezzcraft-1.0.1+26.1.jar](https://github.com/erkinpaw/beezzcraft/releases/download/v1.0.1/beezzcraft-1.0.1+26.1.jar) |

Все версии — на странице [Releases](https://github.com/erkinpaw/beezzcraft/releases) и на сайте
[beezzland.com](https://beezzland.com/ai-mods/beezzcraft-koleso-krafta.html).

**Установка:** [Fabric Loader](https://fabricmc.net/use/installer/) + [Fabric API](https://modrinth.com/mod/fabric-api)
для своей версии, jar мода — в папку `mods`. Мод только клиентский: на сервер ставить не нужно, работает и на
обычных (ванильных) серверах.

## Что умеет

- **Alt + ПКМ** по предмету в инвентаре (рецепты 2×2) или у верстака (3×3) — колесо всего, что из него делается. Alt можно сменить: **Настройки → Управление → Привязки клавиш → BeezzCraft**.
  Золотые кнопки — хватает ресурсов, серые — чего-то не хватает.
- **ЛКМ** — скрафтить 1 раз, **Ctrl** — ×10, **Shift** — сколько хватит ресурсов.
- **Колёсико мыши** — варианты одной вещи: заборы из разного дерева, факел / факел душ / медный факел, уровни инструментов.
  Мод запоминает последний выбранный вариант.
- **Все рецепты игры**, даже не открытые в книге рецептов (факел до каменной кирки).
- **Очередь крафта** (включается шестерёнкой ⚙): ЛКМ по серому рецепту — и он скрафтится сам, когда появятся ресурсы.
- **«Нужно добыть»** — сколько чего не хватает для очереди, в инвентаре и на экране во время игры.
- Русский и английский язык.

![Варианты колёсиком мыши](docs/variants.jpg)
![Верстак: железная кирка](docs/table-iron.jpg)
![Очередь крафта и «Нужно добыть»](docs/queue-needs.jpg)
![Настройки](docs/settings.jpg)

Мод сделан для сервера [BeezzLand](https://beezzland.com) (`play.beezzland.com`) с помощью нейросети.

---

## English

Fabric client mod for Minecraft **26.1 – 26.3**: **Alt + right click** (the key is rebindable in Options → Controls → Key Binds → BeezzCraft) on an item in the inventory or at a crafting table opens
an octagon wheel of everything you can craft from it.

- **Inner ring**: what you can craft right now. **Grey**: missing resources.
- Rings of 8, 16, 24 and 32 buttons (the outer rings split each octagon side in 2, 3 and 4), 80 per page; more go to
  pages (the «◀ 1/2 ▶» switch above the wheel or the mouse wheel).
- Variants of one thing are one button with three dots under it: items whose names end the same way (a fence of each
  wood, new woods included; torch, soul torch, copper torch), a banner of each colour, the
  tiers of a tool (wooden, stone, copper, iron, golden, diamond pickaxe). The mouse wheel over it switches variants.
  It opens on the variant you crafted last, else on one you can make (the wood further up-left in the inventory wins).
- Order: what you can make and what you craft often first, then popular things. Sticks at a crafting table: 112 recipes
  in 33 buttons.
- **Hover** a sector: a card with the result, the ingredients as icons (green tick = enough, red = how many are
  missing) and the clicks drawn as mouse and key pictures.
- **Click** crafts once, **Ctrl + click** crafts 10 times, **Shift + click** crafts as many as possible.
- The inventory shows 2×2 recipes, a crafting table shows 3×3.
- **⚙ gear** in the top left corner of the inventory: switches for the craft queue (off by default), grey recipes and
  the crafting sound. Under it the craft queue is listed: each item, how many, whether it waits for resources, and ✕
  to remove it.
- **Craft queue**: with the queue switched on, clicking a grey recipe queues it. It is crafted by itself as soon as the
  items are in your inventory, but only while nothing or your own inventory is open (never with a chest open).
  3×3 recipes wait for a crafting table. While playing, a small card in the top left corner shows what waits. Some servers forbid automatic
  crafting, which is why it is off by default.
- **What to get**: everything the queue still needs, added up (12 queued torches = 12 coal), as icons with how many
  are missing. Under the queue in the inventory and, while playing, under the queue card at the top left. Switch:
  «Resources to get» behind the gear.

All crafting recipes of the game are known, not only the ones the recipe book has unlocked (a torch unlocks only once
you hold a stone pickaxe): in singleplayer from the server, in multiplayer from the game's own data. Recipes the book
knows are crafted through it (the server lays them out); the others are laid out with ordinary clicks, taking the items
from the inventory top left first. Both work on vanilla servers.

Russian and English.

## Build and test

```
JAVA_HOME=<JDK 25> ./gradlew build runClientGameTest
```

`build/libs/beezzcraft-<version>+26.3.jar` is the mod (needs Fabric API). One source builds for every supported
version; `tools/build-all.sh` builds and game-tests 26.1, 26.2 and 26.3 and puts the jars in `dist/`. The few API
differences (the open screen, the vanilla data pack, the recipe codec) are looked up at run time.

`runClientGameTest` starts a real client and world, then walks through the mod:
- the wheel and a tooltip;
- click, Shift and Ctrl;
- the gear and the craft queue (torches made by themselves once coal arrives);
- an iron pickaxe at a crafting table.

Screenshots are saved to `build/run/clientGameTest/screenshots`. `BEEZZCRAFT_LANG=en_us` gives English ones.

The wheel textures come from `tools/make_textures.py`. If you change the geometry, change it there and in `Wheel.java`.

## License

MIT
