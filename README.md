<p align="center">
    <img alt="Public Teleport Logo" src=".github/banner-700.png">
</p>

An easy-to-use Fabric Teleport and Portal Mod!

- Homes, Warps, Portals, Back, Spawn and TPA functionality
- Checks if teleport destinations are safe to use to prevent suffocation
- Provides physical Portal areas that when walked into can teleport the player to a position in any dimension or to another Minecraft server altogether
- Usable in multiplayer (Server-side) or singleplayer (Client-side)
- Server-side functionality does NOT require client-side installation, but see section "Languages" below
- Minimal configuration with predefined values

<p align="center">
    <img alt="Public Teleport Logo" src=".github/bus-500.png"> *
</p>

## Commands

| Command                                         | Only OP | Description                                                                                              |
| ----------------------------------------------- | :-----: | -------------------------------------------------------------------------------------------------------- |
| `/helpteleport`                                 |         | Shows information about the commands the player can use                                                  |
| **Spawn:**                                      |         |                                                                                                          |
| `/setspawn`                                     |    ✓    | Sets the Spawn point at the players current position/n(Unrelated to the Minecraft world spawn)           |
| `/spawn`                                        |         | Teleports to Spawn (Needs to be set via the `/setspawn` command)                                         |
| **Warps:**                                      |         |                                                                                                          |
| `/setwarp <name>`                               |    ✓    | Sets a Warp position at the players current position                                                     |
| `/delwarp <name>`                               |    ✓    | Deletes a Warp                                                                                           |
| `/warp <name>`                                  |         | Teleports to a Warp                                                                                      |
| `/warps`                                        |         | Lists all Warps                                                                                          |
| **Homes:**                                      |         |                                                                                                          |
| `/sethome [<name>]`                             |         | Sets a Home position at the players current position (Default: `home`)                                   |
| `/delhome <name>`                               |         | Deletes a Home                                                                                           |
| `/home [<name>]`                                |         | Teleports to a Home (Default: `home`)                                                                    |
| `/homes`                                        |         | Lists all your homes                                                                                     |
| **Back:**                                       |         |                                                                                                          |
| `/back`                                         |         | Teleports to the last location before using a Home, Warp, or dying                                       |
| **Portals:**                                    |         |                                                                                                          |
| `/setportal <name> from`                        |  ✓ / ✗  | Sets the "from" position from the full block that is currently looked at                                 |
| `/setportal <name> to`                          |  ✓ / ✗  | Sets the "to" position from the full block that is currently looked at                                   |
| `/setportal <name> target`                      |  ✓ / ✗  | Sets the "target" position at the players current position                                               |
| `/setportal <name> target "<domain/ip>:<port>"` |  ✓ / ✗  | Sets the "target" to another Minecraft servers URL/n(Only OP can use this command for security reasons!) |
| `/delportal <name>`                             |  ✓ / ✗  | Deletes a Portal                                                                                         |
| `/portals`                                      |  ✓ / ✗  | Lists all Portals                                                                                        |
| **TPA:**                                        |         |                                                                                                          |
| `/tpa <player>`                                 |         | Requests teleportation to `<player>`                                                                     |
| `/tpahere <player>`                             |         | Requests `<player>` to teleport to you                                                                   |
| `/tpahereall`                                   |    ✓    | Requests ALL players to teleport to you                                                                  |
| `/tpcancel`                                     |         | Cancels your teleportation request                                                                       |
| `/tpaccept [<player>]`                          |         | Accepts request from `<player>` (Default: Most recent)                                                   |
| `/tpdeny [<player>]`                            |         | Denies request from `<player>` (Default: Most recent)                                                    |

Please note:

- TPA functionality is disabled in singleplayer
- `/setspawn`, `/setwarp` and `/sethome` use your current location rounded to a block position and your current viewing angle to create the teleport point
- The `/setportal` command has three parts (`from`, `to`, `target`), all three parts need to be set with the same name for the Portal to be saved, started Portals that are not saved yet are lost on server restart.
- `/tpaccept` searches for a spawnable block around the target position to teleport the player to

## Configuration

On first run a config file is created (`config/public-teleport/config.json`) with these defaults:

| Field                  | Default | Description                                                                                     |
| ---------------------- | ------- | ----------------------------------------------------------------------------------------------- |
| `defaultLanguage`      | `en_us` | The default language used, see section "Languages" below.                                       |
| `enableSpawn`          | true    | When true the `/setspawn, /spawn` commands are enabled                                          |
| `enableWarps`          | true    | When true the `/setwarp, /delwarp, /warp, /warps` commands are enabled                          |
| `enableHomes`          | true    | When true the `/sethome, /delhome, /home, /homes` commands are enabled                          |
| `enableBack`           | true    | When true the `/back` command is enabled                                                        |
| `enablePortals`        | true    | When true the `/setportal, /delportal, /portals` commands are enabled and the Portals are open  |
| `enableTpa`            | true    | When true the `/tpa, /tpahere, /tpahereall, /tpcancel, /tpaccept, /tpdeny` commands are enabled |
| `maxHomes`             | 10      | The maximum amount of homes a player can have (Set to `0` to disable limit)                     |
| `requestTimeout`       | 60      | How long a teleport request is active before it is removed in seconds                           |
| `portalCommandsOnlyOp` | true    | When true only OP can use the Portal commands (Portals themselfs are always usable by anyone)   |

To change these settings edit the config file and restart your server/client.

## Portals

Portals are physical areas a user can walk into to be teleported to the associated target position or being redirected to the associated Minecraft server.
Portals can have any size, but the bigger they are the more particles a client has to render, so there is a performance tradeoff with extremely large Portals.
The default block a Portal is created with are Purple Stained Glass Panes, but they can easily be replaced with any block that a user can walk into, i.e. Air, Water, Honey, Powdered Snow, Cobwebs, Fences, Trapdoors, Doors, Buttons, etc..

## Languages

| Language | Value (`defaultLanguage` config field) |
| -------- | -------------------------------------- |
| English  | `en_us`                                |
| German   | `de_de`                                |

If you want to provide more translations feel free to open an Issue.

### Multiplayer usage

**Server-side**: The Configuration `defaultLanguage` defines the language all messages are in. (Output on the console is always in English.)

**Client-side**: If the player has installed this mod on their client, `defaultLanguage` is overridden with the client's language setting.

### Singleplayer usage

The Configuration `defaultLanguage` has no effect; the client's language setting is used.

## Installation

Requires [Fabric API](https://modrinth.com/mod/fabric-api) to be installed.

- Download the `.jar` file from the [Releases page](https://github.com/SebiTimeWaster/Public-Teleport/releases) that fits your Minecraft version and put it into the `mods` folder on your server/client
- (Re)Start your server/client
- If needed edit the configuration as described in the section "Configuration" above

<hr>

<sub>\* Bus model by [TheJeroen](https://www.planetminecraft.com/project/bravo-public-buses-arriva-netherlands-vehicles-1-19-3/)</sub>
