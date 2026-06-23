package cash.bedwars.world;

import cash.bedwars.BedWarsCashPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/** Copies a map template from plugins/BedWarsCash/maps/<name>/ into the server world folder. */
public final class MapImporter {
    private MapImporter() {}

    public static File templateDir(BedWarsCashPlugin plugin, String template) {
        return new File(plugin.getDataFolder(), "maps/" + template);
    }

    public static boolean templateExists(BedWarsCashPlugin plugin, String template) {
        if (template == null || template.isBlank()) return false;
        File dir = templateDir(plugin, template);
        return dir.isDirectory() && new File(dir, "level.dat").exists();
    }

    public static World loadOrImport(BedWarsCashPlugin plugin, String worldName, String template) throws IOException {
        File dest = worldDirectory(worldName);
        File legacy = legacyWorldDirectory(worldName);

        // Paper 26+ migrates legacy folders into world/dimensions — remove duplicate to avoid conflicts.
        if (!dest.equals(legacy) && legacy.isDirectory()) {
            plugin.getLogger().info("Removing legacy world folder (Paper uses dimensions): " + legacy.getPath());
            deleteRecursive(legacy.toPath());
        }

        if (!new File(dest, "level.dat").exists()) {
            plugin.getLogger().info("Importing map template '" + template + "' into " + dest.getPath());
            if (dest.exists()) deleteRecursive(dest.toPath());
            copyDirectory(templateDir(plugin, template).toPath(), dest.toPath());
        }

        deleteIfExists(new File(dest, "session.lock"));
        deleteIfExists(new File(legacy, "session.lock"));

        World world = Bukkit.getWorld(worldName);
        if (world != null) return world;
        return new WorldCreator(worldName).createWorld();
    }

    /** Paper 26 stores custom worlds under world/dimensions/minecraft/<name>. */
    public static File worldDirectory(String worldName) {
        File container = Bukkit.getWorldContainer();
        File dimensionRoot = new File(container, "world/dimensions/minecraft");
        if (dimensionRoot.isDirectory()) {
            return new File(dimensionRoot, worldName);
        }
        return legacyWorldDirectory(worldName);
    }

    private static File legacyWorldDirectory(String worldName) {
        return new File(Bukkit.getWorldContainer(), worldName);
    }

    private static void deleteIfExists(File file) {
        if (file.exists()) file.delete();
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void copyDirectory(Path src, Path dest) throws IOException {
        Files.walk(src).forEach(source -> {
            try {
                Path target = dest.resolve(src.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    if (target.getParent() != null) Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
