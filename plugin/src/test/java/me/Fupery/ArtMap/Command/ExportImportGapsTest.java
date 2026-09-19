package me.Fupery.ArtMap.Command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Command.CommandExport.ArtworkExport;
import me.Fupery.ArtMap.IO.CompressedMap;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The error and by-name branches of export and import that the round-trip tests skip. */
public class ExportImportGapsTest extends PluginTestSupport {

    private Player admin;
    private Database database;
    private final List<File> written = new java.util.ArrayList<>();

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        when(Bukkit.getScheduler().runTaskAsynchronously(any(), any(Runnable.class)))
                .thenAnswer(i -> { ((Runnable) i.getArgument(1)).run(); return null; });
        when(mocks.getMockServer().getPlayer(anyString())).thenAnswer(i -> {
            for (Player p : mocks.getRandomMockPlayers(10)) if (p.getName().equals(i.getArgument(0))) return p;
            return null;
        });
    }

    @BeforeEach
    public void fresh() {
        mocks.resetMockPlayerPermissions();
        admin = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        clearInvocations(admin);
        when(admin.hasPermission("artmap.admin")).thenReturn(true);
        database = mock(Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        ArtMap.instance().getDataFolder().mkdirs();
    }

    @AfterEach
    public void cleanUp() {
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
        for (File f : written) f.delete();
    }

    private File dataFile(String name) {
        File f = new File(ArtMap.instance().getDataFolder(), name);
        f.delete();
        written.add(f);
        return f;
    }

    private ReturnMessage run(AsyncCommand command, String... args) {
        ReturnMessage msg = new ReturnMessage(admin, null);
        command.runCommand(admin, args, msg);
        return msg;
    }

    private MapArt sunset() throws Exception {
        MapArt art = new MapArt(5, "Sunset", admin.getUniqueId(), admin.getName(), "01-01-2026");
        when(database.getArtworkCompressedMap(5)).thenReturn(Optional.of(CompressedMap.compress(5, new byte[Map.Size.MAX.value])));
        return art;
    }

    @Test
    @DisplayName("every export mode reports a database failure instead of writing")
    public void exportDatabaseFailures() throws Exception {
        when(database.listArtists()).thenThrow(new SQLException("down"));
        assertEquals("Database error! Check logs.", run(new CommandExport(), "export", "-all", "x").message);

        when(database.listMapArt(admin.getUniqueId())).thenThrow(new SQLException("down"));
        assertEquals("Database error! Check logs.", run(new CommandExport(), "export", "-artist", admin.getName(), "x").message);

        when(database.getArtwork("Boom")).thenThrow(new SQLException("down"));
        assertEquals("Database error! Check logs.", run(new CommandExport(), "export", "-title", "Boom", "x").message);
    }

    @Test
    @DisplayName("-artist and -title need a name and a file")
    public void exportShortArguments() {
        assertEquals(Lang.COMMAND_EXPORT.get(), run(new CommandExport(), "export", "-artist", "x").message);
        assertEquals(Lang.COMMAND_EXPORT.get(), run(new CommandExport(), "export", "-title", "x").message);
    }

    @Test
    @DisplayName("an artist can be named instead of given by id, and an empty result is reported")
    public void exportArtistByName() throws Exception {
        MapArt art = sunset();
        when(database.listMapArt(admin.getUniqueId())).thenReturn(List.of(art));
        File out = dataFile("byName.json");
        run(new CommandExport(), "export", "-artist", admin.getName(), "byName");
        assertTrue(out.exists());

        when(database.listMapArt(admin.getUniqueId())).thenReturn(List.of());
        run(new CommandExport(), "export", "-artist", admin.getName(), "empty");
        verify(admin).sendMessage("No artwork found export!");
    }

    @Test
    @DisplayName("a map that cannot be read is reported and skipped, and the rest is still written")
    public void exportMapReadFailure() throws Exception {
        MapArt art = new MapArt(6, "Broken", admin.getUniqueId(), admin.getName(), "01-01-2026");
        when(database.getArtwork("Broken")).thenReturn(Optional.of(art));
        when(database.getArtworkCompressedMap(6)).thenThrow(new SQLException("down"));
        File out = dataFile("broken.json");
        ReturnMessage msg = run(new CommandExport(), "export", "-title", "Broken", "broken");
        assertEquals("Database error! Check logs.", msg.message);
        verify(admin).sendMessage(contains("Skipping"));
        assertTrue(out.exists());
    }

    @Test
    @DisplayName("import -artist accepts a player name and -artist/-title need a value")
    public void importByName() throws Exception {
        MapArt art = sunset();
        MapArt other = new MapArt(7, "Other", UUID.randomUUID(), "Someone", "01-01-2026");
        when(database.getArtworkCompressedMap(7)).thenReturn(Optional.of(CompressedMap.compress(7, new byte[Map.Size.MAX.value])));
        File f = dataFile("byName.json");
        Files.write(f.toPath(), new Gson().toJson(List.of(
                new ArtworkExport(art, database.getArtworkCompressedMap(5).get()),
                new ArtworkExport(other, database.getArtworkCompressedMap(7).get()))).getBytes(StandardCharsets.UTF_8));
        when(database.getArtwork(anyString())).thenReturn(Optional.empty());
        when(database.createMap()).thenAnswer(i -> new Map(9000));

        run(new CommandImport(), "import", "-artist", admin.getName(), "byName.json");
        verify(database).saveArtwork(argThat((MapArt a) -> a.getTitle().equals("Sunset")), any(CompressedMap.class));
        verify(database, org.mockito.Mockito.never()).saveArtwork(argThat((MapArt a) -> a.getTitle().equals("Other")), any(CompressedMap.class));

        assertEquals(Lang.COMMAND_EXPORT.get(), run(new CommandImport(), "import", "-artist", "byName.json").message);
        assertEquals(Lang.COMMAND_EXPORT.get(), run(new CommandImport(), "import", "-title", "byName.json").message);
    }
}
