package me.Fupery.ArtMap.Command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import com.google.gson.reflect.TypeToken;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Command.CommandExport.ArtworkExport;
import me.Fupery.ArtMap.IO.CompressedMap;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The JSON export record and the export / import commands around it. */
public class ArtworkExportTest extends PluginTestSupport {

    private static int nextMapId = 9900;
    private static final UUID ARTIST = UUID.randomUUID();

    private Player admin;
    private Database database;
    private MapArt sunset;
    private CompressedMap sunsetMap;
    private final java.util.List<File> written = new java.util.ArrayList<>();

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        // The import runs its batch asynchronously; run it inline here.
        when(Bukkit.getScheduler().runTaskAsynchronously(any(), any(Runnable.class)))
                .thenAnswer(i -> { ((Runnable) i.getArgument(1)).run(); return null; });
    }

    @BeforeEach
    public void fresh() throws Exception {
        admin = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        clearInvocations(admin);
        when(admin.hasPermission("artmap.admin")).thenReturn(true);
        database = mock(Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        when(database.createMap()).thenAnswer(i -> new Map(nextMapId++));
        sunsetMap = CompressedMap.compress(5, new byte[Map.Size.MAX.value]);
        sunset = new MapArt(5, "Sunset", ARTIST, "Artist", "01-01-2026");
        when(database.getArtworkCompressedMap(5)).thenReturn(Optional.of(sunsetMap));
        ArtMap.instance().getDataFolder().mkdirs();
    }

    @AfterEach
    public void cleanUp() {
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
        mocks.resetMockPlayerPermissions();
        for (File f : written) f.delete();
        written.clear();
    }

    private File dataFile(String name) {
        File f = new File(ArtMap.instance().getDataFolder(), name);
        written.add(f);
        return f;
    }

    private ReturnMessage run(AsyncCommand command, String... args) {
        ReturnMessage msg = new ReturnMessage(admin, null);
        command.runCommand(admin, args, msg);
        return msg;
    }

    private static List<ArtworkExport> parse(File f) throws Exception {
        try (java.io.Reader r = Files.newBufferedReader(f.toPath())) {
            return new Gson().fromJson(r, new TypeToken<List<ArtworkExport>>() {}.getType());
        }
    }

    // ---- the record ------------------------------------------------------

    @Test
    @DisplayName("an export survives a JSON round trip without its map id")
    public void jsonRoundTrip() {
        ArtworkExport export = new ArtworkExport(sunset, sunsetMap);
        String json = new Gson().toJson(export);
        assertFalse(json.contains("mapId"), "map ids are reassigned on import and must not be exported");
        ArtworkExport back = new Gson().fromJson(json, ArtworkExport.class);
        assertEquals("Sunset", back.getTitle());
        assertEquals(ARTIST, back.getArtist());
        assertTrue(back.toString().contains("Sunset"));
    }

    @Test
    @DisplayName("importing a new artwork creates a map and saves it under the exported title")
    public void importSavesNewArtwork() throws Exception {
        when(database.getArtwork("Sunset")).thenReturn(Optional.empty());
        new ArtworkExport(sunset, sunsetMap).importArtwork(admin);
        verify(database).saveArtwork(argThat((MapArt a) -> a.getTitle().equals("Sunset") && a.getArtist().equals(ARTIST)), any(CompressedMap.class));
    }

    @Test
    @DisplayName("an identical artwork already in the database is skipped as a duplicate")
    public void importSkipsIdenticalArtwork() throws Exception {
        MapArt existing = new MapArt(nextMapId++, "Sunset", ARTIST, "Artist", "01-01-2026");
        when(database.getArtwork("Sunset")).thenReturn(Optional.of(existing));
        new ArtworkExport(sunset, sunsetMap).importArtwork(admin);
        verify(database, never()).saveArtwork(any(MapArt.class), any(CompressedMap.class));
    }

    @Test
    @DisplayName("a title clash with a different artwork is imported under a suffixed title")
    public void importRenamesOnTitleClash() throws Exception {
        MapArt other = new MapArt(nextMapId++, "Sunset", UUID.randomUUID(), "Someone", "02-02-2026");
        when(database.getArtwork("Sunset")).thenReturn(Optional.of(other));
        new ArtworkExport(sunset, sunsetMap).importArtwork(admin);
        verify(database).saveArtwork(argThat((MapArt a) -> a.getTitle().equals("Sunset_1")), any(CompressedMap.class));
    }

    @Test
    @DisplayName("a database failure during import is logged and nothing is saved")
    public void importDatabaseFailureIsContained() throws Exception {
        when(database.getArtwork(anyString())).thenThrow(new java.sql.SQLException("down"));
        new ArtworkExport(sunset, sunsetMap).importArtwork(admin);
        verify(database, never()).saveArtwork(any(MapArt.class), any(CompressedMap.class));
    }

    // ---- export command ----------------------------------------------------

    @Test
    @DisplayName("export -all writes every artist's artworks to a json file in the data folder")
    public void exportAllWritesFile() throws Exception {
        when(database.listArtists()).thenReturn(List.of(ARTIST));
        when(database.listMapArt(ARTIST)).thenReturn(List.of(sunset));
        File out = dataFile("exportAll.json");
        out.delete();

        run(new CommandExport(), "export", "-all", "exportAll");

        assertTrue(out.exists(), "the .json suffix is added when missing");
        List<ArtworkExport> exported = parse(out);
        assertEquals(1, exported.size());
        assertEquals("Sunset", exported.get(0).getTitle());
        verify(admin).sendMessage(contains("Completed export of 1"));
    }

    @Test
    @DisplayName("export -title and -artist select their artworks, and refuse to overwrite a file")
    public void exportByTitleAndArtist() throws Exception {
        when(database.getArtwork("Sunset")).thenReturn(Optional.of(sunset));
        when(database.listMapArt(ARTIST)).thenReturn(List.of(sunset));
        File byTitle = dataFile("byTitle.json");
        byTitle.delete();
        run(new CommandExport(), "export", "-title", "Sunset", "byTitle.json");
        assertEquals(1, parse(byTitle).size());

        File byArtist = dataFile("byArtist.json");
        byArtist.delete();
        run(new CommandExport(), "export", "-artist", ARTIST.toString(), "byArtist");
        assertEquals(1, parse(byArtist).size());

        run(new CommandExport(), "export", "-artist", ARTIST.toString(), "byArtist");
        verify(admin).sendMessage(contains("all ready exists"));

        when(database.getArtwork("Nope")).thenReturn(Optional.empty());
        ReturnMessage msg = run(new CommandExport(), "export", "-title", "Nope", "x");
        assertTrue(msg.message.contains("Nope"));
    }

    @Test
    @DisplayName("an artwork with no map data is skipped and reported")
    public void exportSkipsArtworkWithoutMap() throws Exception {
        MapArt orphan = new MapArt(6, "Orphan", ARTIST, "Artist", "01-01-2026");
        when(database.getArtwork("Orphan")).thenReturn(Optional.of(orphan));
        when(database.getArtworkCompressedMap(6)).thenReturn(Optional.empty());
        File out = dataFile("orphan.json");
        out.delete();
        run(new CommandExport(), "export", "-title", "Orphan", "orphan");
        verify(admin).sendMessage(contains("Skipping"));
        assertEquals(0, parse(out).size());
    }

    // ---- import command ----------------------------------------------------

    private File exportFileWith(String name, ArtworkExport... arts) throws Exception {
        File f = dataFile(name);
        Files.write(f.toPath(), new Gson().toJson(List.of(arts)).getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    @DisplayName("import -all imports every artwork in the file")
    public void importAll() throws Exception {
        exportFileWith("importAll.json", new ArtworkExport(sunset, sunsetMap));
        when(database.getArtwork(anyString())).thenReturn(Optional.empty());

        run(new CommandImport(), "import", "-all", "importAll");

        verify(admin).sendMessage(contains("1 artworks available"));
        verify(database).saveArtwork(any(MapArt.class), any(CompressedMap.class));
        verify(admin).sendMessage(contains("Import complete"));
    }

    @Test
    @DisplayName("import -title and -artist filter the file's artworks")
    public void importFiltered() throws Exception {
        MapArt dawn = new MapArt(7, "Dawn", UUID.randomUUID(), "Other", "03-03-2026");
        when(database.getArtworkCompressedMap(7)).thenReturn(Optional.of(sunsetMap));
        exportFileWith("importSome.json", new ArtworkExport(sunset, sunsetMap), new ArtworkExport(dawn, sunsetMap));
        when(database.getArtwork(anyString())).thenReturn(Optional.empty());

        run(new CommandImport(), "import", "-title", "Dawn", "importSome.json");
        verify(database).saveArtwork(argThat((MapArt a) -> a.getTitle().equals("Dawn")), any(CompressedMap.class));
        verify(database, never()).saveArtwork(argThat((MapArt a) -> a.getTitle().equals("Sunset")), any(CompressedMap.class));

        run(new CommandImport(), "import", "-artist", ARTIST.toString(), "importSome.json");
        verify(database).saveArtwork(argThat((MapArt a) -> a.getTitle().equals("Sunset")), any(CompressedMap.class));

        run(new CommandImport(), "import", "-artist", "NoSuchPlayerName", "importSome.json");
        verify(admin).sendMessage(contains("Player not found"));
    }

    @Test
    @DisplayName("import refuses missing files, bad modes and short argument lists")
    public void importArgumentErrors() {
        run(new CommandImport(), "import", "-all", "doesNotExist");
        verify(admin).sendMessage(contains("cannot be found"));
        assertEquals(me.Fupery.ArtMap.api.Config.Lang.COMMAND_EXPORT.get(), run(new CommandImport(), "import", "-nonsense", "x").message);
        assertEquals(me.Fupery.ArtMap.api.Config.Lang.COMMAND_EXPORT.get(), run(new CommandImport(), "import", "-title", "x").message);
        assertEquals(me.Fupery.ArtMap.api.Config.Lang.COMMAND_EXPORT.get(), run(new CommandImport(), "import").message);
        when(admin.hasPermission("artmap.admin")).thenReturn(false);
        assertEquals(me.Fupery.ArtMap.api.Config.Lang.NO_PERM.get(), run(new CommandImport(), "import", "-all", "x").message);
    }
}
