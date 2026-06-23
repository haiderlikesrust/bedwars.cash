Custom lobby / arena maps
=========================

Place a complete Minecraft world folder here, e.g.:

  plugins/BedWarsCash/maps/asian_hub/level.dat
  plugins/BedWarsCash/maps/asian_hub/region/...

Then in config.yml:

  lobby:
    mode: custom
    template: asian_hub
    world: bwc_lobby

  arena:
    mode: custom
    template: ginko_arena
    world: bwc_arena

Stand on the spawn point in-game and run:  /bwc setlobby
For custom arenas, set the countdown area with /bwc setwait and beds with /bwc setbed <team>.

The world is copied into the server folder on first start (server/bwc_lobby/ or server/bwc_arena/).
Rebuild the map in the template folder and delete the imported world folder to re-import.

Build the hub in creative (WorldEdit, Axiom, etc.), export the world folder, and drop it here.
