package me.Fupery.ArtMap.IO.Legacy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.bukkit.command.CommandSender;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.Command.CommandExport.ArtworkExport;

public abstract class DatabaseConverter {

    protected CommandSender sender;

    public abstract boolean isNeeded();

    public abstract boolean canBeForced();

    protected abstract boolean createExport(boolean force) throws Exception;

    public boolean createExport(CommandSender sender, boolean force) {
        this.sender = sender;
        try {
            return this.createExport(force);
        } catch (Exception e) {
            this.sender.sendMessage(Lang.CONVERT_EXPORT_ERROR.get());
            ArtMap.instance().getLogger().log(Level.SEVERE, "Failure creating export!",e);
            return false;
        }
    }

    protected String export(List<ArtworkExport> export) throws Exception {
        if(export.isEmpty()) {
            return Lang.CONVERT_EXPORT_EMPTY.get();
        }

        File exportFile = File.createTempFile("conversion_", ".json", ArtMap.instance().getDataFolder());
        try (FileWriter writer = new FileWriter(exportFile);) {
            Gson gson = ArtMap.instance().getGson(true);
            Type collectionType = new TypeToken<List<ArtworkExport>>() {
            }.getType();
            gson.toJson(export, collectionType, writer);
            writer.flush();
            writer.close();
            return String.format(Lang.CONVERT_EXPORT_COMPLETE.get(), export.size(), exportFile.getName());
        } catch (IOException e) {
            ArtMap.instance().getLogger().log(Level.WARNING, "Failure writing art export file.", e);
            return Lang.CONVERT_EXPORT_WRITE_ERROR.get();
        }

    }

    protected void sendMessage(String msg) {
        ArtMap.instance().getLogger().info(sender.getName() + " :: " + msg);
        sender.sendMessage(msg);
    }

}