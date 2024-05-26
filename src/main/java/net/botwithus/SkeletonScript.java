package net.botwithus;

import net.botwithus.internal.scripts.ScriptDefinition;
import net.botwithus.rs3.game.Client;
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer;
import net.botwithus.rs3.script.LoopingScript;
import net.botwithus.rs3.script.config.ScriptConfig;
import net.botwithus.rs3.script.Execution;
import net.botwithus.rs3.game.Coordinate;
import net.botwithus.rs3.game.movement.Movement;
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery;
import net.botwithus.rs3.game.queries.builders.characters.NpcQuery;
import net.botwithus.rs3.game.scene.entities.object.SceneObject;
import net.botwithus.rs3.game.scene.entities.characters.npc.Npc;
import net.botwithus.api.game.hud.Dialog;
import net.botwithus.rs3.input.KeyboardInput;
import net.botwithus.rs3.game.hud.interfaces.Component;
import net.botwithus.rs3.game.hud.interfaces.Interfaces;
import net.botwithus.api.game.hud.inventories.Backpack;
import net.botwithus.api.game.hud.inventories.Bank;

import java.awt.event.KeyEvent;
import java.util.Random;

public class SkeletonScript extends LoopingScript {

    private BotState botState = BotState.NOT_LOGGED_IN;
    private boolean someBool = true;
    private int beersBought = 0;
    private boolean navigating = false;

    enum BotState {
        NOT_LOGGED_IN,
        BACKPACK_FULL,
        BARTENDER_NEARBY,
        NAVIGATE_TO_SHOP,
        INTERACT_WITH_BARTENDER,
        NAVIGATE_TO_BANK,
        HANDLE_DIALOG,
        CHECK_DOORS
    }

    private enum DialogState {
        START,
        FIRST_SPACE_PRESS,
        FIRST_CHOICE,
        SECOND_SPACE_PRESS,
        FINAL_SPACE_PRESS,
        COMPLETE
    }

    private Random random = new Random();
    private Coordinate shopCoordinate = new Coordinate(3215, 3395, 0);
    private Coordinate bankLocation = new Coordinate(3186, 3433, 0);
    private DialogState dialogState = DialogState.START;

    private long stateStartTime;
    private static final long TIMEOUT_DURATION = 15000;  // 15 seconds
    private SkeletonScriptGraphicsContext sgc;

    public SkeletonScript(String s, ScriptConfig scriptConfig, ScriptDefinition scriptDefinition) {
        super(s, scriptConfig, scriptDefinition);
        this.sgc = new SkeletonScriptGraphicsContext(getConsole(), this);
    }

    @Override
    public void onLoop() {
        LocalPlayer player = Client.getLocalPlayer();

        if (System.currentTimeMillis() - stateStartTime > TIMEOUT_DURATION) {
            handleTimeout();
        }

        switch (botState) {
            case NOT_LOGGED_IN:
                handleNotLoggedIn();
                break;
            case BACKPACK_FULL:
                interactWithBankBooth();
                break;
            case BARTENDER_NEARBY:
                if (!Backpack.isFull()) {
                    interactWithNearestBartender();
                } else {
                    botState = BotState.NAVIGATE_TO_BANK;
                }
                break;
            case NAVIGATE_TO_SHOP:
                if (shopCoordinate.distanceTo(player.getCoordinate()) < 10) {
                    println("Reached the shop.");
                    botState = BotState.INTERACT_WITH_BARTENDER;
                    navigating = false;
                    stateStartTime = System.currentTimeMillis();
                } else {
                    navigateTo(shopCoordinate, BotState.NAVIGATE_TO_SHOP);
                }
                break;
            case INTERACT_WITH_BARTENDER:
                if (!Backpack.isFull()) {
                    interactWithNearestBartender();
                } else {
                    botState = BotState.NAVIGATE_TO_BANK;
                }
                break;
            case NAVIGATE_TO_BANK:
                navigateTo(bankLocation, BotState.BACKPACK_FULL);
                break;
            case HANDLE_DIALOG:
                handleBartenderDialog();
                break;
            case CHECK_DOORS:
                checkAndHandleDoors();
                break;
            default:
                break;
        }

        // Check if navigating and update state if destination reached
        if (navigating) {
            if ((botState == BotState.NAVIGATE_TO_SHOP && shopCoordinate.distanceTo(player.getCoordinate()) < 10) ||
                (botState == BotState.NAVIGATE_TO_BANK && bankLocation.distanceTo(player.getCoordinate()) < 10)) {
                println("Reached the destination.");
                navigating = false;
                stateStartTime = System.currentTimeMillis();
                if (botState == BotState.NAVIGATE_TO_SHOP) {
                    botState = BotState.INTERACT_WITH_BARTENDER;
                } else if (botState == BotState.NAVIGATE_TO_BANK) {
                    botState = BotState.BACKPACK_FULL;
                }
            }
        }
    }


