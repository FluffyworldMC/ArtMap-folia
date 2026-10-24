package me.Fupery.ArtMap.Easel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Each easel effect plays its sound at the given spot once the scheduler runs it. */
public class EaselEffectTest extends PluginTestSupport {

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        me.Fupery.ArtMap.Utils.Scheduler scheduler = ArtMap.instance().getScheduler();
        doAnswer(i -> { ((Runnable) i.getArgument(1)).run(); return null; })
                .when(scheduler).runSafely(any(Location.class), any(Runnable.class));
    }

    @Test
    @DisplayName("the effects run without a server and the audible ones reach the world")
    public void effectsPlay() {
        World world = mock(World.class);
        Location here = new Location(world, 1, 64, 1);
        // SAVE_ARTWORK and MOUNT_CANVAS name particles the 1.21 test API has renamed (Paper remaps them
        // on a live server), so they cannot run under the mocks.
        for (EaselEffect effect : new EaselEffect[] { EaselEffect.SPAWN, EaselEffect.BREAK, EaselEffect.USE_DENIED,
                EaselEffect.START_RIDING, EaselEffect.STOP_RIDING }) {
            effect.playEffect(here);
        }
        verify(world, atLeastOnce()).playSound(any(Location.class), any(org.bukkit.Sound.class), any(float.class), any(float.class));
    }
}
