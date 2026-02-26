package ohio.pugnetgames.chad.launcher;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses simple markdown (matching the game's UpdateLogManager format)
 * and produces a list of styled JavaFX nodes.
 *
 * Supported syntax: # H1, ## H2, ### H3, - bullets, --- separator, plain text.
 * Bold markers (**) are stripped — style is conveyed by type instead.
 */
public class MarkdownRenderer {

    private enum LineType { H1, H2, H3, BULLET, SEPARATOR, TEXT, BLANK }

    private record ParsedLine(LineType type, String text) {}

    public static List<Node> render(String markdown) {
        List<Node> nodes = new ArrayList<>();
        String[] rawLines = markdown.split("\n");

        for (String raw : rawLines) {
            ParsedLine parsed = parseLine(raw);

            switch (parsed.type) {
                case BLANK -> {
                    Region gap = new Region();
                    gap.setPrefHeight(6);
                    nodes.add(gap);
                }
                case SEPARATOR -> {
                    Separator sep = new Separator();
                    sep.getStyleClass().add("md-separator");
                    sep.setPadding(new Insets(4, 0, 4, 0));
                    nodes.add(sep);
                }
                case H1 -> nodes.add(styledLabel(parsed.text, "md-h1"));
                case H2 -> nodes.add(styledLabel(parsed.text, "md-h2"));
                case H3 -> nodes.add(styledLabel(parsed.text, "md-h3"));
                case BULLET -> {
                    Label label = styledLabel("· " + parsed.text, "md-bullet");
                    label.setPadding(new Insets(0, 0, 0, 14));
                    nodes.add(label);
                }
                case TEXT -> nodes.add(styledLabel(parsed.text, "md-text"));
            }
        }
        return nodes;
    }

    private static Label styledLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static ParsedLine parseLine(String raw) {
        String trimmed = raw.trim();

        if (trimmed.isEmpty())          return new ParsedLine(LineType.BLANK,     "");
        if (trimmed.startsWith("---"))  return new ParsedLine(LineType.SEPARATOR, "");

        LineType type;
        String text;

        if (trimmed.startsWith("### ")) {
            type = LineType.H3;
            text = trimmed.substring(4);
        } else if (trimmed.startsWith("## ")) {
            type = LineType.H2;
            text = trimmed.substring(3);
        } else if (trimmed.startsWith("# ")) {
            type = LineType.H1;
            text = trimmed.substring(2);
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            type = LineType.BULLET;
            text = trimmed.substring(2);
        } else {
            type = LineType.TEXT;
            text = trimmed;
        }

        // Strip bold markers — style conveys emphasis instead
        text = text.replace("**", "");
        return new ParsedLine(type, text.trim());
    }
}