    private BotState determineState(LocalPlayer player) {
        Client.GameState gameState = Client.getGameState();
        if (player == null || gameState != Client.GameState.LOGGED_IN) {
            return BotState.NOT_LOGGED_IN;
        } else if (Backpack.isFull()) {
            return BotState.BACKPACK_FULL;
        } else if (Dialog.isOpen()) {
            return BotState.HANDLE_DIALOG;
        } else {
            Npc bartender = NpcQuery.newQuery().name("Bartender").results().nearest();
            if (bartender != null && bartender.getCoordinate().distanceTo(player.getCoordinate()) < 10) {
                return BotState.BARTENDER_NEARBY;
            } else if (shopCoordinate.distanceTo(player.getCoordinate()) >= 8) {
                return BotState.NAVIGATE_TO_SHOP;
            } else {
                return BotState.INTERACT_WITH_BARTENDER;
            }
        }
    }

    private void handleTimeout() {
        println("Timeout reached for state: " + botState + ". Resetting state.");
        botState = Backpack.isFull() ? BotState.NAVIGATE_TO_BANK : BotState.NAVIGATE_TO_SHOP;
        stateStartTime = System.currentTimeMillis();
    }

    private void handleNotLoggedIn() {
        Execution.delay(random.nextLong(3000, 7000));
    }

    private void navigateTo(Coordinate destination, BotState nextState) {
        if (!navigating) {
            int randomX = random.nextInt(11) - 5;
            int randomY = random.nextInt(11) - 5;

            Coordinate randomizedDestination = new Coordinate(
                destination.getX() + randomX, 
                destination.getY() + randomY, 
                destination.getZ()
            );

            println("Navigating to: " + randomizedDestination);
            Movement.walkTo(randomizedDestination.getX(), randomizedDestination.getY(), false);
            stateStartTime = System.currentTimeMillis();
            navigating = true;
            Execution.delay(random.nextLong(10000, 12000));
            botState = nextState;
        } else {
            LocalPlayer player = Client.getLocalPlayer();
            if (destination.distanceTo(player.getCoordinate()) < 10) {
                println("Reached destination: " + destination);
                navigating = false;
                stateStartTime = System.currentTimeMillis();
                botState = nextState;
            } else if (System.currentTimeMillis() - stateStartTime > TIMEOUT_DURATION) {
                println("Timeout reached while navigating. Resetting navigation.");
                navigating = false;
                stateStartTime = System.currentTimeMillis();
            }
        }
    }

    private void navigateToBank() {
        navigateTo(bankLocation, BotState.NAVIGATE_TO_BANK);
        LocalPlayer player = Client.getLocalPlayer();
        
        if (bankLocation.distanceTo(player.getCoordinate()) < 10) {
            println("Reached the bank. Loading last preset.");
            Bank.loadLastPreset();
            Execution.delay(random.nextLong(5000, 8000));
            
            if (!Backpack.isFull()) {
                println("Preset loaded successfully. Backpack is not full.");
                botState = BotState.NAVIGATE_TO_SHOP;
            } else {
                println("Failed to load preset or backpack is still full.");
            }
            
            stateStartTime = System.currentTimeMillis();
        } else {
            println("Failed to reach the bank. Retrying.");
            Execution.delay(random.nextLong(1500, 3000));
        }
    }

    private void interactWithNearestBartender() {
        if (!Dialog.isOpen()) {
            Npc bartender = NpcQuery.newQuery().name("Bartender").results().nearest();
            if (bartender != null) {
                println("Interacting with Bartender.");
                bartender.interact("Talk-to");
                stateStartTime = System.currentTimeMillis();

                while (System.currentTimeMillis() - stateStartTime < TIMEOUT_DURATION && !Dialog.isOpen()) {
                    Execution.delay(100);
                }

                if (Dialog.isOpen()) {
                    botState = BotState.HANDLE_DIALOG;
                } else {
                    println("Failed to open dialog with Bartender. Reattempting interaction.");
                    Execution.delay(random.nextLong(600, 1000));
                }
            } else {
                println("No Bartender found nearby.");
                Execution.delay(random.nextLong(600, 1000));
            }
        } else {
            botState = BotState.HANDLE_DIALOG;
        }
    }


