package com.stash.hunt.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalNear;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stash.hunt.Addon;
import com.stash.hunt.Utils;
import com.stash.hunt.modules.searcharea.modes.Spiral;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.*;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Objects;

import static com.stash.hunt.Utils.setPressed;

public class StashMover2 extends Module {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<BlockPos> pearlContainer = sgGeneral.add(new BlockPosSetting.Builder()
        .name("pearl-container")
        .description("The position of the chest that contains the pearls. This should be in range of the pearl.")
        .defaultValue(new BlockPos(0, 0, 0))
        .build()
    );

    private final Setting<Double> chestInteractRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("chest-interact-range")
        .description("The range in blocks to interact with chests")
        .defaultValue(4.0)
        .build()
    );

    private final Setting<Integer> teleportChunkDetectDistance = sgGeneral.add(new IntSetting.Builder()
        .name("teleport-chunk-detect-distance")
        .description("The amount of chunks the player must move to be detected as a pearl teleport.")
        .defaultValue(12)
        .build()
    );

    private final Setting<Double> yRangeForChestDetection = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-range-for-chest-detection")
        .description("The range in the y-axis from the players position to search for chests")
        .defaultValue(5.0)
        .build()
    );

    private final Setting<String> pearlAccountName = sgGeneral.add(new StringSetting.Builder()
        .name("pearl-account-name")
        .description("The name of the account that will pearl you to your base.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> pearlPhrase = sgGeneral.add(new StringSetting.Builder()
        .name("pearl-phrase")
        .description("The phrase that the account will look for in chat to pearl you.")
        .defaultValue("pearl")
        .build()
    );

    private final Setting<Integer> afterPearlDelay = sgGeneral.add(new IntSetting.Builder()
        .name("after-pearl-delay")
        .description("The delay in ticks after pearling to wait before moving to the next state.")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> pearlPitchingDelay = sgGeneral.add(new IntSetting.Builder()
        .name("pearl-pitching-delay")
        .description("The ticks while the player will get into the correct pitch.")
        .defaultValue(50)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<BlockPos> soulSandPos = sgGeneral.add(new BlockPosSetting.Builder()
        .name("soul-sand-pos")
        .description("The position of the sould sand block in the stasis chamber.")
        .defaultValue(new BlockPos(0, 0, 0))
        .build()
    );

    private final Setting<Integer> chestInteractDelay = sgGeneral.add(new IntSetting.Builder()
        .name("chest-interact-delay")
        .description("The delay in ticks between interacting with chests")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> fromNether = sgGeneral.add(new BoolSetting.Builder()
        .name("from-nether")
        .description("Whether the stash is in the nether")
        .defaultValue(false)
        .build()
    );

    private final Setting<BlockPos> glowstonePos = sgGeneral.add(new BlockPosSetting.Builder()
        .name("glowstone-shulker-pos")
        .description("The position of the shulker that contains the glowstone.")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(fromNether::get)
        .build()
    );

    private final Setting<Boolean> onlyMoveShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-move-shulkers")
        .description("Only move shulker boxes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Enable debug messages")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> debugTemp = sgGeneral.add(new DoubleSetting.Builder()
        .name("debug-temp")
        .description("Temporary debug setting")
        .defaultValue(10.0)
        .build()
    );

    private final Setting<Double> debugTemp2 = sgGeneral.add(new DoubleSetting.Builder()
        .name("debug-temp2")
        .description("Temporary debug setting")
        .defaultValue(0.950)
        .build()
    );

    private final Setting<State> currState = sgGeneral.add(new EnumSetting.Builder<State>()
        .name("state")
        .description("The current state of the module")
        .defaultValue(State.IDLE_LOOTING)
        .onChanged(s -> {
            state = s;
        })
        .build()
    );

    // Chest positions store either a single chest OR the left chest of a double chest
    private HashSet<BlockPos> depositChests = new HashSet<>();
    private HashSet<BlockPos> lootChests = new HashSet<>();

    private State state = State.IDLE_LOOTING;

    private BlockPos currentPathGoal = null;

    private boolean usedEchest = false;

    private BlockPos beforePearlPos = null;
    private BlockPos beforeRespawnPos = null;

    private int pearlDelay = 0;
    private int pearlPitchDelay = 0;
    private int interactDelay = 0;
    private int portalDelay = 0;
    private int chestOpenDelay = 0;
    private int teleportDelay = 0;

    private int quickMoveTimeout = 0;
    private int chestOpenTimeout = 0;
    private int lootTimeout = 0;

    private boolean waitingForChestUpdate = false;

    /** The amount of inventory slots to keep empty. 1 is used so a pearl can be picked up easily */
    private final int EMPTY_INV_SLOTS = 1;
    private boolean hasOpenedPearlChest = false;
    public StashMover2() {
        super(Addon.CATEGORY, "StashMover2", "Automates moving items from stashes to your base");
    }

    @Override
    public void onActivate() {
//        state = State.IDLE_LOOTING;
        state = currState.get();
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("allowBreak false");
        currentPathGoal = null;
        usedEchest = false;
        beforePearlPos = null;
        beforeRespawnPos = null;
        pearlDelay = 20;
        portalDelay = 0;
        pearlPitchDelay = 0;
        chestOpenDelay = 0;
        chestOpenTimeout = 0;
        teleportDelay = 0;
        lootTimeout = 0;
        waitingForChestUpdate = false;
    }

    @Override
    public void onDeactivate() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        // Buttons
        WTable buttonTable = list.add(theme.table()).widget();

        WButton addDepositChunkBtn = buttonTable.add(theme.button("Add deposit chunk")).widget();
        addDepositChunkBtn.action = () -> {
            depositChests.addAll(getContainersInChunk(mc.player.getChunkPos()));
        };

        WButton addLootChunkBtn = buttonTable.add(theme.button("Add loot chunk")).widget();
        addLootChunkBtn.action = () -> {
            lootChests.addAll(getContainersInChunk(mc.player.getChunkPos()));
        };

        WButton clearDepositBtn = buttonTable.add(theme.button("Clear deposit chests")).widget();
        clearDepositBtn.action = () -> {
            depositChests.clear();
        };

        WButton clearLootBtn = buttonTable.add(theme.button("Clear loot chests")).widget();
        clearLootBtn.action = () -> {
            lootChests.clear();
        };

        WButton saveBtn = buttonTable.add(theme.button("Save data")).widget();
        saveBtn.action = this::save;

        WButton loadBtn = buttonTable.add(theme.button("Load data")).widget();
        loadBtn.action = this::load;

        return list;
    }

    private enum State
    {
        IDLE_LOOTING,
        IDLE_DEPOSITING,
        MOVING_TO_LOOT,
        LOOTING,
        MOVING_TO_DEPOSIT,
        DEPOSITING,
        MOVING_TO_LOOT_ENDER_CHEST,
        MOVING_TO_DEPOSIT_ENDER_CHEST,
        PEARLING,
        AWAITING_PEARL,
        GETTING_PEARL,
        THROWING_PEARL,
        PATHING_TO_PORTAL,
        REPLENISHING_ANCHOR,
        DYING
    }

    private void clickSlot(ScreenHandler handler, int slotId, int mouseButton, SlotActionType action) {
        mc.interactionManager.clickSlot(handler.syncId, slotId, mouseButton, action, mc.player);
    }

    private int findEmptyPlayerSlot(ScreenHandler handler) {
        // Player inventory slots are the LAST 36 in the handler (slot indices: handler.slots.size() - 36 .. handler.slots.size() - 1)
        for (int i = handler.slots.size() - 36; i < handler.slots.size(); i++) {
            if (!handler.getSlot(i).hasStack()) {
                return i;
            }
        }
        return -1; // None found
    }

    private State lastState = null;

    @EventHandler
    private void onInventory(InventoryEvent event)
    {
        waitingForChestUpdate = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event)
    {
        if (mc.player == null) return;
        if (state != lastState) {
            debugMsg("State changed to " + state);
            lastState = state;
        }

        if (portalDelay > 0)
        {
            portalDelay--;
            // avoid issues with things being null mid dimension switch
            return;
        }

        if (teleportDelay > 0)
        {
            teleportDelay--;
            return;
        }

        if (interactDelay > 0)
        {
            interactDelay--;
        }

        if (quickMoveTimeout > 0)
        {
            quickMoveTimeout--;
        }

        // go to nearest loot chest
        if (state == State.IDLE_LOOTING)
        {
            BlockPos nearestChest = findNearestContainer(lootChests);
            if (nearestChest == null)
            {
                int slots = Utils.emptyInvSlots(mc);
                // If there are still items to deposit but no more chests to fill the inventory with
                if (slots > 0)
                {
                    state = State.PEARLING;
                }
                else
                {
                    info("All lootable chests have been looted, disabling");
                    this.toggle();
                }
            }
            else
            {
                debugMsg("Moving to loot chest at " + nearestChest);
                state = State.MOVING_TO_LOOT;
                currentPathGoal = nearestChest;
                // POSSIBLE BUG: if baritone cannot find a path it may instantly disable
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(currentPathGoal, (int) Math.floor(chestInteractRange.get())));
            }
        }

        if (state == State.REPLENISHING_ANCHOR) {
            BlockPos anchorPos = findBlock(Blocks.RESPAWN_ANCHOR);
            if (anchorPos != null) {
                BlockState blockState = mc.world.getBlockState(anchorPos);
                int charges = blockState.get(RespawnAnchorBlock.CHARGES);
                if (charges < 2) {
                    info("Disabling to save spawn");
                    this.toggle();
                    return;
                }
                int chargesToFill = 4 - charges;
                if (chargesToFill > 0) {
                    int glowstoneCount = Utils.totalInvCount(mc, Items.GLOWSTONE);
                    info("Glowstone count: " + glowstoneCount);
                    if (glowstoneCount >= chargesToFill && interactDelay <= 0) {
                        // We have enough glowstone in our inventory—use them on the anchor.
                        for (int i = 0; i < chargesToFill; i++) {
                            interactWithBlock(anchorPos);
                        }
                        return;
                    } else if (glowstoneCount < chargesToFill) {
                        // Not enough glowstone in inventory, so try to extract from a shulker.
                        if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler) {
                            info("Shulker box handler open, searching for glowstone...");
                            boolean extracted = false;
                            // Loop over container slots (exclude the last 36 which are player inventory)
                            for (int i = 0; i < handler.slots.size() - 36; i++) {
                                Slot slot = handler.getSlot(i);
                                if (slot.hasStack() && slot.getStack().getItem().equals(Items.GLOWSTONE)) {
                                    info("Found glowstone in slot " + i + ". Extracting one.");
                                    moveOneItemFromSlotToHotbar1(handler, i);
                                    extracted = true;
                                    break;
                                }
                            }
                            if (extracted) {
                                interactDelay = chestInteractDelay.get();  // Set delay to avoid spamming
                                return;
                            } else {
                                info("No glowstone found in the open container.");
                                // Try to open the shulker if not already open.
                                if (!interactWithBlock(glowstonePos.get())) {
                                    info("Failed to interact with glowstone shulker at " + glowstonePos.get());
                                }
                            }
                        } else {
                            debugMsg("Shulker box not open; interacting with glowstone shulker at " + glowstonePos.get());
                            if (!interactWithBlock(glowstonePos.get())) {
                                info("Failed to interact with glowstone shulker at " + glowstonePos.get());
                            }
                        }
                    }
                } else {
                    // All charges are filled; move on.
                    state = State.IDLE_LOOTING;
                }
            } else {
                info("No respawn anchor found");
            }
        }

        // interact
        if (state == State.MOVING_TO_LOOT
            && !BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive()
            && interactDelay <= 0)
        {
            debugMsg("Within range of a loot chest, interacting");
            if (interactWithBlock(currentPathGoal))
            {
                state = State.LOOTING;
                waitingForChestUpdate = true;
                debugMsg("Interacted with container");
            }
            else
            {
                debugMsg("Interacting failed");
            }
        }

        // take items from chest
        if (state == State.LOOTING
            && mc.currentScreen instanceof GenericContainerScreen screen
            && !waitingForChestUpdate)
        {
            lootTimeout = 0;
            debugMsg("Looting chest");
            ScreenHandler handler = screen.getScreenHandler();
            int slotsLeftInChest = quickMoveSlots(handler, false);
            int emptySlotsInInventory = Utils.emptyInvSlots(mc);

            if (slotsLeftInChest == 0)
            {
                debugMsg("Finished looting chest at " + currentPathGoal);
                lootChests.remove(currentPathGoal);
            }
            if (emptySlotsInInventory <= EMPTY_INV_SLOTS)
            {
                // deposit to enderchest or move on
                if (usedEchest)
                {
                    // inventory and echest are filled, ready to pearl
                    state = State.PEARLING;
                    // reset echest for next use
                    usedEchest = false;
                }
                else
                {
                    // path to echest
                    state = State.MOVING_TO_LOOT_ENDER_CHEST;
                    waitingForChestUpdate = true;
                    BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.ENDER_CHEST);
                }
            }
            else
            {
                // reset state to find a new chest to loot until inventory is full
                state = State.IDLE_LOOTING;
            }
            mc.execute(() -> { mc.player.closeScreen(); mc.player.closeHandledScreen(); });
            return;
        }
        else if (state == State.LOOTING && waitingForChestUpdate)
        {
            lootTimeout++;
            if (lootTimeout > 30)
            {
                debugMsg("Timed out, interacting again");
                HashSet<BlockPos> setWithoutCurrent = new HashSet<>(lootChests);
                setWithoutCurrent.remove(currentPathGoal);
                currentPathGoal = findNearestContainer(setWithoutCurrent);
                state = State.MOVING_TO_LOOT;
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(currentPathGoal, (int) Math.floor(chestInteractRange.get())));
                lootTimeout = 0;
                return;
            }
        }


        // fill up enderchest
        if (state == State.MOVING_TO_LOOT_ENDER_CHEST
            && !BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().isActive()
            && mc.currentScreen instanceof GenericContainerScreen screen
            && !waitingForChestUpdate
            )
        {
            ScreenHandler handler = screen.getScreenHandler();
            int filledSlots = 0;
            for (int i = 0; i < handler.slots.size() - 36; i++) {
                Slot slot = handler.getSlot(i);
                if (slot.hasStack()) {
                    filledSlots++;
                }
            }
            debugMsg("filled slots: " + filledSlots);
            if (filledSlots == 27 && usedEchest) {
                state = State.IDLE_LOOTING;
                mc.player.closeHandledScreen();
                mc.player.closeScreen();
                return;
            }
            else if (quickMoveTimeout <= 0)
            {
                quickMoveTimeout = 30;
                info("quick moving");
                quickMoveSlots(handler, true);
                usedEchest = true;
            }

        }

        // send pearl message
        if (state == State.PEARLING)
        {
            // prior to 1.21.4 update, make sure the player is in the overworld before pearling
            if (fromNether.get() && mc.player.getWorld().getRegistryKey() == World.NETHER)
            {
                state = State.PATHING_TO_PORTAL;
                BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.NETHER_PORTAL);
                return;
            }
            ChatUtils.sendPlayerMsg("/msg " + pearlAccountName.get() + " " + pearlPhrase.get() + " " + generateRandomCharacters(25));
            state = State.AWAITING_PEARL;
            freeHotbar1();
            beforePearlPos = mc.player.getBlockPos();
            mc.execute(() -> { mc.player.closeScreen(); mc.player.closeHandledScreen(); });
            return;
        }

        if (state == State.PATHING_TO_PORTAL
            && !BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().isActive())
        {
            state = State.PEARLING;
            portalDelay = 100;
        }

        if (state == State.GETTING_PEARL)
        {
            if (mc.player.getInventory().getStack(0).getItem().equals(Items.ENDER_PEARL))
            {
                state = State.THROWING_PEARL;
                pearlPitchDelay = pearlPitchingDelay.get();
                hasOpenedPearlChest = false;
            }
            else if (hasOpenedPearlChest && !waitingForChestUpdate)
            {
                ScreenHandler handler = mc.player.currentScreenHandler;
                for (int i = 0; i < handler.slots.size() - 36; i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot.hasStack() && slot.getStack().getItem().equals(Items.ENDER_PEARL)) {
                        moveOneItemFromSlotToHotbar1(handler, i);

                        mc.execute(() -> { mc.player.closeScreen(); mc.player.closeHandledScreen(); });
                        return;
                    }
                }
            }
            else if (!hasOpenedPearlChest)
            {

                if (interactWithBlock(pearlContainer.get()))
                {
                    hasOpenedPearlChest = true; // so we don't open it again
                    waitingForChestUpdate = true;
                }
                else
                {
                    debugMsg("Failed to open pearl chest");
                }
            }
        }

        if (state == State.THROWING_PEARL)
        {
            debugMsg("pearl pitch delay: " + pearlPitchDelay);
            // TODO: Add setting for the trapdoor type
            BlockPos trapdoorPos = findBlock(Blocks.OAK_TRAPDOOR);
            // wait until the trapdoor is found
            if (trapdoorPos == null)
            {
                debugMsg("No trapdoor found");
                return;
            }
            BlockState trapdoor = mc.player.getWorld().getBlockState(trapdoorPos);
            // wait for the trapdoor to be open before pearling to avoid hitting it
            if (!trapdoor.get(TrapdoorBlock.OPEN)) return;
            // assume we are already in the pearl water and have a pearl in hotbar slot 1
            mc.player.getInventory().selectedSlot = 0;
            mc.player.setPitch((float)Rotations.getPitch(soulSandPos.get()));
            debugMsg("aiming at pitch of " + Rotations.getPitch(soulSandPos.get()));
            mc.player.setYaw((float) Rotations.getYaw(soulSandPos.get()));
            mc.player.closeScreen(); mc.player.closeHandledScreen();
            if (pearlPitchDelay > 0)
            {
                pearlPitchDelay--;
            }
            else {
                HitResult hitResult = mc.getCameraEntity().raycast(1, 0, false);
                if (hitResult == null || hitResult.getType().equals(HitResult.Type.BLOCK)) return;
                mc.execute(() -> {
                    if (Objects.equals(getBlockTargetted(100.0), soulSandPos.get()))
                    {
                        debugMsg("Throwing pearl");
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        state = State.IDLE_DEPOSITING;
                        pearlDelay = afterPearlDelay.get();
                        mc.player.setSneaking(false);
                        setPressed(mc.options.forwardKey, false);
                    }
                    else
                    {
//                        info("target block is at " + getBlockTargetted(100.0));
                        mc.player.setSneaking(true);
                        setPressed(mc.options.forwardKey, true);
                    }
                });
            }
        }

        if (pearlDelay > 0)
        {
            pearlDelay--;
        }

        if (state == State.IDLE_DEPOSITING && pearlDelay <= 0)
        {
            BlockPos nearestChest = findNearestContainer(depositChests);
            if (nearestChest == null)
            {
                info("All deposit chests have been filled, disabling");
                this.toggle();
            }
            else
            {
                debugMsg("Moving to deposit chest at " + nearestChest);
                state = State.MOVING_TO_DEPOSIT;
                currentPathGoal = nearestChest;
                // POSSIBLE BUG: if baritone cannot find a path it may instantly disable
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(currentPathGoal, (int) Math.floor(chestInteractRange.get())));
            }
        }

        if (state == State.MOVING_TO_DEPOSIT
            && !BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive()
            && interactDelay <= 0)
        {
            debugMsg("Within range of a deposit chest, interacting");
            if (interactWithBlock(currentPathGoal))
            {
                state = State.DEPOSITING;
                debugMsg("Interacted with container");
            }
            else
            {
                debugMsg("Interacting failed");
            }
        }

        if (chestOpenDelay > 0)
        {
            chestOpenDelay--;
        }

        if (state == State.DEPOSITING && mc.currentScreen instanceof GenericContainerScreen screen)
        {
            if (chestOpenDelay == 0)
            {
                ScreenHandler handler = screen.getScreenHandler();
                quickMoveSlots(handler, true);
                chestOpenDelay = 20;
                return;
            }
            else if (chestOpenDelay == 1)
            {
                boolean moreToDeposit = Utils.emptyInvSlots(mc) < 36;
                int emptySlotsInInventory = Utils.emptyInvSlots(mc);


                if (moreToDeposit)
                {
                    debugMsg("Finished depositing to chest at " + currentPathGoal);
                    depositChests.remove(currentPathGoal);
                    state = State.IDLE_DEPOSITING;
                }
                else
                {
                    // if the inventory is empty and the echest has already been depleted
                    if (usedEchest)
                    {
                    ChatUtils.sendPlayerMsg("/kill");
//                        info("Would be killing");
//                        this.toggle();
                        beforeRespawnPos = mc.player.getBlockPos();
                        usedEchest = false;
                    }
                    else
                    {
                        state = State.MOVING_TO_DEPOSIT_ENDER_CHEST;
                        waitingForChestUpdate = true;
                        BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.ENDER_CHEST);
                    }
                }
                mc.execute(() -> { mc.player.closeScreen(); mc.player.closeHandledScreen(); });
                return;
            }
        }
        else if (state == State.DEPOSITING)
        {
            chestOpenTimeout++;
            if (chestOpenTimeout > 100)
            {
                debugMsg("Chest interact timed out, retrying...");
                chestOpenTimeout = 0;
                state = State.IDLE_DEPOSITING;
            }
        }

        if (state == State.MOVING_TO_DEPOSIT_ENDER_CHEST
            && !BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().isActive()
            && mc.currentScreen instanceof GenericContainerScreen screen
            && !waitingForChestUpdate)
        {
            ScreenHandler handler = screen.getScreenHandler();
            int filledSlots = 0;
            for (int i = 0; i < handler.slots.size() - 36; i++) {
                Slot slot = handler.getSlot(i);
                if (slot.hasStack()) {
                    filledSlots++;
                }
            }
            if (filledSlots == 0 && usedEchest) {
                state = State.IDLE_DEPOSITING;
                mc.player.closeHandledScreen();
                mc.player.closeScreen();
                return;
            }
            else if (quickMoveTimeout <= 0)
            {
                quickMoveTimeout = 30;
                info("quick moving");
                quickMoveSlots(handler, false);
                usedEchest = true;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onOpenScreenEvent(OpenScreenEvent event) {
        if (!(event.screen instanceof DeathScreen)) return;
        state = State.DYING;
        mc.player.requestRespawn();
        event.cancel();
    }

    // TODO: redo this cause it only moves 1 slot
    private void moveXItemsToSlot(ScreenHandler handler, int from, int count, int to)
    {
        clickSlot(handler, from, 0, SlotActionType.PICKUP);
        for (int i = 0; i < count; i++)
        {
            clickSlot(handler, to, 1, SlotActionType.PICKUP);
        }

        clickSlot(handler, from, 0, SlotActionType.PICKUP);
    }

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateRandomCharacters(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private BlockPos findBlock(Block blockToFind) {
        if (mc.player == null || mc.world == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        int range = 5;

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (block.equals(blockToFind)) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (state == State.AWAITING_PEARL && beforePearlPos != null)
        {
            if (Math.sqrt(mc.player.squaredDistanceTo(Vec3d.of(beforePearlPos))) > teleportChunkDetectDistance.get() * 16)
            {
                state = State.GETTING_PEARL;
                beforePearlPos = null;
                usedEchest = false;
                teleportDelay = 20;
            }
            else
            {
                beforePearlPos = mc.player.getBlockPos();
            }
        }

        if (state == State.DYING && beforeRespawnPos != null)
        {
            if (Math.sqrt(mc.player.squaredDistanceTo(Vec3d.of(beforeRespawnPos))) > teleportChunkDetectDistance.get() * 16)
            {
                freeHotbar1();
                if (fromNether.get())
                {
                    state = State.REPLENISHING_ANCHOR;
                }
                else
                {
                    state = State.IDLE_LOOTING;
                }
                beforeRespawnPos = null;
                usedEchest = false;
                teleportDelay = 20;
            }
            else
            {
                beforeRespawnPos = mc.player.getBlockPos();
            }
        }
    }

    private int getEmptySlot() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.AIR) {
                return i;
            }
        }
        return -1;
    }

    // assumes there is 1 empty slot in the inventory somewhere
    private void freeHotbar1()
    {
        if (mc.player.getInventory().getStack(0).getItem() != Items.AIR)
        {
            // shift click hotbar 1 slot to make it empty
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                36,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
            );
        }
//        mc.player.closeHandledScreen();
//        mc.player.closeScreen();
    }

    /** Moves all slots within the specified inventory by quick moving them into the
     * inventory.
     * @param handler The screen handler of the container
     * @param playerInventory If this is true the player inventory will be moved, otherwise the container will be moved.
     * @return The amount of slots left in the other container.
     */
    private int quickMoveSlots(ScreenHandler handler, boolean playerInventory) {
        int emptySlotsInInventory = Utils.emptyInvSlots(mc);
        int slotsLeft = 0;

        if (playerInventory) {
            // SHIFT-click items from player inventory -> container
            for (int i = handler.slots.size() - 36; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                if (slot.hasStack()) {
                    clickSlot(handler, slot.id);
                }
            }
        }
        else {
            // SHIFT-click items from container -> player inventory
            for (int i = 0; i < handler.slots.size() - 36; i++) {
                Slot slot = handler.getSlot(i);
                if (slot.hasStack()) {
                    if (onlyMoveShulkers.get() && !(slot.getStack().getItem() instanceof BlockItem)) continue;
                    else if (onlyMoveShulkers.get() && !(((BlockItem)slot.getStack().getItem()).getBlock() instanceof ShulkerBoxBlock)) continue;
                    if (emptySlotsInInventory > EMPTY_INV_SLOTS) {
                        clickSlot(handler, slot.id);
                        emptySlotsInInventory--;
                    }
                    else {
                        slotsLeft++;
                    }
                }
            }
        }
        return slotsLeft;
    }

    /**
     * Attempts to extract exactly one glowstone from the given slot in the open container.
     * It does so by simulating a split-stack action:
     *   1. Left-click the source slot to pick up the entire stack.
     *   2. Right-click an empty slot in the player's inventory to deposit one item.
     *   3. Left-click the source slot again to return the remainder.
     * Then it closes the container.
     *
     * @param handler   The open container's ScreenHandler.
     * @param slotIndex The index of the slot that contains the glowstone stack.
     */
    private void moveOneItemFromSlotToHotbar1(ScreenHandler handler, int slotIndex) {
        // 1) Pick up the entire stack from the source slot.
        mc.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
        // 2) Find an empty slot in the player's inventory (the last 36 slots).
//        int emptySlot = findEmptyPlayerSlot(handler);
//        if (emptySlot != -1) {
            // 3) Right-click the empty slot to deposit exactly one item.
            mc.interactionManager.clickSlot(handler.syncId, 54, 1, SlotActionType.PICKUP, mc.player);
//            info("Deposited one item into slot " + emptySlot);
//        } else {
//            info("No empty slot found in player inventory for splitting stack.");
//        }
        // 4) Left-click the source slot to return the remainder.
        mc.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
    }


    private void clickSlot(ScreenHandler handler, int slotId) {
        mc.interactionManager.clickSlot(
            handler.syncId,
            slotId,
            0,
            SlotActionType.QUICK_MOVE,
            mc.player
        );
    }

    private boolean interactWithBlock(BlockPos pos)
    {
        Vec3d vec = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        BlockHitResult hitResult = new BlockHitResult(vec, Direction.UP, pos, false);
        float yaw = (float)Rotations.getYaw(pos);
        float pitch = (float)Rotations.getPitch(pos);
//        if (!Objects.equals(getChestOrShulkerLookingAt(chestInteractRange.get()), pos)) {
        if (!isBlockInLineOfVision(pos, debugTemp.get())) {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
            debugMsg("set yaw and pitch");
            debugMsg("Wanted " + pos);
            return false;
        }
        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult) == ActionResult.SUCCESS) {
            debugMsg("interaction succeeded");
            mc.player.swingHand(Hand.MAIN_HAND);
            interactDelay = chestInteractDelay.get();
            return true;
        }
        return false;
    }

    // chatgpt ahh code
    public BlockPos getBlockTargetted(double maxDistance) {
        maxDistance += debugTemp.get(); // issues with eye/feet calculations
        Vec3d eyePos = mc.player.getCameraPosVec(1.0F); // Get player's eye position
        Vec3d lookVec = mc.player.getRotationVec(1.0F); // Get the direction the player is looking

        Vec3d targetPos = eyePos.add(lookVec.multiply(maxDistance)); // Extend the vector

        World world = mc.player.getWorld();

        BlockHitResult hitResult = world.raycast(new net.minecraft.world.RaycastContext(
            eyePos, targetPos,
            net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            debugMsg("Looking at block " + hitResult.getBlockPos());
            BlockState blockState = world.getBlockState(hitResult.getBlockPos());
            Block block = blockState.getBlock();
            return hitResult.getBlockPos();
        }

        return null; // No block hit
    }

    public boolean isBlockInLineOfVision(BlockPos blockPos, double maxDistance) {
        double fovThreshold = debugTemp2.get();
        // 1. Get the player's eye position and look direction
        Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
        Vec3d lookVec = mc.player.getRotationVec(1.0F).normalize(); // Direction the player is looking

        // 2. Get the direction vector from the player's eyes to the block
        Vec3d blockVec = new Vec3d(
            blockPos.getX() + 0.5 - eyePos.x, // Center of block
            blockPos.getY() + 0.5 - eyePos.y,
            blockPos.getZ() + 0.5 - eyePos.z
        ).normalize();

        // 3. Compute the dot product (cosine of the angle)
        double dotProduct = lookVec.dotProduct(blockVec);

        // 4. Check if the block is within range and within the FOV threshold
        return dotProduct >= fovThreshold && eyePos.squaredDistanceTo(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= (maxDistance * maxDistance);
    }

    private void debugMsg(String msg)
    {
        if (debug.get())
        {
            info(msg);
        }
    }

    private BlockPos findNearestContainer(HashSet<BlockPos> containers)
    {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos container : containers)
        {
            double distance = mc.player.getPos().distanceTo(Vec3d.of(container));
            if (distance < nearestDistance)
            {
                nearest = container;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private HashSet<BlockPos> getContainersInChunk(ChunkPos pos)
    {
        HashSet<BlockPos> containers = new HashSet<>();
        if (mc.player == null) return containers;
        World world = mc.player.getWorld();
        BlockPos startingPos = pos.getStartPos();
        int yMin = (int) (mc.player.getY() - yRangeForChestDetection.get());
        int yMax = (int) (mc.player.getY() + yRangeForChestDetection.get());
        for (int x = 0; x < 16; x++)
        {
            for (int y = yMin; y < yMax; y++)
            {
                for (int z = 0; z < 16; z++)
                {
                    BlockPos blockPos = startingPos.add(x, y, z);
                    BlockState blockState = world.getBlockState(blockPos);
                    Block block = blockState.getBlock();
                    // TODO: Add to setting
                    if (block instanceof ChestBlock || block instanceof TrappedChestBlock)
                    {
                        ChestType chestType = blockState.get(ChestBlock.CHEST_TYPE);
                        if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT)
                        {
                            Direction facing = blockState.get(ChestBlock.FACING);
                            BlockPos leftChestPart = chestType == ChestType.LEFT ? blockPos : blockPos.offset(facing.rotateYCounterclockwise());

                            containers.add(leftChestPart);
                        }
                        else
                        {
                            containers.add(blockPos);
                        }
                    }
                }
            }
        }
        info("Found " + containers.size() + " containers in chunk " + pos);
        return containers;
    }

    private void load()
    {
        File file = getFile();
        if (!file.exists()) return;
        try
        {
            FileReader reader = new FileReader(file);
            SaveData data = GSON.fromJson(reader, SaveData.class);
            depositChests = data.depositChests;
            lootChests = data.lootChests;
            info("Successfully loaded " + depositChests.size() + " deposit chests and " + lootChests.size() + " loot chests.");
            reader.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void save()
    {
        try
        {
            File file = getFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);
            SaveData data = new SaveData();
            data.depositChests = depositChests;
            data.lootChests = lootChests;
            GSON.toJson(data, writer);
            writer.flush();
            writer.close();
            info("Sucessfully Saved " + depositChests.size() + " deposit chests and " + lootChests.size() + " loot chests.");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private File getFile()
    {
        return new File(new File(MeteorClient.FOLDER, "stash-mover"), "save.json");
    }

    private class SaveData
    {
        public HashSet<BlockPos> depositChests;
        public HashSet<BlockPos> lootChests;
    }

}
