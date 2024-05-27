package net.botwithus;

import net.botwithus.rs3.imgui.ImGui;
import net.botwithus.rs3.imgui.ImGuiWindowFlag;
import net.botwithus.rs3.script.ScriptConsole;
import net.botwithus.rs3.script.ScriptGraphicsContext;

public class BeerShopperGraphicsContext extends ScriptGraphicsContext {

    private BeerShopper script;

    public BeerShopperGraphicsContext(ScriptConsole scriptConsole, BeerShopper script) {
        super(scriptConsole);
        this.script = script;
    }

    @Override
    public void drawSettings() {
        if (ImGui.Begin("Beer shopper", ImGuiWindowFlag.None.getValue())) {
            if (ImGui.BeginTabBar("My bar", ImGuiWindowFlag.None.getValue())) {
                if (ImGui.BeginTabItem("Settings", ImGuiWindowFlag.None.getValue())) {
                    ImGui.Text("Start in Varrock west bank or the Inn");
                    ImGui.Text("It will use load last preset, make sure empty inventory is loaded last.");
                    ImGui.Text("Have enough coins.");
                    ImGui.Text("Beers bought: " + script.getBeersBought());

                    long elapsedTime = script.getElapsedTime();
                    double beersPerHour = script.getBeersPerHour();

                    ImGui.Text(String.format("Script running time: %02d:%02d:%02d",
                        elapsedTime / 3600000, (elapsedTime / 60000) % 60, (elapsedTime / 1000) % 60));
                    ImGui.Text(String.format("Beers per hour: %.2f", beersPerHour));
                    
                    ImGui.EndTabItem();
                }
                ImGui.EndTabBar();
            }
            ImGui.End();
        }
    }

    @Override
    public void drawOverlay() {
        super.drawOverlay();
    }
}
