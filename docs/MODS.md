# Mods for Kiln 0.3.0

Fabric mods for Minecraft 26.3 on Modrinth, as Kiln 0.3.0 runs them in Horizon, counted on the 0.3.0 build on 2026-11-04 by the catalogue (`catalogue/`, D-078). Three levels, never added up into one number: what was tried in the game, what Kiln's test stand ran, and what is only data. Everything else is in the catalogue on [horizon-kiln.cc](https://horizon-kiln.cc/mods.html).

| Level | Mods |
|---|---|
| **Works**: tried in the game | 7 |
| **Should work**, its code ran on Kiln's test stand | 276 |
| **Should work**, data only (structures) | 194 |

## Works

Tried in Horizon through Kiln, each feature seen working (README, "Mods known to run").

| Mod | Version | What works |
|---|---|---|
| [Armour Durability HUD](https://modrinth.com/mod/armour-durablity-hud) | 1.11.26+mc26.3 | Each piece of armour worn, its icon and what is left of it, on the HUD. |
| [Better Than Mending](https://modrinth.com/mod/better-than-mending) | 2.3.0 | Sneaking, a right click with a worn item that has Mending repairs it with the player's experience. |
| [Heart Crystals](https://modrinth.com/mod/heart-crystals) | 1.0.1-26.3 | Using a crystal raises max health; hearts are kept with the world and through respawns. |
| [Illager Warship](https://modrinth.com/mod/illager-warship) | 1.0.0 | Its warship generates in new worlds. |
| [Kiln Speedrun Timer](https://github.com/Manager1215/horizon-kiln/tree/main/mods/kiln-timer) | 0.2.0 | Automatic splits: Nether, Bastion, Fortress, Blind, Stronghold, End, Finish. RTA and IGT. Personal best with deltas. |
| [Show FPS](https://modrinth.com/mod/showfps) | 2.0.0 | Its FPS counter. |
| [SpongeSucc (Lava Sponge)](https://modrinth.com/mod/spongesucc) | 3.1.2-1.26.3 | A lava sponge placed beside lava takes it up and turns into a hot lava sponge. |

## Should work: its code ran on Kiln's test stand

The stand runs a mod's own code on Kiln 0.3.0's Java side with a scripted minute of play in place of the game (the title screen, a new world, an advancement, a key, the Nether and back, a pause, leaving): its entry points started, 1,200 ticks went by without an error, and it reached nothing Kiln has only the name of. That is not the same as each of its features working in the game. A feature behind a screen, a key or an item may never have run: a mod with only a `main` entry point (its items and blocks) is started and then used by nothing on the stand. "Kiln lacks" counts what the mod's code refers to that Kiln doesn't have, on paths the minute didn't reach. Nobody tried these one by one. Mods with Mixin aren't here: Kiln doesn't apply Mixin yet.

| Mod | Version | On the stand | Entry points | Kiln lacks |
|---|---|---|---|---|
| [[AdvancedChatBox] Simply Updated](https://modrinth.com/mod/advancedchatbox-simply-updated) | 26.3-1.9.1 | started and ticked | client | 32 |
| [AdvancedChatBox Reforged](https://modrinth.com/mod/advancedchatbox-reforged) | 1.2.6-26.3 | started and ticked | client | 29 |
| [AdvancedChatFilters Reforged](https://modrinth.com/mod/advancedchatfilters-reforged) | 1.3.2-26.3 | started and ticked | client | 41 |
| [AetherTune](https://modrinth.com/mod/aethertune) | 26x | started and ticked | client | 23 |
| [AH Buy Price Tracker](https://modrinth.com/mod/ah-buy-price-tracker) | 1.1.0+mc26.3 | started and ticked | client | 3 |
| [All Items by OPNiki](https://modrinth.com/mod/all-items-by-opniki) | 1.1.0 | started and ticked | client | 22 |
| [Antique Trading Ship](https://modrinth.com/mod/antique-trading-ship) | 1.0.0 | started and ticked | main | nothing |
| [Anvil Uses](https://modrinth.com/mod/anvil-uses) | 1.1 | started and ticked | main | 1 |
| [Apple Jam](https://modrinth.com/mod/apple-jam) | 1.0.3 | started and ticked | client | 5 |
| [Armor Durability](https://modrinth.com/mod/armor-durability-gregoryam) | 1.0.0 | started and ticked | client | 1 |
| [Armor Glance](https://modrinth.com/mod/armorglance) | 2.8.0 | started and ticked | client | nothing |
| [Armor Hud](https://modrinth.com/mod/armor-hud) | 3.5.0+26.3-fabric | started and ticked | client | nothing |
| [Attribute Swap Window](https://modrinth.com/mod/attributeswapwindow) | 1.1.0-26.3 | started and ticked | main | 7 |
| [Auto Greeting](https://modrinth.com/mod/auto-greeting) | 1.2.1 | started and ticked | client | 3 |
| [Auto Sprint+](https://modrinth.com/mod/auto-sprintplus) | 1.4-26.3 | drew on the HUD | client | 8 |
| [Auto Totem V2](https://modrinth.com/mod/auto-totem-v2) | 3.0.0 | started and ticked | client, main | 4 |
| [AutoMaximize](https://modrinth.com/mod/automaximize) | 1.2.0 | started and ticked | client | 3 |
| [AutoMuteOnAfk](https://modrinth.com/mod/automuteonafk) | 1.1.1 | started and ticked | client | 4 |
| [AutoSwap](https://modrinth.com/mod/autoswap) | 1.5.2 | started and ticked | client | 53 |
| [AutoTorch!](https://modrinth.com/mod/autotorch) | 1.1.1 | started and ticked | client | 13 |
| [BarSwap](https://modrinth.com/mod/barswap) | 26.3-fabric-1.0.0 | started and ticked | client | 5 |
| [Basalt Watchtower](https://modrinth.com/mod/basalt-watchtower) | 1.0.0 | started and ticked | main | nothing |
| [BatteryCraft](https://modrinth.com/mod/batterycraft) | 1.0.0-beta.5+mc26.3 | started and ticked | client | nothing |
| [Beginner Survival House](https://modrinth.com/mod/beginner-survival-house) | 1.0.0 | started and ticked | main | nothing |
| [Best Armor](https://modrinth.com/mod/best-armor) | 1.2.0 | started and ticked | client | 78 |
| [Better Equipment Smithing](https://modrinth.com/mod/better-equipment-smithing) | 1.1.1-26.3-Fabric | started and ticked | main | nothing |
| [Better Gamemode](https://modrinth.com/mod/better-gamemode) | 1.4.0 | started and ticked | client, main | 6 |
| [Better Revive X Trinkets](https://modrinth.com/mod/better-revive-x-trinkets) | 1.0.2 | started and ticked | main | 9 |
| [Better Trim Tooltips](https://modrinth.com/mod/btt) | 1.0.1 | started and ticked | client | 8 |
| [Blackwolf Library](https://modrinth.com/mod/blackwolf-library) | 1.4.1-Fabric | started and ticked | main | 14 |
| [Block Grep](https://modrinth.com/mod/blockgrep) | 26.0.1-fabric | started and ticked | client | 33 |
| [BlueMap Entities](https://modrinth.com/mod/bluemap-entities) | 1.6 | started and ticked | main | nothing |
| [Bonemeal Sugarcane](https://modrinth.com/mod/bonemeal-sugarcane) | 1.6.1.0 | started and ticked | main | 8 |
| [Bucket Fixes](https://modrinth.com/mod/bucket-fixes) | 2.0.0 | started and ticked | client | 8 |
| [Button Fix](https://modrinth.com/mod/buttonfix) | 1.1.0-fabric+26.1-26.3 | started and ticked | client | 9 |
| [Calculator](https://modrinth.com/mod/calc) | 1.1.0+26.3 | started and ticked | client | nothing |
| [Camera Snapper](https://modrinth.com/mod/camera-snapper) | 1.1.0 | started and ticked | client | 5 |
| [Challenge Timer by OPNiki](https://modrinth.com/mod/challenge-timer-by-opniki) | 1.1.0 | drew on the HUD | client | 3 |
| [Chat Guides](https://modrinth.com/mod/chat-guides) | 3.7.0 | started and ticked | client | nothing |
| [Chat Spammer](https://modrinth.com/mod/chatspammer) | 1.2.0+26.x-fabric | started and ticked | client | 2 |
| [ChatItem](https://modrinth.com/mod/chatitem) | 1.0.3+26.3 | started and ticked | main | 5 |
| [Cherry Grove Starter House](https://modrinth.com/mod/cherry-grove-starter-house) | 1.0.0 | started and ticked | main | nothing |
| [CherryVillage](https://modrinth.com/mod/cherryvillage) | 1.0.3-26.3-Fabric | started and ticked | main | nothing |
| [ClickFeeder](https://modrinth.com/mod/clickfeeder) | 1.3.0 | started and ticked | client | 22 |
| [Client Playsound](https://modrinth.com/mod/clientplaysound) | 1.0.5+26.3 | started and ticked | client | 7 |
| [clientside harvest](https://modrinth.com/mod/clientside-harvest) | 1.0.2 | started and ticked | client | 24 |
| [Close Buttons](https://modrinth.com/mod/close-buttons) | 1.0.2-26.3 | started and ticked | client | nothing |
| [Colorflow](https://modrinth.com/mod/colorflow) | 2.6.1+mc26.3 | started and ticked | client | 21 |
| [ColorMixing Mod](https://modrinth.com/mod/colormixing-mod) | 1.0.0 | started and ticked | main | nothing |
| [Command Binds](https://modrinth.com/mod/commandbinds) | 1.0.0 | started and ticked | client, main | 7 |
| [Common Network](https://modrinth.com/mod/common-network) | 26.3-1.1.1 | started and ticked | main | 14 |
| [Compressed Blocks](https://modrinth.com/mod/compressed-blocks) | 1.0.3 | started and ticked | main | nothing |
| [Config Manager](https://modrinth.com/mod/configmanager) | 1.1.3 | started and ticked | client, main | nothing |
| [ConfigLib TXF](https://modrinth.com/mod/configlib-txf) | 26.3-2.0.5-fabric | started and ticked | main | 47 |
| [Coords Copyer](https://modrinth.com/mod/c-c) | 1.0.1 | started and ticked | client | nothing |
| [Craft the Totem of Undying](https://modrinth.com/mod/craft-the-totem-of-undying) | 3.4.1 | started and ticked | main | nothing |
| [Craftable Bell](https://modrinth.com/mod/craftable-bell) | 26.3-0.1 | started and ticked | client, main | nothing |
| [Craftable Horse Armour & Saddle [CHA&S]](https://modrinth.com/mod/cha-s) | 26.3-3.1.0 | started and ticked | main | nothing |
| [Craftable Name Tag](https://modrinth.com/mod/craftable-name-tag) | 26.3-0.1 | started and ticked | client, main | nothing |
| [Craftable Notch Apple](https://modrinth.com/mod/craftable-notch-apple) | 26.3-0.1 | started and ticked | client, main | nothing |
| [Craftable Saplings](https://modrinth.com/mod/craftable-saplings) | 1.3.1 | started and ticked | main | nothing |
| [Craftable Totem of Undying](https://modrinth.com/mod/craftable-totem-of-undying) | 26.3-0.1 | started and ticked | client, main | nothing |
| [CraftCalculator](https://modrinth.com/mod/craftcalculator) | 1.7+26.3 | started and ticked | client | 50 |
| [Create: Synthetic Pressure](https://modrinth.com/mod/create-synthetic-pressure) | 26.X-1.0.2 | started and ticked | main | nothing |
| [Crop Harvest MC](https://modrinth.com/mod/crop-harvest-mc) | 2.0.0 | started and ticked | main | 41 |
| [DeathLogPlus](https://modrinth.com/mod/deathlogplus) | 1.1.0 | started and ticked | client | 15 |
| [Delight Lib](https://modrinth.com/mod/delight-lib) | 26.09.16-26.3-fabric | started and ticked | main | nothing |
| [Disable My Mods Please](https://modrinth.com/mod/disable-my-mods-please) | 1.2.0+mc26.3 | started and ticked | client | 3 |
| [Disconnecter9000](https://modrinth.com/mod/disconnecter9000) | 1.1.0+26.1-26.3 | started and ticked | client | 2 |
| [double-doors-lite](https://modrinth.com/mod/double-doors-lite) | 0.2.0+mc26.3 | started and ticked | main | 6 |
| [Durability Warner HUD](https://modrinth.com/mod/durability-warner-hud) | 1.0.0+26.3 | started and ticked | client | nothing |
| [DuraHUD](https://modrinth.com/mod/durahud) | 1.4.0+26.3 | started and ticked | client | 30 |
| [DuskCustomizer](https://modrinth.com/mod/duskcustomizer) | 1.0.1 | started and ticked | main | 21 |
| [DynamicRenderDistance](https://modrinth.com/mod/dynamicrenderdistance) | 1.3.0 | started and ticked | client | 13 |
| [EaseonCS - SilentSign](https://modrinth.com/mod/easeon-cs-silentsign) | 26.3.2.0+mc26.3 | started and ticked | client | 9 |
| [EaseonSS - BatBegone](https://modrinth.com/mod/easeon-ss-batbegone) | 26.3.0.0+mc26.3 | started and ticked | main | 1 |
| [EaseonSS - BoneHarvest](https://modrinth.com/mod/easeon-ss-boneharvest) | 26.3.0.0+mc26.3 | started and ticked | main | 9 |
| [EaseonSS - ChestPeek](https://modrinth.com/mod/easeon-ss-chestpeek) | 26.3.0.0+mc26.3 | started and ticked | main | 4 |
| [EaseonSS - DeepRefill](https://modrinth.com/mod/easeon-ss-deeprefill) | 26.3.0.0+mc26.3 | started and ticked | main | 10 |
| [EaseonSS - DropBooster](https://modrinth.com/mod/easeon-ss-dropbooster) | 26.3.0.0+mc26.3 | started and ticked | main | 5 |
| [EaseonSS - EnderBuff](https://modrinth.com/mod/easeon-ss-enderbuff) | 26.3.4.0+mc26.3 | started and ticked | main | 22 |
| [EaseonSS - HandyChest](https://modrinth.com/mod/easeon-ss-handychest) | 26.3.0.0+mc26.3 | started and ticked | main | 12 |
| [EaseonSS - LogStripper](https://modrinth.com/mod/easeon-ss-logstripper) | 26.3.0.0+mc26.3 | started and ticked | main | 46 |
| [EaseonSS - PickupRadius](https://modrinth.com/mod/easeon-ss-pickupradius) | 26.3.0.0+mc26.3 | started and ticked | main | 5 |
| [EaseonSS - PumpkinCarver](https://modrinth.com/mod/easeon-ss-pumpkincarver) | 26.3.1.0+mc26.3 | started and ticked | main | 8 |
| [EaseonSS - QuickMend](https://modrinth.com/mod/easeon-ss-quickmend) | 26.3.0.0+mc26.3 | started and ticked | main | 2 |
| [EaseonSS - XpBooster](https://modrinth.com/mod/easeon-ss-xpbooster) | 26.3.0.0+mc26.3 | started and ticked | main | nothing |
| [Easy Craft of Chainmail Armor](https://modrinth.com/mod/easy-craft-chainmail-armor) | 3.4.1 | started and ticked | main | nothing |
| [Easy Craft Saddles](https://modrinth.com/mod/easy-craft-saddles) | 2.4.1 | started and ticked | main | nothing |
| [Easy Diamond](https://modrinth.com/mod/easy-diamond) | 3.4.1 | started and ticked | main | nothing |
| [Easy Emerald](https://modrinth.com/mod/easy-emerald) | 3.4.1 | started and ticked | main | nothing |
| [Easy Netherite Ingot](https://modrinth.com/mod/easy-netherite-ingot) | 1.4.1 | started and ticked | main | nothing |
| [EasyItemSign](https://modrinth.com/mod/easyitemsign) | V1.0.0-26.3 | started and ticked | main | 11 |
| [EasyReplant](https://modrinth.com/mod/easyreplant) | 26.3 | started and ticked | main | 13 |
| [EclipseUI](https://modrinth.com/mod/eclipseui) | 1.0.5+mc26.3-fabric | started and ticked | client | 12 |
| [Effect Monitor](https://modrinth.com/mod/effect-monitor) | 1.0.8 | started and ticked | client | 11 |
| [Elytra Swapper](https://modrinth.com/mod/elytra-swapper) | 2.1.0+26.3-fabric | started and ticked | client | 6 |
| [End Villager Outpost](https://modrinth.com/mod/end-villager-outpost) | 1.0.0 | started and ticked | main | nothing |
| [Energized Power - Biomes O' Plenty](https://modrinth.com/mod/energized-power-bop) | 3.0.0+26.3.x-fabric | started and ticked | client, main | nothing |
| [Entity Tracker Hud](https://modrinth.com/mod/entity-tracker-hud) | 1.0.2+26.3 | started and ticked | client | 12 |
| [Escape from the Void with Totem of Undying](https://modrinth.com/mod/escape-from-the-void-with-totem-of-undying) | 1.07 | started and ticked | main | 11 |
| [Eymistaken's Potion HUD](https://modrinth.com/mod/eymistakens-potion-hud) | 1.0.5 | started and ticked | client | 20 |
| [FarmSpotter](https://modrinth.com/mod/farmspotter) | 1.0.0 | started and ticked | client, main | 2 |
| [Fix Calcite Deepslate and Tuff Rework](https://modrinth.com/mod/fix-calcite-deepslate-and-tuff-rework) | 1.0 | started and ticked | main | nothing |
| [FlatSavanna](https://modrinth.com/mod/flatsavanna) | 1.1.0 | started and ticked | main | nothing |
| [Forest Ruins](https://modrinth.com/mod/forest-ruins) | 1.0.0 | started and ticked | main | nothing |
| [FOV Toggle](https://modrinth.com/mod/fov-toggle) | 2.0.1-1.26.3 | started and ticked | main | nothing |
| [FPC - FixPlaceContainer](https://modrinth.com/mod/fpc-fixplacecontainer) | 1.0.1+26.3 | started and ticked | client | nothing |
| [Free Cursor](https://modrinth.com/mod/free-cursor) | 1.3.1+mc26.3 | started and ticked | client, main | 11 |
| [Friends&Foes - Flowery Mooblooms (Fabric/Quilt)](https://modrinth.com/mod/friends-and-foes-flowery-mooblooms-fabric) | fabric-3.0.3+mc26.3 | started and ticked | main | nothing |
| [Giant Cherry Blossom Bonsai Tree](https://modrinth.com/mod/giant-cherry-blossom-bonsai-tree) | 1.0.0 | started and ticked | main | nothing |
| [Giant Natural Additions](https://modrinth.com/mod/giant-natural-additions) | 1.0.0 | started and ticked | main | nothing |
| [Giant Plane Wreck](https://modrinth.com/mod/giant-plane-wreck) | 1.0.0 | started and ticked | main | nothing |
| [Gothic Church](https://modrinth.com/mod/gothic-church) | 1.0.0 | started and ticked | main | nothing |
| [Happy Ghast Overdrive](https://modrinth.com/mod/happy-ghast-overdrive) | 1.1.5+26.3 | started and ticked | main | 10 |
| [Head Lab](https://modrinth.com/mod/head-lab) | 1.0.0+26.3 | started and ticked | client, main | 26 |
| [Hitbox Toggle](https://modrinth.com/mod/hitboxtoggle) | 1.0.0 | started and ticked | client | 3 |
| [HotbarBumpers](https://modrinth.com/mod/hotbarbumpers) | 1.0.1 | started and ticked | client | 3 |
| [I18nAutoUpdateMod](https://modrinth.com/mod/i18nautoupdatemod) | 1.2.0 | started and ticked | client | 2 |
| [Illager Arena](https://modrinth.com/mod/illager-arena) | 1.0.0 | started and ticked | main | nothing |
| [Illager Viking Longship](https://modrinth.com/mod/illager-viking-longship) | 1.0.0 | started and ticked | main | nothing |
| [Illager War Trireme](https://modrinth.com/mod/illager-war-trireme) | 1.0.0 | started and ticked | main | nothing |
| [Inventory Drop](https://modrinth.com/mod/inventory-drop) | 1.1.0+26.3 | started and ticked | client | 5 |
| [InvSee](https://modrinth.com/mod/invseemod) | 1.2.2 | started and ticked | main | 47 |
| [InvShots](https://modrinth.com/mod/invshots) | 1.0.1-26.3 | started and ticked | main | 22 |
| [Iron Door Key](https://modrinth.com/mod/iron-door-key) | 1.2.2-fabric-26.3 | started and ticked | main | 9 |
| [Item Rename](https://modrinth.com/mod/widderitemrename) | V1.0.0-26.3 | started and ticked | main | 7 |
| [ItemRename](https://modrinth.com/mod/itemrename) | 1.0.3+26.3 | started and ticked | main | 4 |
| [Japanese Residence](https://modrinth.com/mod/japanese-residence) | 1.0.0 | started and ticked | main | nothing |
| [Jauml](https://modrinth.com/mod/jauml) | 3.0.0+fabric-26.3 | started and ticked | main | 12 |
| [Jesus' Intervention Just Arrived - JIJA](https://modrinth.com/mod/jija) | 1.0.9 | started and ticked | client, main | 6 |
| [JourneyMap Teams](https://modrinth.com/mod/journeymap-teams) | 26.3-1.3.1+fabric | started and ticked | client, main | 6 |
| [Jumble](https://modrinth.com/mod/jumble-mod) | 1.1+26.3 | started and ticked | client, main | 2 |
| [Jungle Treehouse Village](https://modrinth.com/mod/jungle-treehouse-village) | 1.0.0 | started and ticked | main | nothing |
| [Just Enough Breeding (JEBr)](https://modrinth.com/mod/justenoughbreeding) | 3.4.1 | started and ticked | client, main | 7 |
| [Just Enough Professions (JEP)](https://modrinth.com/mod/just-enough-professions-jep) | 130.0 | started and ticked | client | 6 |
| [KissMe!](https://modrinth.com/mod/kissme) | 1.0.0 | started and ticked | main | 4 |
| [kUI](https://modrinth.com/mod/kui) | 1.2.0+mc26.3 | started and ticked | client | 19 |
| [launch command](https://modrinth.com/mod/launch-command) | r1.4-26.3+fabric | started and ticked | main | 18 |
| [Leon's Spooky Paintings](https://modrinth.com/mod/leons-spooky-paintings) | 1.0.0 | started and ticked | main | nothing |
| [Litematica Backup](https://modrinth.com/mod/litematica-backup) | 2.0 | started and ticked | client | nothing |
| [Litematica RawMaterials](https://modrinth.com/mod/litematica-rawmaterials) | 0.5.0+26.3.x | started and ticked | main | 18 |
| [Locator Triangulation](https://modrinth.com/mod/locator-triangulation) | 1.5.0+mc26.3-fabric | started and ticked | client | 21 |
| [Low Durability Switcher](https://modrinth.com/mod/low-durability-switcher) | 1.3.1+26.3 | started and ticked | client | 3 |
| [Lower Shelves](https://modrinth.com/mod/lower-shelves) | 1.0.7 | started and ticked | client, main | 6 |
| [MCEF Modern](https://modrinth.com/mod/mcef-modern) | 0.3.3+mc26.3.jcef146.0.10 | started and ticked | client | 7 |
| [Media Controls](https://modrinth.com/mod/media-controls) | 1.2.0 | started and ticked | client, main | 9 |
| [Mend Command](https://modrinth.com/mod/mend-command) | 1.0.1+26.3 | started and ticked | main | 4 |
| [Minecraft Custom Day/Night Mod](https://modrinth.com/mod/minecraft-custom-daynight-mod) | 2.2.0 | started and ticked | main | 16 |
| [ModernConfig](https://modrinth.com/mod/modernconfig) | 1.5 | started and ticked | client | 22 |
| [More Mobs](https://modrinth.com/mod/more-mobs) | 1.5.11+mod | started and ticked | main | 9 |
| [More Nuggets](https://modrinth.com/mod/more-nuggets) | 26.3-0.1 | started and ticked | client, main | nothing |
| [Mosaics](https://modrinth.com/mod/mosaics) | 1.2 | started and ticked | main | 3 |
| [Nbt Display](https://modrinth.com/mod/nbt-display) | 1.0.2+26.3 | started and ticked | client | 3 |
| [NebsConfigLib](https://modrinth.com/mod/nebsconfiglib) | 1.0.9+26.3 | started and ticked | client | nothing |
| [Nemo's Firework Keybinding](https://modrinth.com/mod/nemos-firework-keybinding) | 26.3-1.1.3 | started and ticked | client, main | 4 |
| [Nemo's Paintings](https://modrinth.com/mod/nemos-paintings) | 26.3-2.1 | started and ticked | main | nothing |
| [Network Blocker](https://modrinth.com/mod/networkblocker) | 1.0.0+26.1.2-fabric | started and ticked | main | nothing |
| [No darkness effect](https://modrinth.com/mod/no-darkness-effect) | 0.0.2-26.2 | started and ticked | client | 2 |
| [Nobody Lib](https://modrinth.com/mod/nobodylib) | 0.1.0+26.3 | started and ticked | client, main | 22 |
| [Note Block Assistant](https://modrinth.com/mod/noteblock-assistant) | 0.2.1 | started and ticked | main | 17 |
| [Oblique](https://modrinth.com/mod/oblique) | 2.2.0+26.3 | started and ticked | client, main | 1 |
| [OBS Recording Monitor](https://modrinth.com/mod/obs-recording-monitor) | 26.09.24 | drew on the HUD | client | 14 |
| [Ocean Lily Pad Village](https://modrinth.com/mod/ocean-lily-pad-village) | 1.0.0 | started and ticked | main | nothing |
| [Old Netherite Crafts](https://modrinth.com/mod/old-netherite-crafts) | 2.0.0_26.3(Fabric) | started and ticked | main | nothing |
| [OnlySleep](https://modrinth.com/mod/onlysleep) | 1.4.4 | started and ticked | main | 57 |
| [OPAC-UI](https://modrinth.com/mod/opac-ui) | 1.1.0 | started and ticked | client, main | 25 |
| [Palette Placer](https://modrinth.com/mod/palette-placer) | 1.0.2+26.3 | started and ticked | client | 9 |
| [Panic Button](https://modrinth.com/mod/panic-button) | 1.0.0+26.3 | started and ticked | main | 5 |
| [Panorama Screenshot](https://modrinth.com/mod/panorama-screenshot) | 1.1.3-26.3 | started and ticked | client | 4 |
| [PerformanceHUD](https://modrinth.com/mod/performancehud) | 1.2.0 | started and ticked | client | 7 |
| [PianoLib](https://modrinth.com/mod/pianolib) | 1.2.3+DJP2.9.3 | started and ticked | client, main | 1 |
| [Piglin Paintings](https://modrinth.com/mod/piglin-paintings) | 1.0.0 | started and ticked | main | nothing |
| [PillarPlan: Even Spacing Guide](https://modrinth.com/mod/pillarplan-even-spacing-guide) | 0.2.3 | started and ticked | client | 8 |
| [Player Kits](https://modrinth.com/mod/player-kits) | 26.3 | started and ticked | client | 15 |
| [Pocket Tunes](https://modrinth.com/mod/pocket-tunes) | 1.0.0-26.3 | started and ticked | client, main | 9 |
| [PocketBrowser](https://modrinth.com/mod/pocketbrowser) | 1.0.0+26.3 | started and ticked | client | 62 |
| [PortalHelper](https://modrinth.com/mod/portalhelper) | 1.2-26.3 | started and ticked | client | nothing |
| [Potentials](https://modrinth.com/mod/potentials) | 0.11.1 | started and ticked | client, main | nothing |
| [Prefer OpenGL](https://modrinth.com/mod/preferopengl) | fabric-26.2-v1.0.0 | started and ticked | client | nothing |
| [pv-addon-broadcast](https://modrinth.com/mod/pv-addon-broadcast) | 1.1.1 | started and ticked | main | nothing |
| [pv-addon-groups](https://modrinth.com/mod/pv-addon-groups) | 1.1.1 | started and ticked | main | nothing |
| [pv-addon-lavaplayer-lib](https://modrinth.com/mod/pv-addon-lavaplayer-lib) | 1.2.3 | started and ticked | main | nothing |
| [pv-addon-priority](https://modrinth.com/mod/pv-addon-priority) | 1.1.0 | started and ticked | main | nothing |
| [pv-addon-sculk](https://modrinth.com/mod/pv-addon-sculk) | 1.1.0 | started and ticked | main | nothing |
| [pv-addon-soundphysics](https://modrinth.com/mod/pv-addon-soundphysics) | 1.1.2 | started and ticked | main | nothing |
| [pv-addon-spectator](https://modrinth.com/mod/pv-addon-spectator) | 1.1.0 | started and ticked | main | nothing |
| [pv-addon-voice-messages](https://modrinth.com/mod/pv-addon-voice-messages) | 1.0.0-beta.4 | started and ticked | main | 3 |
| [pv-addon-whisper](https://modrinth.com/mod/pv-addon-whisper) | 1.1.0 | started and ticked | main | nothing |
| [Qommons](https://modrinth.com/mod/qommons) | 1.0.1 | started and ticked | client | 20 |
| [Quick Coordinates Copy: Enhanced](https://modrinth.com/mod/quickcoordinatescopy-enhanced) | 1.5.3 | started and ticked | client | 3 |
| [quick resource pack](https://modrinth.com/mod/quick-resource-pack) | 1.2.0-mc26.3 | started and ticked | client | 5 |
| [QuickDistance](https://modrinth.com/mod/quickdistance) | 26.3-1.0.0 | started and ticked | client, main | 3 |
| [QuickRebind](https://modrinth.com/mod/quickrebind) | 1.1.0+26.3 | started and ticked | client | 32 |
| [QuitKeybind](https://modrinth.com/mod/quitkeybind) | 1.0.0+26.3 | started and ticked | client | 2 |
| [Random Language](https://modrinth.com/mod/random-language) | 1.5.1 | started and ticked | client | 6 |
| [RationalCraft](https://modrinth.com/mod/rationalcraft) | 26.3-fabric-1.0.1 | started and ticked | client, main | nothing |
| [Ravents](https://modrinth.com/mod/ravents) | 4.0 | started and ticked | client, main | 86 |
| [Repairables Anvil](https://modrinth.com/mod/repairables-anvil) | 26.3-0.1 | started and ticked | main | 4 |
| [Roleplay Utils](https://modrinth.com/mod/roleplay-utils) | 1.0.5 | started and ticked | client, main | 11 |
| [Roughly Enough Professions (REP)](https://modrinth.com/mod/roughly-enough-professions-rep) | 10.0.0 | started and ticked | client | nothing |
| [RPG Coins](https://modrinth.com/mod/rpgcoins) | 3.0.1 | started and ticked | main | nothing |
| [Ruined Lighthouse](https://modrinth.com/mod/ruined-lighthouse) | 1.0.0 | started and ticked | main | nothing |
| [Saro's Easy Fly](https://modrinth.com/mod/saros-easy-fly) | 1.0.2 | started and ticked | main | 5 |
| [Saro's Easy Lang Switcher](https://modrinth.com/mod/saros-easy-lang-switcher) | 1.0.1 | started and ticked | client | 13 |
| [Saro's Easy Time](https://modrinth.com/mod/saros-easy-time) | 1.0.2 | started and ticked | main | 9 |
| [Saro´s Book Renamer](https://modrinth.com/mod/saros-book-renamer) | 1.0.2 | started and ticked | client | 4 |
| [Sawmill House](https://modrinth.com/mod/sawmill-house) | 1.0.0 | started and ticked | main | nothing |
| [SeamlessLib](https://modrinth.com/mod/seamless-api) | 2.0.2+mc26.3-fabric | started and ticked | main | nothing |
| [SettingsManagerLib](https://modrinth.com/mod/settingsmanagerlib) | 1.1.3+26.3 | started and ticked | client, main | 1 |
| [SH: Buried Halls / Underground Dungeon Structures](https://modrinth.com/mod/sh-buried-halls-underground-dungeon-structures) | 1.6.1 | started and ticked | main | nothing |
| [Shader Patcher](https://modrinth.com/mod/shader-patcher) | 1.2 | started and ticked | client | 3 |
| [Shrug Mod](https://modrinth.com/mod/shrug-mod) | 1.0.12 | started and ticked | client | 1 |
| [Simple AutoFish](https://modrinth.com/mod/simpleautofish) | 1.1.0+mc26.3 | started and ticked | client | 7 |
| [Simple AutoSprint](https://modrinth.com/mod/simple-autosprint) | 2.0.0 | started and ticked | client | 3 |
| [Simple Block Randomizer](https://modrinth.com/mod/simple-block-randomizer) | 2.0.0 | started and ticked | client | 11 |
| [Simple Modpack Update Checker](https://modrinth.com/mod/smuc) | 2.2.1 | started and ticked | client | 4 |
| [Simple Playtime Command](https://modrinth.com/mod/simple-playtime-command) | 1.1.0+mc26.3 | started and ticked | main | 5 |
| [Simple SetHome Commands](https://modrinth.com/mod/simplesethome) | 1.1.0+mc26.3 | started and ticked | main | 3 |
| [SimpleRepair](https://modrinth.com/mod/simplerepair) | 1.2.0 | started and ticked | client | 1 |
| [Simplify Tool Config Lib](https://modrinth.com/mod/simplify-tools-configuration-library) | 1.1.0 | started and ticked | main | nothing |
| [Skeleton Ghost Ship](https://modrinth.com/mod/skeleton-ghost-ship) | 1.0.0 | started and ticked | main | nothing |
| [SleepTime](https://modrinth.com/mod/sleeptime) | 1.0.9 | started and ticked | client | 1 |
| [Small horse stable](https://modrinth.com/mod/small-horse-stable) | 1.0.0 | started and ticked | main | nothing |
| [Smart Refill](https://modrinth.com/mod/smart-refill) | 1.0.2 | started and ticked | client | 3 |
| [Smooth Join](https://modrinth.com/mod/smooth-join) | 1.3.2 | started and ticked | client | nothing |
| [Snap Perspective](https://modrinth.com/mod/snap-perspective) | 1.0.2 | started and ticked | client | 2 |
| [SnapYaw](https://modrinth.com/mod/snapyaw) | 0.1.2 | started and ticked | client | 3 |
| [Snowy Tents](https://modrinth.com/mod/snowy-tents) | 1.0.0 | started and ticked | main | nothing |
| [Spawn Animations](https://modrinth.com/mod/spawn-animations) | 1.11.6+mod | started and ticked | main | 9 |
| [Speedometer](https://modrinth.com/mod/speedometermod) | 1.1.0 | drew on the HUD | client | 1 |
| [SprintByDefault](https://modrinth.com/mod/sprintbydefault) | 26.1-26.3 | started and ticked | client | 1 |
| [SprintIndicator](https://modrinth.com/mod/sprintindicator) | mc26.3 | started and ticked | client, main | nothing |
| [Standby](https://modrinth.com/mod/standby) | 1.0.1 | started and ticked | client, main | 10 |
| [Star Calculator](https://modrinth.com/mod/star-calculator) | 1.0.5 | started and ticked | client, main | 2 |
| [Statistics Updater](https://modrinth.com/mod/statistics-updater) | 0.2.0-26.3 | started and ticked | main | 18 |
| [Stew Detective](https://modrinth.com/mod/stew-detective) | 1.2.0 | started and ticked | client | 6 |
| [The Advanced Sword Bow Swapper](https://modrinth.com/mod/the-advanced-sword-bow-swapper) | 1.0.1-26.3 | started and ticked | client, main | 6 |
| [Thunder Detector](https://modrinth.com/mod/thunder-detector) | 1.0.1 | started and ticked | client | nothing |
| [TickTally](https://modrinth.com/mod/ticktally) | 1.6.0 | started and ticked | client | 5 |
| [TMYK](https://modrinth.com/mod/tmyk) | 1.1.0 | started and ticked | client, main | 12 |
| [Toggle Resource Pack](https://modrinth.com/mod/toggle-resource-pack) | v1.2.0 | started and ticked | client | 17 |
| [Toggle Subtitles](https://modrinth.com/mod/toggle-subtitles) | 1.5+26.3-fabric | started and ticked | client | 1 |
| [Torchkey](https://modrinth.com/mod/torchkey) | 1.0.10+26.2 | started and ticked | client | 5 |
| [Totem Assist](https://modrinth.com/mod/totem-assist) | 1.0.1 | started and ticked | client | nothing |
| [TT-StreamChat](https://modrinth.com/mod/tiktok-streamchat) | 0.3.0 | started and ticked | client, main | 1 |
| [TurboTech's Metal Pipes](https://modrinth.com/mod/turbotechs-metal-pipes) | v1.0+mc26.3 | started and ticked | main | 1 |
| [Uncraftable No More](https://modrinth.com/mod/uncraftable-no-more) | v1.0.7 | started and ticked | main | nothing |
| [Uncrafting MC](https://modrinth.com/mod/uncrafting-mc) | 2.0.0 | started and ticked | main | nothing |
| [Underwater Village](https://modrinth.com/mod/underwater-village) | 1.0.0 | started and ticked | main | nothing |
| [UniLink](https://modrinth.com/mod/unilink) | 1.1.1-fabric+26.1 | started and ticked | client | nothing |
| [Unipose](https://modrinth.com/mod/unipose) | 1.0.1 | started and ticked | main | 20 |
| [Unstable SMP Death Sounds - Optimized](https://modrinth.com/mod/unstable-smp-death-sounds-optimized) | 1.0.0-26.3 | started and ticked | client | 5 |
| [Update Advancement Quests](https://modrinth.com/mod/update-advancement-quests) | 2.0.0-26.3 | started and ticked | main | nothing |
| [UPDATEd Timber (Server Side)](https://modrinth.com/mod/timber-by-mycf-server-side) | 2.1.0+26.3 | started and ticked | main | 3 |
| [Usage Ticker Reloaded](https://modrinth.com/mod/usage-ticker-reloaded) | 1.2.1 | started and ticked | client | 4 |
| [Useless Upgrade Template](https://modrinth.com/mod/uselessupgradetemplate) | 1.0.3r-farbic | started and ticked | main | nothing |
| [VeinMiner Enchantment](https://modrinth.com/mod/veinminer-enchantment) | 2.13.0 | started and ticked | main | nothing |
| [Villager Fletcher House](https://modrinth.com/mod/villager-fletcher-house) | 1.0.0 | started and ticked | main | nothing |
| [Villager Paintings](https://modrinth.com/mod/villager-paintings) | 1.0.0 | started and ticked | main | nothing |
| [Villager Saloon](https://modrinth.com/mod/villager-saloon) | 1.0.0 | started and ticked | main | nothing |
| [Villager Trade Reroll](https://modrinth.com/mod/easylibrariantrade) | 1.0.6 | started and ticked | main | 85 |
| [Villager Warship](https://modrinth.com/mod/villager-warship) | 1.0.0 | started and ticked | main | nothing |
| [VillagerExplorer](https://modrinth.com/mod/villagerexplorer) | 26.3-1.1.0-fabric | started and ticked | client | 71 |
| [Visual Data Size](https://modrinth.com/mod/visual-data-size) | 1.3.1 | started and ticked | client | 10 |
| [Voice chat visualizer](https://modrinth.com/mod/voice-chat-visualizer) | 1.0.0+26.3 | started and ticked | client | 20 |
| [Warden Nether Portal](https://modrinth.com/mod/warden-nether-portal) | 1.0.0 | started and ticked | main | nothing |
| [WindowFix](https://modrinth.com/mod/window-fix) | 1.0.1 | started and ticked | client | 12 |
| [Windows Priority Boost](https://modrinth.com/mod/windows-priority-boost) | 1.0.0 | started and ticked | client | nothing |
| [WITS (What Is This Structure?)](https://modrinth.com/mod/wits) | 1.4.3+26.3-fabric | started and ticked | main | 18 |
| [Wonders](https://modrinth.com/mod/seven-wonders) | 1.5.4 | started and ticked | main | 3 |
| [WorkPenalty](https://modrinth.com/mod/workpenalty) | 1.0.0+26.3 | started and ticked | client | 1 |
| [WunderLib](https://modrinth.com/mod/wunderlib) | 26.301.4 | started and ticked | client | 23 |
| [WunderLib: New Dawn](https://modrinth.com/mod/wunderlib-neoforge) | 26.3.1-fabric | started and ticked | client | 13 |
| [ZambieLib](https://modrinth.com/mod/zambielib) | 1.2.0+26.3-fabric | started and ticked | main | nothing |
| [Zero Lib](https://modrinth.com/mod/zero-lib) | 26.3-1.0.6-fabric | started and ticked | main | nothing |

## Should work: data only

Mods without code whose structures (pieces, pools, sets of vanilla blocks) Kiln hands to Horizon's world generator, as it does Illager Warship's (README). Not run and not tried one by one; a piece with a block of the mod's own isn't generated.

| Mod | Version |
|---|---|
| [Abu Simbel - Ruins Structure](https://modrinth.com/mod/abusimbel) | 1.0.0+mod |
| [Aether Ruins](https://modrinth.com/mod/aether-ruins) | 1.0+mod |
| [Amethyst Golem](https://modrinth.com/mod/amethyst-golem) | 2.3 |
| [Amethyst Nether](https://modrinth.com/mod/amethyst-nether) | v4.4+mod |
| [Ancient Cities](https://modrinth.com/mod/ancient-cities) | 1.6 |
| [Ancient Jungle Ring - Ruins Structure](https://modrinth.com/mod/ancient-jungle-ring) | 1.0.0+mod |
| [Animator1110's Adventure](https://modrinth.com/mod/animator1110s-adventure-datapack) | 2.3+mod |
| [Ardwick](https://modrinth.com/mod/ardwick) | 1.4+mod |
| [Astral Travel](https://modrinth.com/mod/astral-travel) | 1.1.4+mod |
| [ATi Structures](https://modrinth.com/mod/ati-structures-fabricforge) | 1.4.7 |
| [ATi Structures - Vanilla Edition](https://modrinth.com/mod/ati-structures-vanilla-edition) | 1.4.7 |
| [Avarii Airship](https://modrinth.com/mod/avarii-airship) | 1.0+mod |
| [Avarii Balloon](https://modrinth.com/mod/avarii-balloon) | 1.0+mod |
| [Aztec Temple & Ruins - Ruins Structure](https://modrinth.com/mod/aztec-ruins) | 1.0.0+mod |
| [Backrooms](https://modrinth.com/mod/ly-backrooms) | 1.1.1+mod |
| [Better portals](https://modrinth.com/mod/enhanced-nether-portals) | 1.0.2m |
| [Bingo UHC](https://modrinth.com/mod/bingo-uhc) | 26.10.0+mod |
| [Biome Strongholds](https://modrinth.com/mod/biome-strongholds) | 1.5+mod |
| [Bounce: Sulfur Cube Minigame](https://modrinth.com/mod/bounce) | 1.0+26.3+mod |
| [Bramble Henge](https://modrinth.com/mod/bramble-henge) | 2.0.3+mod |
| [Bramble Mob](https://modrinth.com/mod/bramble) | 1.2.2+mod |
| [Breeze Island](https://modrinth.com/mod/breeze-island) | v1.0+mod |
| [ButterBee - Mob Variants](https://modrinth.com/mod/butterbee) | 1.1.0-mod-26.3 |
| [Canyons_and_Cliffs_and_Biomes](https://modrinth.com/mod/canyons-and-cliffs-and-biomes) | 1.5.0.14950.0 |
| [Cascades](https://modrinth.com/mod/hybrid-beta) | 1.0.7+mod |
| [Chamber Refurbished](https://modrinth.com/mod/chamber-refurbished) | 1.3+mod |
| [Cherry Villages! (Mot's Structures)](https://modrinth.com/mod/mots-structures) | 1.9+mod |
| [Clear End City](https://modrinth.com/mod/clear-end-city) | 6 |
| [Cliff under a Branch (CliffBranch)](https://modrinth.com/mod/cliffbranch) | 4.2+mod |
| [Crazy Chambers](https://modrinth.com/mod/crazy-chambers) | 1.3.0 |
| [Crop & Kettle](https://modrinth.com/mod/crop-and-kettle) | v1.3.17+mod |
| [Deadly Deadly Dungeon](https://modrinth.com/mod/deadly-deadly-dungeon) | 1.0.6+mod |
| [DeCubed Dungeons (Formerly Dungeons+)](https://modrinth.com/mod/decubed-dungeons) | 1.13.1+mod |
| [Delfos - Ruins Structure](https://modrinth.com/mod/delfos) | 1.0.0+mod |
| [Discoveria - More Structures](https://modrinth.com/mod/discoveria) | 26.3m |
| [Door Locker](https://modrinth.com/mod/door-locker) | 1.7.1 |
| [Dragon Nest](https://modrinth.com/mod/dragon-nest) | 1.0+mod |
| [Dragonkind Evolved / End Battle Reborn](https://modrinth.com/mod/dragonkind-evolved) | 1.15+mod |
| [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) | 6.1.0+mod |
| [Dungeons and Taverns Ancient City Overhaul](https://modrinth.com/mod/dungeons-and-taverns-ancient-city-overhaul) | 4+mod |
| [Dungeons and Taverns Desert Temple Overhaul](https://modrinth.com/mod/dungeons-and-taverns-desert-temple-overhaul) | 2.2+mod |
| [Dungeons and Taverns Jungle Temple Overhaul](https://modrinth.com/mod/dungeons-and-taverns-jungle-temple-overhaul) | 2.2+mod |
| [Dungeons and Taverns Mineshaft Overhaul](https://modrinth.com/mod/dungeons-and-taverns-mineshaft-overhaul) | 2.0.1+mod |
| [Dungeons and Taverns Nether Fortress Overhaul](https://modrinth.com/mod/dungeons-and-taverns-nether-fortress-overhaul) | 3.2.1+mod |
| [Dungeons and Taverns Ocean Monument Overhaul](https://modrinth.com/mod/dungeons-and-taverns-ocean-monument-overhaul) | 2.3+mod |
| [Dungeons and Taverns Pillager Outpost Overhaul](https://modrinth.com/mod/dungeons-and-taverns-pillager-outpost-overhaul) | 3.4.2+mod |
| [Dungeons and Taverns Stronghold Overhaul](https://modrinth.com/mod/dungeons-and-taverns-stronghold-overhaul) | 3.0.1+mod |
| [Dungeons and Taverns Swamp Hut Overhaul](https://modrinth.com/mod/dungeons-and-taverns-swamp-hut-overhaul) | 3+mod |
| [Dungeons and Taverns Woodland Mansion Overhaul](https://modrinth.com/mod/dungeons-and-taverns-woodland-mansion-overhaul) | 2.2.1+mod |
| [Echoo's Structures](https://modrinth.com/mod/echoos-structures) | 26.3-1.3 |
| [Enchanted Elytra](https://modrinth.com/mod/enchanted-elytra) | 26.3.0+mod |
| [Enchantments Encore](https://modrinth.com/mod/enchantments-encore) | 6.1-mod |
| [Enderite Overhaul +](https://modrinth.com/mod/enderite-overhaul) | 49 |
| [Epic Structures: Dungeons](https://modrinth.com/mod/epic-structures-dungeons) | 1.2.6 |
| [Epic Structures: Igloo](https://modrinth.com/mod/epic-structures-igloo) | 1.0.5+mod |
| [Epic Structures: Jungle Temples](https://modrinth.com/mod/epic-structures-jungle-temples) | 1.0.3 |
| [Epic Structures: Villages](https://modrinth.com/mod/epic-structures-villages) | 2.0.0+mod |
| [Epic Structures: Villages Standalone Edition](https://modrinth.com/mod/epic-structures-villages-standalone-edition) | 1.0.0+mod |
| [Epic Structures: Witch Huts](https://modrinth.com/mod/epic-structures-witch-huts) | 1.3.2 |
| [Evil Stone House of Death](https://modrinth.com/mod/evil-stone-house-of-death) | v1.0+mod |
| [Fabled Roots](https://modrinth.com/mod/fabled-roots) | 3.6-mod |
| [Fishing Expansion](https://modrinth.com/mod/ly-fishing-expansion) | 1.3.2+mod |
| [Flame Columns](https://modrinth.com/mod/flame-columns) | 1.0 |
| [Forest Villages](https://modrinth.com/mod/forest-villages) | 1.0+mod |
| [Fortress of War / Nether Fortress Update](https://modrinth.com/mod/fortress-of-war) | 1.3+mod |
| [Frozen Nether Roof](https://modrinth.com/mod/frozen-nether-roof) | 1.1.1+mod |
| [Gigantic Squid](https://modrinth.com/mod/gigantic-squid) | 1.2+mod |
| [Grand Capitals](https://modrinth.com/mod/grand-capitals) | 1.4.0 |
| [Grim kingdoms: Lost structures & ruins](https://modrinth.com/mod/grim-kingdoms-lost-structures-ruins) | 2.0.3+mod |
| [Grim kingdoms: Lost structures & ruins Vanilla Edition](https://modrinth.com/mod/grim-kingdoms-lost-structures-ruins-vanilla-edition) | 2.0.0+mod |
| [Guns, Gadgetery & Mission Editor](https://modrinth.com/mod/guns-gadgetery-mission-editor) | 1.7.3+mod |
| [Hopo Better Mineshaft](https://modrinth.com/mod/hopo-better-mineshaft) | 1.3.8 |
| [Hopo Better Ruined Portals](https://modrinth.com/mod/hopo-better-ruined-portals) | 1.5.2 |
| [Hopo Better Underwater Ruins](https://modrinth.com/mod/hopo-better-underwater-ruins) | 1.2.9 |
| [Ice Castle](https://modrinth.com/mod/ice-castle) | 1.0+mod |
| [Improved Pillager Outpost](https://modrinth.com/mod/improved-pillager-outpost) | 7 |
| [Improved Potions](https://modrinth.com/mod/improved-potions) | 2.2+mod |
| [Improved/More Biomes](https://modrinth.com/mod/improved-more-biomes) | 3.7.3+mod |
| [Incendium Legacy](https://modrinth.com/mod/incendium) | 5.5.4+26.3 |
| [Japanese Castle - Structure](https://modrinth.com/mod/japanese-castle) | 1.0.0+mod |
| [Japanese Temple - Structure](https://modrinth.com/mod/japanese-temple) | 1.0.0+mod |
| [Journey to the North-East](https://modrinth.com/mod/journey-to-the-north-east) | 3.6+mod |
| [Judaism Datapack](https://modrinth.com/mod/judaismdatapack) | 0.7+mod |
| [Katters Structures](https://modrinth.com/mod/katters-structures) | 2.9-mod |
| [Katters Structures - Ambient](https://modrinth.com/mod/katters-structures-only-ambient) | 2.9-mod |
| [Katters Structures - Deep Blue](https://modrinth.com/mod/katters-structures-deep-blue) | 2.9-mod |
| [Katters Structures - Dungeon](https://modrinth.com/mod/katters-structures-only-dungeon) | 2.9-mod |
| [Katters Structures - Village](https://modrinth.com/mod/katters-structures-only-village) | 2.9-mod |
| [KawaMood's Waystones in DNT Shrines](https://modrinth.com/mod/waystones-in-dnt-shrines) | 2.0.1+mod |
| [KawaMood's Waystones in Modded Villages](https://modrinth.com/mod/kawamoods-waystones-in-modded-villages) | 1.0+mod |
| [KawaMood's Waystones in Villages](https://modrinth.com/mod/waystones-in-villages) | 3.0.1+mod |
| [Land Ahoy!](https://modrinth.com/mod/landahoy) | 1.4.1+mod |
| [Lone farmer house](https://modrinth.com/mod/lone-farmer-house) | v1.0+mod |
| [Lost Sky Islands](https://modrinth.com/mod/lost-sky-islands) | 1.0.3 |
| [Lucky Block Reborn](https://modrinth.com/mod/lbr) | M-1.1.2 |
| [Lucky Blocks World Add-on](https://modrinth.com/mod/lucky-blocks-world) | v1.0.1+mod |
| [Lunar Phase / Custom Overworld for the Lunar Phase SMP](https://modrinth.com/mod/lunar-phase) | 1.1+mod |
| [Lush Cave Bramble](https://modrinth.com/mod/lush-cave-bramble) | 1.4.0+mod |
| [Lush Cave Ruins](https://modrinth.com/mod/lush-cave-ruins) | 2.3.1+mod |
| [Magic Items](https://modrinth.com/mod/magic-items) | 2.4-26.3+mod |
| [Mangrove Manor](https://modrinth.com/mod/mangrove-manor) | 1.3.1+mod |
| [Matcha Flavoured](https://modrinth.com/mod/matcha-flavoured) | 1.12.9+mod |
| [Maze Dungeon](https://modrinth.com/mod/maze-dungeon) | 1.0+mod |
| [McDonalds - Structure](https://modrinth.com/mod/mcdonalds) | 1.0.0+mod |
| [michael9r9r's RPG Loot (modern port)](https://modrinth.com/mod/michael9r9rs-rpg-loot-modern-port) | 26.3+mod |
| [Mine Treasure](https://modrinth.com/mod/mine-treasure) | 1.6.5+mod |
| [Minimal Structures (Mingen)](https://modrinth.com/mod/mingen) | v7.3 |
| [Moai Statues](https://modrinth.com/mod/moai-statues) | 1.0.0+mod |
| [More Enchants](https://modrinth.com/mod/moreenchantments) | 1.6+mod |
| [Moss Ruins](https://modrinth.com/mod/moss-ruins) | 1.3+mod |
| [Mycenae - Ruins Structure](https://modrinth.com/mod/mycenae) | 1.0.0+mod |
| [Necropolis - Ancient City Overhaul](https://modrinth.com/mod/necropolis-ancient-city-overhaul) | 2.0.4+mod |
| [Need More Structures](https://modrinth.com/mod/need-more-structures) | 6.4+mod |
| [Newgrange - Ruins Structure](https://modrinth.com/mod/newgrange) | 1.0.0+mod |
| [Nice Mob Variants](https://modrinth.com/mod/nice-mob-variants) | 2.7-mod |
| [Nice Things](https://modrinth.com/mod/nice-things-eden) | 3.1-mod |
| [Nimari Garden](https://modrinth.com/mod/nimari-garden) | 1.0+mod |
| [Nimari Magic Tower](https://modrinth.com/mod/nimari-magic-tower) | 1.0+mod |
| [Nullscape](https://modrinth.com/mod/nullscape) | 2.0.1+26.3 |
| [Ominous Mansions / Woodland Mansion Update](https://modrinth.com/mod/ominous-mansions) | 1.7+mod |
| [Panda Temple](https://modrinth.com/mod/panda-temple) | V1-1.21++mod |
| [Petra - Ruins Structure](https://modrinth.com/mod/petra) | 1.0.0+mod |
| [Pillager Medieval Mansion Dungeon](https://modrinth.com/mod/pillager-medieval-mansion-dungeon) | 1.0.0+mod |
| [Plate Havoc](https://modrinth.com/mod/plate-havoc) | 8+1J |
| [Prisma Craft](https://modrinth.com/mod/prisma-craft) | 2.2+mod |
| [Pyramid](https://modrinth.com/mod/pyramid) | 1.0+mod |
| [qrafty's Dappled Villages](https://modrinth.com/mod/qraftys-dappled-villages) | 1.0+mod |
| [qrafty's End Villages](https://modrinth.com/mod/qraftys-end-villages) | 5.0-26.3+mod |
| [qrafty's Jungle Villages](https://modrinth.com/mod/qraftys-jungle-villages) | 6.0+mod |
| [qrafty's Mountain Villages](https://modrinth.com/mod/qraftys-mountain-villages) | 5.0+mod |
| [qraftyfied: STRUCTURES](https://modrinth.com/mod/qraftyfied) | 13.0.0-26.3+mod |
| [Re vault](https://modrinth.com/mod/revault) | 1.3.3+mod |
| [Recondite](https://modrinth.com/mod/recondite) | 0.2.1 |
| [Ruins and Towers](https://modrinth.com/mod/ruins-and-towers) | 0.3.3+mod |
| [Sculk Ruins](https://modrinth.com/mod/sculk-ruins) | 1.0+mod |
| [Ship With Robber](https://modrinth.com/mod/ship-with-robber) | 1.0 |
| [Simple ant](https://modrinth.com/mod/simple-ant) | r1.0_mc26.2+mod |
| [Simple Apple Trees](https://modrinth.com/mod/simple-apple-trees) | 2.0.1+mod |
| [Simple Structures: Caves](https://modrinth.com/mod/simple-structures-caves) | 1.3 |
| [Simple Structures: Desert](https://modrinth.com/mod/simple-structures-desert) | 1.3.1 |
| [Simple Structures: Mushroom Fields](https://modrinth.com/mod/simplestructure-mushroom-fields) | 1.1.1 |
| [Simple Structures: Nostalgia](https://modrinth.com/mod/simple-structure-nostalgia) | 1.2.1 |
| [Simple Structures: Pale Garden](https://modrinth.com/mod/simple-structure-pale-garden) | 1.1.1 |
| [Simply OneBlock (Wilderness Bound Included)](https://modrinth.com/mod/simply-oneblock) | 1.4.0 |
| [Sincerelyuq's Overhaul](https://modrinth.com/mod/sincerelyuq-dungeons) | TEST |
| [Sky Temple](https://modrinth.com/mod/sky-temple) | 1.0+mod |
| [Skyblock Infinite](https://modrinth.com/mod/skyblock-infinite) | 1.1.18+mod |
| [Small Home](https://modrinth.com/mod/small-home) | 1.0 |
| [Small Structures](https://modrinth.com/mod/small-structures) | 0.6+1.21.9-26.3 |
| [Spelunkers Dream](https://modrinth.com/mod/spelunkers-dream) | 1.0.4+mod |
| [Stellarity](https://modrinth.com/mod/stellarity) | 6.0.1 |
| [Stone Rooms](https://modrinth.com/mod/stone-rooms) | 1.3 |
| [Stonehenge - Ruins Structure](https://modrinth.com/mod/stonehenge) | 1.0.0+mod |
| [Strongholds](https://modrinth.com/mod/strongholds) | 1.4 |
| [Structory](https://modrinth.com/mod/structory) | 1.3.18+26.3 |
| [Structory: Towers](https://modrinth.com/mod/structory-towers) | 1.0.19+26.3 |
| [Structures](https://modrinth.com/mod/structures-mod) | 4.5 |
| [Structures+](https://modrinth.com/mod/structures-pluss) | 3.1 |
| [Sulfur Cave Labyrinth](https://modrinth.com/mod/sulfur-cave-labyrinth) | 1.1.1+mod |
| [Survivor's Elegy](https://modrinth.com/mod/survivors-elegy) | 1.1.3+mod |
| [Swamp Spider's House](https://modrinth.com/mod/swamp-spiders-house) | 1.1+mod |
| [The Backrooms](https://modrinth.com/mod/aria-backrooms) | 1.1.0+mod |
| [The Dreaming Realm Dimension](https://modrinth.com/mod/the-dreaming-realm) | 1.4.1+mod |
| [The Frostpale / Frozen World](https://modrinth.com/mod/the-frostpale) | 1.4+mod |
| [The Ice Warrior](https://modrinth.com/mod/the-ice-warrior) | r3.1-mc26.3+mod |
| [The Lost Woods](https://modrinth.com/mod/the-lost-woods) | 2.0.2+mod |
| [The Nether Reactor](https://modrinth.com/mod/nether-reactor) | 1.3.1+mod |
| [The Ruined Dimension](https://modrinth.com/mod/the-ruined-dimension) | 1+mod |
| [The T-Birds Structure Collection](https://modrinth.com/mod/tbirds-structures) | 1.2+mod |
| [The Wastes / Custom Overworld from the Wastes SMP](https://modrinth.com/mod/the-wastes) | 1.3+mod |
| [ThirtyFangs's Kingdom](https://modrinth.com/mod/thirtyfangss-kingdom) | 1.1.1+mod |
| [Thun's Structures](https://modrinth.com/mod/thuns-structures) | 1.3.4 |
| [Tidal Towns](https://modrinth.com/mod/tidal-towns) | 2.0+mod |
| [Tower Game](https://modrinth.com/mod/tower-game) | 2.1-en+mod |
| [Tower Of Gods](https://modrinth.com/mod/tower-of-gods) | 2.0+mod |
| [Towns and Towers](https://modrinth.com/mod/towns-and-towers) | 1.13.13 |
| [Trek](https://modrinth.com/mod/trek) | B0.6.2.1+mod |
| [Triple Life](https://modrinth.com/mod/triple-life-combat-minigame) | 1.6.1+mod |
| [Ultimate Warden](https://modrinth.com/mod/ultimate-warden) | 0.3.pre-b1-mod |
| [Unstable Farlands(OuterLands)](https://modrinth.com/mod/unstable-farlandsouterlands) | 1+mod |
| [Unstable Underworld](https://modrinth.com/mod/unstableunderworld) | 1+mod |
| [Updated Nether Fortress](https://modrinth.com/mod/updated-nether-fortress) | 1.7.1+mod |
| [Useful Structures](https://modrinth.com/mod/useful-structures) | v3.1+mod |
| [Vanilla Structure Update](https://modrinth.com/mod/vanilla-structure-update) | V2.12+mod |
| [Vanilla++](https://modrinth.com/mod/vanilla-plus-plus-data-pack) | 26.0.mod |
| [Varied Temples](https://modrinth.com/mod/varied-temples) | v3.0+mod |
| [Veylith Laboratory](https://modrinth.com/mod/veylith-laboratory) | 1.0+mod |
| [Villages Revamped](https://modrinth.com/mod/villages-revamped) | V2.11+mod |
| [Walking Spawns Structures](https://modrinth.com/mod/walking-spawns-structures) | 1.1.0+mod |
| [Walled City Structure](https://modrinth.com/mod/walled-city-structure) | 1.0.3+mod |
| [Wild Nature](https://modrinth.com/mod/wild-nature-thirtyfangs) | alpha-1.3+mod |
| [Woodland Mansions](https://modrinth.com/mod/woodland-mansions) | 1.5 |
| [Yiga Hideout](https://modrinth.com/mod/yiga-hideout) | 1.0+mod |
| [Zaffre's Mineshafts](https://modrinth.com/mod/zaffres-mineshafts) | 1.1+mod |

Not listed: 10 more mods in which the scan finds nothing Kiln lacks but which the stand didn't run (server-side mods, mods without an entry point it runs); the catalogue shows them.
