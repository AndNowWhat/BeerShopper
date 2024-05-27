package net.botwithus;

import net.botwithus.internal.scripts.ScriptDefinition;
import net.botwithus.rs3.game.Client;
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer;
import net.botwithus.rs3.script.LoopingScript;
import net.botwithus.rs3.script.config.ScriptConfig;
import net.botwithus.rs3.script.Execution;
import net.botwithus.rs3.game.Coordinate;
import net.botwithus.rs3.game.movement.NavPath;
import net.botwithus.rs3.game.movement.Movement;
import net.botwithus.rs3.game.movement.TraverseEvent;
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

public class BeerShopper extends LoopingScript {

    private BotState botState = BotState.NOT_LOGGED_IN;
    private boolean someBool = true;
    private int beersBought = 0;
    private boolean navigating = false;
    private long scriptStartTime;
    private long stateStartTime;
    private static final long TIMEOUT_DURATION = 15000;  // 15 seconds
    private static final long HOUR_IN_MILLIS = 3600000;  // 1 hour in milliseconds
    private BeerShopperGraphicsContext sgc;
    private Random random = new Random();
    private Coordinate shopCoordinate = new Coordinate(3215, 3395, 0);
    private Coordinate bankLocation = new Coordinate(3186, 3433, 0);
    private DialogState dialogState = DialogState.START;
    enum BotState {
        NOT_LOGGED_IN,
        BACKPACK_FULL,
        BARTENDER_NEARBY,
        NAVIGATE_TO_SHOP,
        INTERACT_WITH_BARTENDER,
        NAVIGATE_TO_BANK,
        HANDLE_DIALOG,
        CHECK_DOORS,
        STOPPED
    }

    private enum DialogState {
        START,
        FIRST_SPACE_PRESS,
        FIRST_CHOICE,
        SECOND_SPACE_PRESS,
        FINAL_SPACE_PRESS,
        COMPLETE
    }



    public BeerShopper(String s, ScriptConfig scriptConfig, ScriptDefinition scriptDefinition) {
        super(s, scriptConfig, scriptDefinition);
        this.sgc = new BeerShopperGraphicsContext(getConsole(), this);
        this.scriptStartTime = System.currentTimeMillis();  // Initialize script start time
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - scriptStartTime;
    }

    public double getBeersPerHour() {
        double hoursElapsed = (double) getElapsedTime() / HOUR_IN_MILLIS;
        return hoursElapsed > 0 ? beersBought / hoursElapsed : 0;
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
                if (moveTo(shopCoordinate)) {
                    botState = BotState.INTERACT_WITH_BARTENDER;
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
                if (moveTo(bankLocation)) {
                    botState = BotState.BACKPACK_FULL;
                }
                break;
            case HANDLE_DIALOG:
                handleBartenderDialog();
                break;
            case CHECK_DOORS:
                checkAndHandleDoors();
                break;
            case STOPPED:
                println("Bot has stopped.");
                break;
            default:
                break;
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

    private boolean moveTo(Coordinate location) {
        println("moveTo");
        LocalPlayer player = Client.getLocalPlayer();

        if (location.distanceTo(player.getCoordinate()) < 10) {
            println("moveTo | Already at the target location.");
            return true;
        }

        // Randomize the target location within a 3-tile radius
        Coordinate randomizedLocation = new Coordinate(
            location.getX() + random.nextInt(7) - 3,
            location.getY() + random.nextInt(7) - 3,
            location.getZ()
        );

        println("moveTo | Traversing to randomized location: " + randomizedLocation);
        NavPath path = NavPath.resolve(randomizedLocation);
        TraverseEvent.State moveState = Movement.traverse(path);

        switch (moveState) {
            case FINISHED:
                println("moveTo | Successfully moved to the area.");
                return true;

            case NO_PATH:
            case FAILED:
                println("moveTo | Path state: " + moveState.toString());
                println("moveTo | No path found or movement failed. Please navigate to the correct area manually.");
                botState = BotState.STOPPED;
                return false;

            default:
                println("moveTo | Unexpected state: " + moveState.toString());
                botState = BotState.STOPPED;
                return false;
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

    private void handleBartenderDialog() {
        while (Dialog.isOpen()) {
            println("Current Dialog State: " + dialogState);
            switch (dialogState) {
                case START:
                    if (Interfaces.isOpen(1184)) {
                        pressKeyAndAdvance(KeyEvent.VK_SPACE, DialogState.FIRST_SPACE_PRESS);
                    }
                    break;
                case FIRST_SPACE_PRESS:
                    if (Interfaces.isOpen(1188)) {
                        pressKeyAndAdvance(KeyEvent.VK_1, DialogState.FIRST_CHOICE);
                    }
                    break;
                case FIRST_CHOICE:
                    if (Interfaces.isOpen(1191)) {
                        pressKeyAndAdvance(KeyEvent.VK_SPACE, DialogState.SECOND_SPACE_PRESS);
                    }
                    break;
                case SECOND_SPACE_PRESS:
                    if (Interfaces.isOpen(1184)) {
                        pressKeyAndAdvance(KeyEvent.VK_SPACE, DialogState.FINAL_SPACE_PRESS);
                    }
                    break;
                case FINAL_SPACE_PRESS:
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

    private void interactWithBankBooth() {
        SceneObject bankBooth = SceneObjectQuery.newQuery().name("Bank booth").results().nearest();
        LocalPlayer player = Client.getLocalPlayer();

        if (bankBooth != null && bankBooth.getCoordinate().distanceTo(player.getCoordinate()) < 10) {
            println("Interacting with bank booth.");
            Bank.loadLastPreset();
            Execution.delay(random.nextLong(3000, 5000));
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
            if (!moveTo(bankLocation)) {
                botState = BotState.STOPPED;
            }
        }
    }

    private void pressKeyAndAdvance(int key, DialogState nextState) {
        KeyboardInput.pressKey(key);
        Execution.delay(random.nextInt(700, 1000));
        dialogState = nextState;
    }

    @Override
    public BeerShopperGraphicsContext getGraphicsContext() {
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
