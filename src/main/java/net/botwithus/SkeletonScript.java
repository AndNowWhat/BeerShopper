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
    
    enum BotState {
        NOT_LOGGED_IN,
        BACKPACK_FULL,
        BARTENDER_NEARBY,
        NAVIGATE_TO_SHOP,
        NAVIGATE_TO_SECOND_LOCATION,
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
    private Coordinate secondLocation = new Coordinate(3226, 3397, 0);
    private Coordinate bankLocation = new Coordinate(3186, 3433, 0);
    private DialogState dialogState = DialogState.START;

    private long stateStartTime;
    private static final long TIMEOUT_DURATION = 15000;  // 15 seconds

    public SkeletonScript(String s, ScriptConfig scriptConfig, ScriptDefinition scriptDefinition) {
        super(s, scriptConfig, scriptDefinition);
        this.sgc = new SkeletonScriptGraphicsContext(getConsole(), this);

    }

    @Override
    public void onLoop() {
        LocalPlayer player = Client.getLocalPlayer();
        BotState state = determineState(player);

        if (System.currentTimeMillis() - stateStartTime > TIMEOUT_DURATION) {
            println("Timeout reached for state: " + state + ". Resetting state.");
            if (Backpack.isFull()) {
                state = botState.NAVIGATE_TO_BANK;
            } else {
                state = botState.NAVIGATE_TO_SHOP;
            }
            stateStartTime = System.currentTimeMillis();
        }

        switch (state) {
            case NOT_LOGGED_IN:
                Execution.delay(random.nextLong(3000, 7000));
                break;
            case BACKPACK_FULL:
                navigateToBank();
                break;
            case BARTENDER_NEARBY:
                Npc bartender = NpcQuery.newQuery().name("Bartender").results().nearest();
                if (bartender != null) {
                    interactWithBartender(bartender);
                }
                break;
            case NAVIGATE_TO_SHOP:
                navigateToShop();
                break;
            case NAVIGATE_TO_SECOND_LOCATION:
                navigateToSecondLocation();
                break;
            default:
                break;
        }
    }

    private BotState determineState(LocalPlayer player) {
        Client.GameState gameState = Client.getGameState();
        println("Game state: " + gameState);
        println("Local player: " + player);
        
        if (player == null || gameState != Client.GameState.LOGGED_IN) {
            return BotState.NOT_LOGGED_IN;
        } else if (Backpack.isFull()) {
            return botState.BACKPACK_FULL;
        } else {
            Npc bartender = NpcQuery.newQuery().name("Bartender").results().nearest();
            if (bartender != null && bartender.getCoordinate().distanceTo(player.getCoordinate()) < 5) {
                return botState.BARTENDER_NEARBY;
            } else if (shopCoordinate.distanceTo(player.getCoordinate()) >= 3) {
                return botState.NAVIGATE_TO_SHOP;
            } else {
                return botState.NAVIGATE_TO_SECOND_LOCATION;
            }
        }
    }


    private void navigateToBank() {
        println("Inventory is full. Moving to the bank.");
        Movement.walkTo(bankLocation.getX(), bankLocation.getY(), false);
        stateStartTime = System.currentTimeMillis();  // Reset the state start time
        long timeout = TIMEOUT_DURATION;
        LocalPlayer player = Client.getLocalPlayer();
        while (System.currentTimeMillis() - stateStartTime < timeout) {
            if (bankLocation.distanceTo(player.getCoordinate()) < 10) {
                println("Reached the bank location.");
                Bank.loadLastPreset();
                Execution.delay(random.nextLong(1500, 3000));
                stateStartTime = System.currentTimeMillis();  // Reset the state start time
                return;
            }
            Execution.delay(random.nextLong(100, 200));
        }
        println("Failed to reach the bank.");
        Execution.delay(random.nextLong(1500, 3000));
    }

    private void interactWithBartender(Npc bartender) {
        println("Bartender is near. Interacting with Bartender.");
        bartender.interact("Talk-to");
        stateStartTime = System.currentTimeMillis();  // Reset the state start time
        long timeout = TIMEOUT_DURATION;
        while (System.currentTimeMillis() - stateStartTime < timeout && !Dialog.isOpen()) {
            Execution.delay(100);
        }
        if (Dialog.isOpen()) {
            handleBartenderDialog();
        } else {
            println("Failed to open dialog with Bartender.");
        }
    }

    private void navigateToShop() {
        println("Navigating to the shop.");
        Movement.walkTo(shopCoordinate.getX(), shopCoordinate.getY(), false);
        stateStartTime = System.currentTimeMillis();  // Reset the state start time
        long timeout = TIMEOUT_DURATION;
        LocalPlayer player = Client.getLocalPlayer();
        while (System.currentTimeMillis() - stateStartTime < timeout) {
            if (shopCoordinate.distanceTo(player.getCoordinate()) < 3) {
                println("Reached the shop location.");
                checkAndHandleDoors();
                navigateToSecondLocation();
                return;
            }
            Execution.delay(random.nextLong(100, 200));
        }
        println("Failed to reach the shop.");
        Execution.delay(random.nextLong(1500, 3000));
    }

    private void navigateToSecondLocation() {
        println("Navigating to the second location.");
        Movement.walkTo(secondLocation.getX(), secondLocation.getY(), false);
        stateStartTime = System.currentTimeMillis();  // Reset the state start time
        long timeout = TIMEOUT_DURATION;
        LocalPlayer player = Client.getLocalPlayer();
        while (System.currentTimeMillis() - stateStartTime < timeout) {
            if (secondLocation.distanceTo(player.getCoordinate()) < 3) {
                println("Reached the second location.");
                findAndInteractWithBartender();
                return;
            }
            Execution.delay(random.nextLong(100, 200));
        }
        println("Failed to reach the second location.");
        Execution.delay(random.nextLong(1500, 3000));
    }

    private void findAndInteractWithBartender() {
        Npc bartender = NpcQuery.newQuery().name("Bartender").results().nearest();
        if (bartender != null) {
            interactWithBartender(bartender);
        } else {
            println("Bartender not found.");
        }
    }

    private void checkAndHandleDoors() {
        SceneObjectQuery query = SceneObjectQuery.newQuery()
            .name("Door")
            .option("Open", "Close");

        SceneObject door = query.results().nearest();

        if (door != null) {
            if (door.getOptions().contains("Open")) {
                println("Closed door found, opening it.");
                door.interact("Open");
                Execution.delay(random.nextLong(1000, 2000));
            } else if (door.getOptions().contains("Close")) {
                println("Door is already open.");
            } else {
                println("Door found but neither open nor close action available.");
            }
        } else {
            println("No door found.");
        }
    }

    private void handleBartenderDialog() {
        while (Dialog.isOpen()) {
            switch (dialogState) {
                case START:
                    if (Interfaces.isOpen(1184)) {
                        println("Handling initial dialog interface (1184).");
                        KeyboardInput.pressKey(KeyEvent.VK_SPACE);
                        Execution.delay(random.nextInt(1000, 1200));
                        dialogState = DialogState.FIRST_SPACE_PRESS;
                    }
                    break;
                case FIRST_SPACE_PRESS:
                    if (Interfaces.isOpen(1188)) {
                        println("Handling choice interface (1188).");
                        KeyboardInput.pressKey(KeyEvent.VK_1);
                        Execution.delay(random.nextInt(1000, 1200));
                        dialogState = DialogState.FIRST_CHOICE;
                    }
                    break;
                case FIRST_CHOICE:
                    if (Interfaces.isOpen(1191)) {
                        println("Handling second dialog interface (1191).");
                        KeyboardInput.pressKey(KeyEvent.VK_SPACE);
                        Execution.delay(random.nextInt(1000, 1200));
                        dialogState = DialogState.SECOND_SPACE_PRESS;
                    }
                    break;
                case SECOND_SPACE_PRESS:
                    if (Interfaces.isOpen(1184)) {
                        println("Handling final dialog interface (1184).");
                        KeyboardInput.pressKey(KeyEvent.VK_SPACE);
                        Execution.delay(random.nextInt(1000, 1200));
                        dialogState = DialogState.FINAL_SPACE_PRESS;
                    }
                    break;
                case FINAL_SPACE_PRESS:
                    if (Interfaces.isOpen(1191)) {
                        println("Completing dialog interaction.");
                        KeyboardInput.pressKey(KeyEvent.VK_SPACE);
                        Execution.delay(random.nextInt(1000, 1200));
                        dialogState = DialogState.COMPLETE;
                    }
                    break;
                case COMPLETE:
                    println("Dialog interaction complete.");
                    dialogState = DialogState.START;
                    return;
            }
        }
        println("Dialog not open.");
        dialogState = DialogState.START;
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
}
