package me.Fupery.ArtMap.Listeners;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.mocks.PluginTestSupport;

/**
 * Every {@link RegisteredListener} must tear down *all* of the events it
 * subscribes to.
 *
 * <p>{@code EventManager.unregisterAll()} (called from ArtMap.onDisable) just
 * delegates to each listener's own {@code unregister()}. Any event a listener
 * handles but forgets to unregister stays wired to the old, dead listener
 * instance after a disable/reload - so it keeps firing into a plugin whose
 * ArtMap.instance(), database and ArtistHandler have been torn down.
 *
 * <p>This is checked reflectively rather than by listing the events by hand, so
 * adding an @EventHandler without extending unregister() fails immediately
 * instead of silently leaking.
 */
public class ListenerUnregisterTest extends PluginTestSupport {

    @org.junit.jupiter.api.BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    /** Every listener EventManager actually registers. */
    private static List<Class<? extends RegisteredListener>> registeredListeners() {
        return Arrays.asList(
                PlayerInteractListener.class,
                PlayerInteractEaselListener.class,
                PlayerQuitListener.class,
                ChunkUnloadListener.class,
                PlayerCraftListener.class,
                InventoryInteractListener.class,
                MapInitializeListener.class,
                PlayerSwapHandListener.class,
                PlayerDismountListener.class,
                PlayerJoinEventListener.class,
                PlayerCommandPreListener.class);
    }

    /** Event types this listener subscribes to via @EventHandler. */
    private static Set<Class<?>> handledEvents(Class<?> listener) {
        Set<Class<?>> handled = new LinkedHashSet<>();
        for (Method m : listener.getDeclaredMethods()) {
            if (m.isAnnotationPresent(EventHandler.class) && m.getParameterCount() == 1) {
                handled.add(m.getParameterTypes()[0]);
            }
        }
        return handled;
    }

    private static HandlerList handlerListOf(Class<?> event) {
        try {
            Method m = event.getMethod("getHandlerList");
            return (HandlerList) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Test
    @DisplayName("every listener unregisters every event it handles")
    public void unregisterCoversAllHandledEvents() throws Exception {
        StringBuilder problems = new StringBuilder();

        for (Class<? extends RegisteredListener> type : registeredListeners()) {
            Set<Class<?>> handled = handledEvents(type);
            // Register a real instance against each handled event's HandlerList, run
            // unregister(), then see which ones it actually removed.
            RegisteredListener listener = type.getDeclaredConstructor().newInstance();
            Set<String> leaked = new TreeSet<>();

            for (Class<?> event : handled) {
                HandlerList list = handlerListOf(event);
                if (list == null) {
                    continue;
                }
                org.bukkit.plugin.RegisteredListener probe = new org.bukkit.plugin.RegisteredListener(
                        listener, (l, e) -> { }, org.bukkit.event.EventPriority.NORMAL,
                        null, false);
                list.register(probe);
                listener.unregister();
                boolean stillThere = Arrays.asList(list.getRegisteredListeners()).contains(probe);
                if (stillThere) {
                    leaked.add(event.getSimpleName());
                    list.unregister(probe); // don't poison other tests
                }
            }
            if (!leaked.isEmpty()) {
                problems.append("\n  ").append(type.getSimpleName())
                        .append(" handles but never unregisters: ")
                        .append(leaked.stream().collect(Collectors.joining(", ")));
            }
        }

        assertTrue(problems.length() == 0,
                "listeners leak event handlers on disable/reload:" + problems);
    }

    @Test
    @DisplayName("sanity: the reflective scan actually sees the handlers")
    public void scanFindsHandlers() {
        Set<Class<?>> handled = handledEvents(PlayerQuitListener.class);
        assertTrue(handled.size() >= 4,
                "expected PlayerQuitListener to expose several @EventHandler methods, saw " + handled);
        assertTrue(handled.stream().allMatch(Event.class::isAssignableFrom),
                "every @EventHandler parameter should be a Bukkit Event");
    }
}
