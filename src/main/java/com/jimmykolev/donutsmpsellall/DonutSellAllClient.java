package com.jimmykolev.donutsmpsellall;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class DonutSellAllClient implements ClientModInitializer {
    private static final int DEFAULT_DELAY_TICKS = 10;
    private static final int MIN_DELAY_TICKS = 5;
    private static final int MAX_DELAY_TICKS = 200;
    private static final int REMOVAL_TIMEOUT_TICKS = 100;

    private static boolean active;
    private static boolean waitingForRemoval;
    private static int cooldown;
    private static int removalWait;
    private static int delayTicks;
    private static int listed;
    private static int originalHotbarSlot;
    private static long price;
    private static ItemStack target = ItemStack.EMPTY;
    private static String targetName = "";

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("sellall")
                .then(argument("price", LongArgumentType.longArg(1))
                    .executes(context -> start(
                        LongArgumentType.getLong(context, "price"),
                        DEFAULT_DELAY_TICKS
                    ))
                    .then(argument("delayTicks", IntegerArgumentType.integer(MIN_DELAY_TICKS, MAX_DELAY_TICKS))
                        .executes(context -> start(
                            LongArgumentType.getLong(context, "price"),
                            IntegerArgumentType.getInteger(context, "delayTicks")
                        ))
                    )
                )
            );

            dispatcher.register(literal("sellallcancel")
                .executes(context -> {
                    if (active) {
                        finish("Cancelled after listing " + listed + " item(s).", Formatting.YELLOW);
                    } else {
                        message("No Sell All job is running.", Formatting.GRAY);
                    }
                    return 1;
                })
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(DonutSellAllClient::tick);
    }

    private static int start(long requestedPrice, int requestedDelay) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            message("Join a server before using /sellall.", Formatting.RED);
            return 0;
        }

        if (active) {
            message("A Sell All job is already running. Use /sellallcancel first.", Formatting.RED);
            return 0;
        }

        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty()) {
            message("Hold the item you want to sell, then run /sellall <price>.", Formatting.RED);
            return 0;
        }

        if (held.getCount() != 1 || held.getMaxCount() != 1) {
            message("Safety stop: this version only lists unstackable items individually.", Formatting.RED);
            return 0;
        }

        price = requestedPrice;
        delayTicks = requestedDelay;
        target = held.copyWithCount(1);
        targetName = held.getName().getString();
        originalHotbarSlot = client.player.getInventory().selectedSlot;
        listed = 0;
        cooldown = 0;
        removalWait = 0;
        waitingForRemoval = false;
        active = true;

        int count = countMatching(client);
        message(
            "Selling " + count + " " + targetName + " item(s) at $" + price
                + " each. Delay: " + delayTicks + " ticks. Use /sellallcancel to stop.",
            Formatting.GREEN
        );
        return 1;
    }

    private static void tick(MinecraftClient client) {
        if (!active) {
            return;
        }

        if (client.player == null || client.interactionManager == null || client.getNetworkHandler() == null) {
            finish("Stopped because the connection closed.", Formatting.RED);
            return;
        }

        // Avoid inventory movement while any menu, chat box, or confirmation screen is open.
        if (client.currentScreen != null) {
            return;
        }

        ItemStack held = client.player.getMainHandStack();

        if (waitingForRemoval) {
            if (!matches(held)) {
                waitingForRemoval = false;
                removalWait = 0;
                listed++;
                cooldown = delayTicks;
            } else if (++removalWait > REMOVAL_TIMEOUT_TICKS) {
                finish(
                    "Stopped because the auction did not remove the item. Enable DonutSMP Fast Sell and try again.",
                    Formatting.RED
                );
            }
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (matches(held)) {
            if (held.getCount() != 1) {
                finish("Safety stop: the held slot contains more than one item.", Formatting.RED);
                return;
            }

            client.getNetworkHandler().sendChatCommand("ah sell " + price);
            waitingForRemoval = true;
            removalWait = 0;
            return;
        }

        int matchingSlot = findMatchingSlot(client);
        if (matchingSlot == -1) {
            finish("Finished. Listed " + listed + " item(s) at $" + price + " each.", Formatting.GREEN);
            return;
        }

        if (matchingSlot < 9) {
            client.player.getInventory().selectedSlot = matchingSlot;
            cooldown = 2;
            return;
        }

        int selected = client.player.getInventory().selectedSlot;
        client.interactionManager.clickSlot(
            client.player.currentScreenHandler.syncId,
            matchingSlot,
            selected,
            SlotActionType.SWAP,
            client.player
        );
        cooldown = 2;
    }

    private static int findMatchingSlot(MinecraftClient client) {
        int selected = client.player.getInventory().selectedSlot;

        for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && matches(client.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        for (int slot = 9; slot < 36; slot++) {
            if (matches(client.player.getInventory().getStack(slot))) {
                // PlayerScreenHandler uses the same slot numbers for inventory slots 9-35.
                return slot;
            }
        }

        return -1;
    }

    private static int countMatching(MinecraftClient client) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, target);
    }

    private static void finish(String text, Formatting colour) {
        MinecraftClient client = MinecraftClient.getInstance();
        active = false;
        waitingForRemoval = false;
        target = ItemStack.EMPTY;

        if (client.player != null && originalHotbarSlot >= 0 && originalHotbarSlot < 9) {
            client.player.getInventory().selectedSlot = originalHotbarSlot;
        }

        message(text, colour);
    }

    private static void message(String text, Formatting colour) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Sell All] ").formatted(Formatting.GOLD)
                .append(Text.literal(text).formatted(colour)), false);
        }
    }
}
