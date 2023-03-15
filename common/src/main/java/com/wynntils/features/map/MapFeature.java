/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.features.map;

import com.google.common.reflect.TypeToken;
import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.core.config.Category;
import com.wynntils.core.config.Config;
import com.wynntils.core.config.ConfigCategory;
import com.wynntils.core.config.HiddenConfig;
import com.wynntils.core.config.RegisterConfig;
import com.wynntils.core.features.Feature;
import com.wynntils.core.features.properties.RegisterKeyBind;
import com.wynntils.core.json.TypeOverride;
import com.wynntils.core.keybinds.KeyBind;
import com.wynntils.features.overlays.CustomBarsOverlayFeature;
import com.wynntils.mc.event.PlayerInteractEvent;
import com.wynntils.mc.event.ScreenOpenedEvent;
import com.wynntils.models.map.PoiLocation;
import com.wynntils.models.map.pois.CustomPoi;
import com.wynntils.screens.maps.MainMapScreen;
import com.wynntils.screens.maps.PoiCreationScreen;
import com.wynntils.utils.MathUtils;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.mc.McUtils;
import com.wynntils.utils.render.Texture;
import com.wynntils.utils.render.type.PointerType;
import com.wynntils.utils.render.type.TextShadow;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@ConfigCategory(Category.MAP)
public class MapFeature extends Feature {
    public static MapFeature INSTANCE;

    @RegisterConfig
    public final HiddenConfig<List<CustomPoi>> customPois = new HiddenConfig<>(new ArrayList<>());

    @TypeOverride
    private final Type customPoisType = new TypeToken<ArrayList<CustomPoi>>() {}.getType();

    @RegisterConfig
    public final Config<Float> poiFadeAdjustment = new Config<>(0.4f);

    @RegisterConfig
    public final Config<Float> combatPoiMinZoom = new Config<>(0.166f);

    @RegisterConfig
    public final Config<Float> cavePoiMinZoom = new Config<>(0.28f);

    @RegisterConfig
    public final Config<Float> servicePoiMinZoom = new Config<>(0.8f);

    @RegisterConfig
    public final Config<Float> fastTravelPoiMinZoom = new Config<>(0.166f);

    @RegisterConfig
    public final Config<Float> customPoiMinZoom = new Config<>(0.28f);

    @RegisterConfig
    public final Config<Float> lootChestTier1PoiMinZoom = new Config<>(0.8f);

    @RegisterConfig
    public final Config<Float> lootChestTier2PoiMinZoom = new Config<>(0.8f);

    @RegisterConfig
    public final Config<Float> lootChestTier3PoiMinZoom = new Config<>(0.28f);

    @RegisterConfig
    public final Config<Float> lootChestTier4PoiMinZoom = new Config<>(0.28f);

    @RegisterConfig
    public final Config<PointerType> pointerType = new Config<>(PointerType.Arrow);

    @RegisterConfig
    public final Config<CustomColor> pointerColor = new Config<>(new CustomColor(1f, 1f, 1f, 1f));

    @RegisterConfig
    public final Config<Boolean> renderUsingLinear = new Config<>(true);

    @RegisterConfig
    public final Config<Float> playerPointerScale = new Config<>(1.5f);

    @RegisterConfig
    public final Config<Float> poiScale = new Config<>(1f);

    @RegisterConfig
    public final Config<Boolean> autoWaypointChests = new Config<>(true);

    @RegisterConfig
    public final Config<ChestTier> minTierForAutoWaypoint = new Config<>(ChestTier.TIER_3);

    @RegisterConfig
    public final Config<Boolean> renderRemoteFriendPlayers = new Config<>(true);

    @RegisterConfig
    public final Config<Boolean> renderRemotePartyPlayers = new Config<>(true);

    @RegisterConfig
    public final Config<CustomBarsOverlayFeature.HealthTexture> remotePlayerHealthTexture =
            new Config<>(CustomBarsOverlayFeature.HealthTexture.a);

    @RegisterConfig
    public final Config<TextShadow> remotePlayerNameShadow = new Config<>(TextShadow.OUTLINE);

    private BlockPos lastChestPos;

    @RegisterKeyBind
    public final KeyBind openMapKeybind = new KeyBind("Open Main Map", GLFW.GLFW_KEY_M, false, this::openMainMap);

    @RegisterKeyBind
    public final KeyBind newWaypointKeybind =
            new KeyBind("New Waypoint", GLFW.GLFW_KEY_B, true, this::openWaypointSetup);

    private void openMainMap() {
        // If the current screen is already the map, and we get this event, this means we are holding the keybind
        // and should signal that we should close when the key is not held anymore.
        if (McUtils.mc().screen instanceof MainMapScreen mainMapScreen) {
            mainMapScreen.setHoldingMapKey(true);
            return;
        }

        McUtils.mc().setScreen(MainMapScreen.create());
    }

    private void openWaypointSetup() {
        PoiLocation location = new PoiLocation(
                McUtils.player().getBlockX(),
                McUtils.player().getBlockY(),
                McUtils.player().getBlockZ());

        McUtils.mc().setScreen(PoiCreationScreen.create(null, location));
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!autoWaypointChests.get()) return;

        BlockEntity blockEntity = McUtils.mc().level.getBlockEntity(event.getPos());
        if (blockEntity != null && blockEntity.getType() == BlockEntityType.CHEST) {
            lastChestPos = event.getPos();
        }
    }

    @SubscribeEvent
    public void onScreenOpened(ScreenOpenedEvent.Post event) {
        if (!autoWaypointChests.get()) return;
        if (lastChestPos == null) return;
        if (!(event.getScreen() instanceof ContainerScreen)) return;

        Matcher matcher = Models.Container.lootChestMatcher(event.getScreen());
        if (!matcher.matches()) return;

        ChestTier tier = ChestTier.fromString(matcher.group(1));

        if (tier.ordinal() < minTierForAutoWaypoint.get().ordinal()) return;

        PoiLocation location = new PoiLocation(lastChestPos.getX(), lastChestPos.getY(), lastChestPos.getZ());
        CustomPoi newPoi = new CustomPoi(
                location,
                tier.getWaypointName(),
                CommonColors.WHITE,
                tier.getWaypointTexture(),
                CustomPoi.Visibility.DEFAULT);

        if (customPois.get().stream().noneMatch(customPoi -> customPoi.equals(newPoi))) {
            customPois.get().add(newPoi);

            // TODO: Replace this notification with a popup
            Managers.Notification.queueMessage(Component.literal("Added new waypoint for " + tier.getWaypointName())
                    .withStyle(ChatFormatting.AQUA));

            Managers.Config.saveConfig();
        }
    }

    public enum ChestTier {
        TIER_1(Texture.CHEST_T1, "Loot Chest 1"),
        TIER_2(Texture.CHEST_T2, "Loot Chest 2"),
        TIER_3(Texture.CHEST_T3, "Loot Chest 3"),
        TIER_4(Texture.CHEST_T4, "Loot Chest 4");

        private final Texture waypointTexture;
        private final String waypointName;

        ChestTier(Texture waypointTexture, String waypointName) {
            this.waypointTexture = waypointTexture;
            this.waypointName = waypointName;
        }

        private Texture getWaypointTexture() {
            return waypointTexture;
        }

        private String getWaypointName() {
            return waypointName;
        }

        private static ChestTier fromString(String s) {
            return values()[MathUtils.integerFromRoman(s) - 1];
        }
    }
}