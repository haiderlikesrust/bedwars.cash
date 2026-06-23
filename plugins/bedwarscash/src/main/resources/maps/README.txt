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

Stand on the spawn point in-game and run:  /bwc setlobby

The world is copied into the server folder on first start (server/bwc_lobby/).
Rebuild the map in the template folder and delete server/bwc_lobby/ to re-import.

Build the hub in creative (WorldEdit, Axiom, etc.), export the world folder, and drop it here.
