package me._furiouspotato_.bingo.model;

import java.util.Locale;

import org.bukkit.Material;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum TeamColor {
    WHITE("White", NamedTextColor.WHITE, Material.WHITE_WOOL),
    ORANGE("Orange", NamedTextColor.GOLD, Material.ORANGE_WOOL),
    MAGENTA("Magenta", NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_WOOL),
    LIGHT_BLUE("Light Blue", NamedTextColor.BLUE, Material.LIGHT_BLUE_WOOL),
    YELLOW("Yellow", NamedTextColor.YELLOW, Material.YELLOW_WOOL),
    LIME("Lime", NamedTextColor.GREEN, Material.LIME_WOOL),
    PINK("Pink", NamedTextColor.LIGHT_PURPLE, Material.PINK_WOOL),
    GRAY("Gray", NamedTextColor.DARK_GRAY, Material.GRAY_WOOL),
    LIGHT_GRAY("Light Gray", NamedTextColor.GRAY, Material.LIGHT_GRAY_WOOL),
    CYAN("Cyan", NamedTextColor.DARK_AQUA, Material.CYAN_WOOL),
    PURPLE("Purple", NamedTextColor.DARK_PURPLE, Material.PURPLE_WOOL),
    BLUE("Blue", NamedTextColor.DARK_BLUE, Material.BLUE_WOOL),
    BROWN("Brown", NamedTextColor.GOLD, Material.BROWN_WOOL),
    GREEN("Green", NamedTextColor.DARK_GREEN, Material.GREEN_WOOL),
    RED("Red", NamedTextColor.RED, Material.RED_WOOL),
    BLACK("Black", NamedTextColor.BLACK, Material.BLACK_WOOL);

    private final String displayName;
    private final NamedTextColor textColor;
    private final Material woolMaterial;

    TeamColor(String displayName, NamedTextColor textColor, Material woolMaterial) {
        this.displayName = displayName;
        this.textColor = textColor;
        this.woolMaterial = woolMaterial;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor textColor() {
        return textColor;
    }

    public Material woolMaterial() {
        return woolMaterial;
    }

    public Component displayNameComponent() {
        return Component.text(displayName, textColor);
    }

    public static TeamColor parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (TeamColor color : values()) {
            if (color.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return color;
            }
        }
        throw new IllegalArgumentException("Unknown team: " + value);
    }
}