    private void checkAndHandleDoors() {
        SceneObject door = SceneObjectQuery.newQuery()
            .name("Door")
            .option("Open", "Close")
            .results()
            .nearest();

        if (door != null) {
            if (door.getOptions().contains("Open")) {
                println("Opening closed door.");
                door.interact("Open");
                Execution.delay(random.nextLong(1000, 2000));
            } else if (door.getOptions().contains("Close")) {
                println("Door is already open.");
            }
        } else {
            println("No door found.");
        }
    }

    private void handleBartenderDialog() {
        while (Dialog.isOpen()) {
            println("Current Dialog State: " + dialogState); // Print the current dialog state for tracking
            switch (dialogState) {
                case START:
                    println("Dialog State: START");
                    if (Interfaces.isOpen(1184)) {
                        pressKeyAndAdvance(KeyEvent.VK_SPACE, DialogState.FIRST_SPACE_PRESS);
                    }
                    break;
                case FIRST_SPACE_PRESS:
                    println("Dialog State: FIRST_SPACE_PRESS");
                    if (Interfaces.isOpen(1188)) {
                        pressKeyAndAdvance(KeyEvent.VK_1, DialogState.FIRST_CHOICE);
                    }
                    break;
                case FIRST_CHOICE:
                    println("Dialog State: FIRST_CHOICE");
                    if (Interfaces.isOpen(1191)) {
                        pressKeyAndAdvance(KeyEvent.VK_SPACE, DialogState.SECOND_SPACE_PRESS);
                    }
                    break;
                case SECOND_SPACE_PRESS:
                    println("Dialog State: SECOND_SPACE_PRESS");
                    if (Interfaces.isOpen(1184)) {
                        pressKeyAndAdvance(KeyEvent.VK_SPACE, DialogState.FINAL_SPACE_PRESS);
                    }
                    break;
                case FINAL_SPACE_PRESS:
                    println("Dialog State: FINAL_SPACE_PRESS");
                    if (Interfaces.isOpen(1191)) {
                        pressKeyAndAdvance(KeyEvent.VK_SPACE, DialogState.COMPLETE);
                        println("Dialog interaction complete.");
                        beersBought++;
                        println("Beers bought: " + beersBought);
                        dialogState = DialogState.START;
                        if (!Backpack.isFull()) {
                            botState = BotState.INTERACT_WITH_BARTENDER;
                        } else {
                            botState = BotState.NAVIGATE_TO_BANK;
                        }
                        stateStartTime = System.currentTimeMillis();
                        return;
                    }
                    break;
            }
            Execution.delay(100);
        }
        println("Dialog not open currently. Reattempting interaction.");
        dialogState = DialogState.START;
        botState = BotState.BARTENDER_NEARBY;
        stateStartTime = System.currentTimeMillis();
    }

    
    private void interactWithBankBooth() {
        SceneObject bankBooth = SceneObjectQuery.newQuery().name("Bank booth").results().nearest();
        LocalPlayer player = Client.getLocalPlayer();

        if (bankBooth != null && bankBooth.getCoordinate().distanceTo(player.getCoordinate()) < 10) {
            println("Interacting with bank booth.");
            Bank.loadLastPreset();
            Execution.delay(random.nextLong(3000,5000));
            if (!Backpack.isFull()) {
                println("Preset loaded successfully. Backpack is not full.");
                botState = BotState.NAVIGATE_TO_SHOP;
            } else {
                println("Failed to load preset or backpack is still full.");
            }
            navigating = false;
            stateStartTime = System.currentTimeMillis();
        } else {
            println("Bank booth not found or not within reach.");
            navigateTo(bankLocation, BotState.NAVIGATE_TO_BANK);
        }
    }



    private void pressSpaceAndAdvance(DialogState nextState) {
        KeyboardInput.pressKey(KeyEvent.VK_SPACE);
        Execution.delay(random.nextInt(700, 1000));
        dialogState = nextState;
    }

    private void pressKeyAndAdvance(int key, DialogState nextState) {
        KeyboardInput.pressKey(key);
        Execution.delay(random.nextInt(700, 1000));
        dialogState = nextState;
    }

    @Override
    public SkeletonScriptGraphicsContext getGraphicsContext() {
        return sgc;
    }

    public BotState getBotState() {
        return botState;
    }

    public void setBotState(BotState botState) {
        this.botState = botState;
    }

    public boolean isSomeBool() {
        return someBool;
    }

    public void setSomeBool(boolean someBool) {
        this.someBool = someBool;
    }
    
    public int getBeersBought() {
        return beersBought;
    }
}
