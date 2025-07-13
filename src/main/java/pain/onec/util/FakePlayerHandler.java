package pain.onec.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.SkinTextures.Model;

import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/* Fake player handler but it uses skin.png from this mod
 * Taken from meteor client's source, smashed into a single file
 * Modified by mmorphig
 */

public class FakePlayerHandler {
    private static final List<FakePlayer> fakePlayers = new ArrayList<>();

    public static class FakePlayer extends OtherClientPlayerEntity {
        private PlayerListEntry playerListEntry;
        private static final Identifier CUSTOM_SKIN = Identifier.of("onec:skin.png");
        private SkinTextures skinTextures;

        public FakePlayer(String name, float health) {
            super(MinecraftClient.getInstance().world, new GameProfile(UUID.randomUUID(), name));

            setHealth(health);
            setYaw(0);
            setPitch(0);

            skinTextures = new SkinTextures(
                CUSTOM_SKIN,
                null, null, null,
                Model.WIDE,
                false
            );
        }

        public void spawn() {
            unsetRemoved();
            MinecraftClient.getInstance().world.addEntity(this);
        }

        public void despawn() {
            MinecraftClient.getInstance().world.removeEntity(getId(), RemovalReason.DISCARDED);
            setRemoved(RemovalReason.DISCARDED);
        }

        @Override
        public SkinTextures getSkinTextures() {
            return skinTextures;
        }

        @Override
        protected PlayerListEntry getPlayerListEntry() {
            return null;
        }
    }

    public static List<FakePlayer> getFakePlayers() {
        return fakePlayers;
    }

    public static FakePlayer get(String name) {
        for (FakePlayer fp : fakePlayers) {
            if (fp.getName().getString().equals(name)) return fp;
        }
        return null;
    }

    public static void add(String name, float health) {
        if (MinecraftClient.getInstance().world == null) return;

        FakePlayer fakePlayer = new FakePlayer(name, health);
        fakePlayer.spawn();
        fakePlayers.add(fakePlayer);
    }

    public static void remove(FakePlayer fp) {
        fakePlayers.removeIf(fp1 -> {
            if (fp1.getName().getString().equals(fp.getName().getString())) {
                fp1.despawn();
                return true;
            }
            return false;
        });
    }

    public static void clear() {
        if (fakePlayers.isEmpty()) return;
        fakePlayers.forEach(FakePlayer::despawn);
        fakePlayers.clear();
    }

    public static void forEach(Consumer<FakePlayer> action) {
        for (FakePlayer fp : fakePlayers) action.accept(fp);
    }

    public static int count() {
        return fakePlayers.size();
    }

    public static Stream<FakePlayer> stream() {
        return fakePlayers.stream();
    }

    public static boolean contains(FakePlayer fp) {
        return fakePlayers.contains(fp);
    }
}
