# server/

Paper 26.1.2 server runtime (**requires Java 25**). Jars and world files are gitignored.

Place here:
- `paper.jar` (Paper 26.1.2) - https://papermc.io/downloads/paper
- `plugins/BedWars2026.jar` (+ dependencies) - a BedWars2026 fork that supports MC 26.1
  (e.g. `swe3tie/BedWars2026`, which keeps the `com.tomkeuper` API)
- `plugins/GrimAC.jar` - https://modrinth.com/plugin/grimac (26.1-compatible build)
- `plugins/bedwarscash-0.1.0.jar` - built from `../plugins/bedwarscash` (`./gradlew build`)

Then (must be Java 25):

```bash
java -version            # must report 25
java -Xms2G -Xmx4G -jar paper.jar nogui
```

Set `eula=true` in `eula.txt` and `online-mode=true` in `server.properties`.
If your BedWars fork doesn't fire the `com.tomkeuper` GameEndEvent, an op reports the
winner with `/bwresult <green|blue|red|yellow>`.
See ../docs/SETUP.md for the full guide.
