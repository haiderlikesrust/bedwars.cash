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
        File dest = new File(Bukkit.getWorldContainer(), worldName);
        if (!dest.exists() || !new File(dest, "level.dat").exists()) {
            plugin.getLogger().info("Importing map template '" + template + "' into world '" + worldName + "'...");
            copyDirectory(templateDir(plugin, template).toPath(), dest.toPath());
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) return world;
        return new WorldCreator(worldName).createWorld();
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
