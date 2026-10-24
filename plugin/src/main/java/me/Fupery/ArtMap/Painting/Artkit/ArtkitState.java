package me.Fupery.ArtMap.Painting.Artkit;

public class ArtkitState {
    private ArtkitColor color = ArtkitPalette.defaultColor();
    private ArtkitTool tool = ArtkitTool.DYE;

    public ArtkitColor getColor() {
        return color;
    }

    public void setColor(ArtkitColor color) {
        if (color != null) this.color = color;
    }

    public ArtkitTool getTool() {
        return tool;
    }

    public void setTool(ArtkitTool tool) {
        if (tool != null) this.tool = tool;
    }
}
