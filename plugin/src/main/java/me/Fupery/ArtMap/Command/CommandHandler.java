package me.Fupery.ArtMap.Command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MapView.Scale;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.Event.PlayerOpenMenuEvent;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.Menu.Handler.MenuHandler;
import me.Fupery.ArtMap.Recipe.ArtItem;
import me.Fupery.ArtMap.Recipe.ArtMaterial;
import me.Fupery.ArtMap.Utils.ItemUtils;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final HashMap<String, AsyncCommand> commands;

    public CommandHandler() {
        commands = new HashMap<>();
        //Commands go here - note that they are run on an async thread

		commands.put("save", new CommandSave());

		commands.put("clear", new CommandClear());

		commands.put("delete", new CommandDelete());

		commands.put("preview", new CommandPreview());

		commands.put("import", new CommandImport());

		commands.put("export", new CommandExport());

		commands.put("test", new CommandTest());

		commands.put("convert", new CommandConvert());

		commands.put("repair", new Repair());

		commands.put("search", new Search());

		commands.put("page", new Page());

		commands.put("reload", new AsyncCommand("artmap.admin", "/art reload", true) {
			@Override
			public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
				ArtMap.instance().getScheduler().SYNC.run(() -> {
					ArtMap.instance().reloadRuntimeConfig();
					if (sender instanceof Player) {
						ArtMap.instance().getScheduler().SYNC.run((Player) sender,
								() -> sender.sendMessage("ArtMap reloaded config.yml, lang.yml and menu/*.yml."));
					} else {
						sender.sendMessage("ArtMap reloaded config.yml, lang.yml and menu/*.yml.");
					}
				});
			}
		});

		commands.put("palette", new AsyncCommand("artmap.admin", "/art palette", true) {
			@Override
			public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
				final Player player = (Player) sender;
				// Capture the world on the player's thread, create the map on the global region,
				// then hand the finished item back on the player's thread.
				ArtMap.instance().getScheduler().SYNC.run(player, () -> {
					final org.bukkit.World world = player.getWorld();
					ArtMap.instance().getScheduler().SYNC.run(() -> {
						MapView mapView = Bukkit.getServer().createMap(world);
						mapView.getRenderers().clear();
						mapView.setScale(Scale.CLOSEST);
						mapView.addRenderer(new MapRenderer() {
							boolean done = false;
							@Override
							public void render(MapView view, MapCanvas canvas, Player rendererPlayer) {
								if (!done) {
									for (int y = 0; y < 128; y++) {
										for (int x = 0; x < 128; x++) {
											if (x < 64) {
												canvas.setPixel(x, y, (byte) (y));
											} else {
												canvas.setPixel(x, y, (byte) (y + 128));
											}
										}
									}
									done = true;
								}
							}
						});
						ItemStack map = new ItemStack(Material.FILLED_MAP, 1);
						MapMeta meta = (MapMeta) map.getItemMeta();
						meta.setMapView(mapView);
						map.setItemMeta(meta);
						ArtMap.instance().getScheduler().SYNC.run(player, () -> player.getInventory().setItemInMainHand(map));
					});
				});
			}
		});
		
		commands.put("give", new AsyncCommand("artmap.admin", "/art give <player> <easel|canvas|paintbrush|unsaved:<id>|artwork:<title>> [amount]", true) {
			@Override
			public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
				Player player = Bukkit.getPlayer(args[1]);
				if (player != null) {
					ItemStack item = null;
					if (args[2].equalsIgnoreCase("easel")) {
						item = ArtMaterial.EASEL.getItem();
					} else if (args[2].equalsIgnoreCase("canvas")) {
						item = ArtMaterial.CANVAS.getItem();
					} else if (args[2].equalsIgnoreCase("paintbrush")) {
						item = ArtMaterial.PAINT_BRUSH.getItem();
					} else if (args[2].contains("unsaved:")) {
						String[] strings = args[2].split(":");
						if (strings.length > 1) {
							int id = Integer.parseInt(strings[1]);
							try {
								if(!ArtMap.instance().getArtDatabase().containsUnsavedArtwork(id)) {
									sender.sendMessage(Lang.PREFIX + ChatColor.RED + "No unsaved artwork with that ID.");
									return;
								}
								item = new ArtItem.InProgressArtworkItem(id, player.getName()).toItemStack();
							} catch( Exception e) {
								sender.sendMessage(Lang.PREFIX + ChatColor.RED + "Error retrieving art! Check logs for details.");
								ArtMap.instance().getLogger().log(Level.SEVERE, "Error retrieving art!", e);
							}
						}
					} else if (args[2].contains("artwork:")) {
						String[] strings = args[2].split(":");
						if (strings.length > 1) {
							String title = strings[1];
							try {
								Optional<MapArt> art = ArtMap.instance().getArtDatabase().getArtwork(title);
								if (!art.isPresent()) {
									sender.sendMessage(Lang.PREFIX + ChatColor.RED + String.format(Lang.MAP_NOT_FOUND.get(), title));
									return;
								}
								item = art.get().getMapItem();
							} catch( Exception e) {
								sender.sendMessage(Lang.PREFIX + ChatColor.RED + "Error retrieving art! Check logs for details.");
								ArtMap.instance().getLogger().log(Level.SEVERE, "Error retrieving art!", e);
							}
						}
					}
					if (item == null) {
						sender.sendMessage(Lang.PREFIX + ChatColor.RED + this.usage);
						return;
					}
					if (args.length > 3) {
						int amount = Integer.parseInt(args[3]);
						if (amount > 1)
							item.setAmount(amount);
					}
					ItemStack finalItem = item;
					ArtMap.instance().getScheduler().SYNC.run(player, () -> ItemUtils.giveItem(player, finalItem));
					return;
				}
				sender.sendMessage(Lang.PREFIX + ChatColor.RED + String.format(Lang.PLAYER_NOT_FOUND.get(), args[1]));
			}
		});

        //convenience commands
		commands.put("artists", new AsyncCommand("artmap.menu", "/art", true) {
            @Override
            public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
                if (!(sender instanceof Player)) {
                    Lang.Array.CONSOLE_HELP.send(sender);
                    return;
                }
                ArtMap.instance().getScheduler().SYNC.run((Player) sender, () -> {
                    PlayerOpenMenuEvent event = new PlayerOpenMenuEvent((Player) sender);
                    Bukkit.getServer().getPluginManager().callEvent(event);
                    MenuHandler menuHandler = ArtMap.instance().getMenuHandler();
                    menuHandler.openMenu(((Player) sender), menuHandler.MENU.ARTIST.get(((Player) sender)));
                });
            }
        });

		commands.put("help", new AsyncCommand("artmap.menu", "/art help", true) {
            @Override
            public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
                Lang.Array.CONSOLE_HELP.send(sender);
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 0) return Collections.emptyList();
        if (args.length == 1) {
            Set<String> names = new LinkedHashSet<>();
            for (String name : commands.keySet()) {
                if (commands.get(name).canUse(sender)) names.add(name);
            }
            return filter(names, args[0]);
        }

        String sub = args[0].toLowerCase();
        AsyncCommand handler = commands.get(sub);
        if (handler == null || !handler.canUse(sender)) return Collections.emptyList();
        switch (sub) {
            case "give":
                if (args.length == 2) return filter(onlinePlayers(), args[1]);
                if (args.length == 3) return filter(List.of("easel", "canvas", "paintbrush", "unsaved:", "artwork:"), args[2]);
                if (args.length == 4) return filter(List.of("1", "2", "3", "5", "10", "16", "32", "64"), args[3]);
                return Collections.emptyList();
            case "delete":
            case "preview":
                if (args.length == 2) return filter(artworkTitles(), args[1]);
                return Collections.emptyList();
            case "repair":
                if (args.length == 2) return filter(List.of("-scan", "-repair"), args[1]);
                if (args.length == 3) return filter(List.of("-all"), args[2]);
                return Collections.emptyList();
            case "search":
                if (args.length == 2) return filter(List.of("--mine", "--player"), args[1]);
                if (args.length == 3 && "--player".equalsIgnoreCase(args[1])) return filter(onlinePlayers(), args[2]);
                return Collections.emptyList();
            case "import":
            case "export":
                if (args.length == 2) return filter(List.of("-all", "-artist", "-title"), args[1]);
                if (args.length == 3 && "-artist".equalsIgnoreCase(args[1])) return filter(artistNames(), args[2]);
                if (args.length == 3 && "-title".equalsIgnoreCase(args[1])) return filter(artworkTitles(), args[2]);
                return Collections.emptyList();
            case "page":
                if (args.length == 2) return filter(onlinePlayerIds(), args[1]);
                if (args.length == 3) return filter(List.of("1", "2", "3", "4", "5"), args[2]);
                return Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }

    private List<String> filter(Iterable<String> options, String prefix) {
        List<String> matches = new ArrayList<>();
        String lower = prefix == null ? "" : prefix.toLowerCase();
        for (String option : options) {
            if (option != null && option.toLowerCase().startsWith(lower)) matches.add(option);
        }
        return matches;
    }

    private List<String> onlinePlayers() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
        return names;
    }

    private List<String> onlinePlayerIds() {
        List<String> ids = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) ids.add(player.getUniqueId().toString());
        return ids;
    }

    private List<String> artworkTitles() {
        List<String> titles = new ArrayList<>();
        try {
            for (MapArt art : ArtMap.instance().getArtDatabase().listMapArt()) titles.add(art.getTitle());
        } catch (Exception ignored) {
        }
        return titles;
    }

    private List<String> artistNames() {
        try {
            return ArtMap.instance().getHeadsCache().searchCache("");
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
		// handle quoted arguements since spigot does not
		String[] fixedArgs = fixQuotedArgs(args);

		if (fixedArgs.length > 0) {

			if (commands.containsKey(fixedArgs[0].toLowerCase())) {
				commands.get(fixedArgs[0].toLowerCase()).runPlayerCommand(sender, fixedArgs);
            } else {
                Lang.HELP.send(sender);
            }

        } else {
			commands.get("artists").runPlayerCommand(sender, fixedArgs);
        }
        return true;
    }

	/**
	 * Combines "" arguments.
	 * 
	 * @param args The original args.
	 * @return The combined args.
	 */
	public static String[] fixQuotedArgs(String[] args) {
		ArrayList<String> newArgs = new ArrayList<>();
		String combined = null;
		for (String arg : args) {
			// handle quoted single word
			if (arg.startsWith("\"") && arg.endsWith("\"")) {
				newArgs.add(arg.replace("\"", ""));
				continue;
			}

			// start combine
			if (combined == null && arg.contains("\"")) {
				combined = arg.replace("\"", "");
				continue;
			}
			// end combine
			if (combined != null && arg.contains("\"")) {
				combined += " " + arg.replace("\"", "");
				newArgs.add(combined);
				combined = null;
				continue;
			}

			// add to combined if its not null otherwise its a lone arg
			if (combined != null) {
				combined += " " + arg;
			} else {
				newArgs.add(arg);
			}
		}
		return newArgs.toArray(new String[0]);
	}

}