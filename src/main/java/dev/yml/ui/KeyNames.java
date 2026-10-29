package dev.yml.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.util.Locale;

/** Translates JavaFX input events into Minecraft's GLFW binding names. */
public final class KeyNames {

    private KeyNames() {
    }

    public static String glfw(KeyCode code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case ESCAPE -> "key.keyboard.escape";
            case ENTER -> "key.keyboard.enter";
            case TAB -> "key.keyboard.tab";
            case SPACE -> "key.keyboard.space";
            case BACK_SPACE -> "key.keyboard.backspace";
            case DELETE -> "key.keyboard.delete";
            case INSERT -> "key.keyboard.insert";
            case HOME -> "key.keyboard.home";
            case END -> "key.keyboard.end";
            case PAGE_UP -> "key.keyboard.page.up";
            case PAGE_DOWN -> "key.keyboard.page.down";
            case UP -> "key.keyboard.up";
            case DOWN -> "key.keyboard.down";
            case LEFT -> "key.keyboard.left";
            case RIGHT -> "key.keyboard.right";
            case CAPS -> "key.keyboard.caps.lock";
            case SHIFT -> "key.keyboard.left.shift";
            case CONTROL -> "key.keyboard.left.control";
            case ALT -> "key.keyboard.left.alt";
            case WINDOWS, META -> "key.keyboard.left.win";
            case COMMA -> "key.keyboard.comma";
            case PERIOD -> "key.keyboard.period";
            case SLASH -> "key.keyboard.slash";
            case SEMICOLON -> "key.keyboard.semicolon";
            case QUOTE -> "key.keyboard.apostrophe";
            case OPEN_BRACKET -> "key.keyboard.left.bracket";
            case CLOSE_BRACKET -> "key.keyboard.right.bracket";
            case BACK_SLASH -> "key.keyboard.backslash";
            case MINUS -> "key.keyboard.minus";
            case EQUALS -> "key.keyboard.equal";
            case BACK_QUOTE -> "key.keyboard.grave";
            case NUMPAD0 -> "key.keyboard.keypad.0";
            case NUMPAD1 -> "key.keyboard.keypad.1";
            case NUMPAD2 -> "key.keyboard.keypad.2";
            case NUMPAD3 -> "key.keyboard.keypad.3";
            case NUMPAD4 -> "key.keyboard.keypad.4";
            case NUMPAD5 -> "key.keyboard.keypad.5";
            case NUMPAD6 -> "key.keyboard.keypad.6";
            case NUMPAD7 -> "key.keyboard.keypad.7";
            case NUMPAD8 -> "key.keyboard.keypad.8";
            case NUMPAD9 -> "key.keyboard.keypad.9";
            case DECIMAL -> "key.keyboard.keypad.decimal";
            case ADD -> "key.keyboard.keypad.add";
            case SUBTRACT -> "key.keyboard.keypad.subtract";
            case MULTIPLY -> "key.keyboard.keypad.multiply";
            case DIVIDE -> "key.keyboard.keypad.divide";
            case NUM_LOCK -> "key.keyboard.num.lock";
            case SCROLL_LOCK -> "key.keyboard.scroll.lock";
            case PRINTSCREEN -> "key.keyboard.print.screen";
            case PAUSE -> "key.keyboard.pause";
            case HELP -> "key.keyboard.menu";
            default -> {
                String name = code.name();
                if (name.startsWith("DIGIT")) {
                    yield "key.keyboard." + name.substring(5);
                }
                if (name.startsWith("NUMPAD")) {
                    yield "key.keyboard.keypad." + name.substring(6);
                }
                if (name.startsWith("F") && name.length() <= 3) {
                    yield "key.keyboard." + name.toLowerCase(Locale.ROOT);
                }
                if (name.length() == 1) {
                    yield "key.keyboard." + name.toLowerCase(Locale.ROOT);
                }
                yield "key.keyboard." + name.toLowerCase(Locale.ROOT).replace('_', '.');
            }
        };
    }

    public static String glfw(MouseButton button) {
        return switch (button) {
            case PRIMARY -> "key.mouse.left";
            case SECONDARY -> "key.mouse.right";
            case MIDDLE -> "key.mouse.middle";
            case BACK -> "key.mouse.button.3";
            case FORWARD -> "key.mouse.button.4";
            default -> "key.mouse.left";
        };
    }

    /** The escape hint used by the capture UI. */
    public static boolean isCancel(KeyCode code) {
        return code == KeyCode.ESCAPE;
    }

    public static Button bindButton(String text, Runnable onClick) {
        Button button = new Button(text);
        button.getStyleClass().addAll("btn", "btn-small");
        button.setMinWidth(130);
        button.setOnAction(event -> onClick.run());
        return button;
    }
}
