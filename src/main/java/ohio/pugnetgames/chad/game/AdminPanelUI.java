package ohio.pugnetgames.chad.game;

import ohio.pugnetgames.chad.game.GameObject.ShapeType;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection; // NEW IMPORT
import java.awt.Toolkit; // NEW IMPORT

/**
 * AdminPanelUI is a separate Swing window used to control game cheats and settings
 * while the LWJGL game is running. It interacts with the GamePanel instance.
 */
public class AdminPanelUI extends JFrame {

    private final GamePanel gamePanel;
    private JToggleButton autoCollectToggle;
    private JToggleButton debugLinesToggle;
    private JSlider rSlider, gSlider, bSlider;

    // Object Spawner UI
    private JComboBox<String> shapeSelector;
    private JComboBox<String> textureSelector;

    // NEW UI Components for Teleport
    private JTextField xField, yField, zField;
    private JLabel currentCoordsLabel;

    public AdminPanelUI(GamePanel gamePanel) {
        this.gamePanel = gamePanel;

        setTitle("Pug's Admin Console");
        setSize(400, 750); // Increased height for new features
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        // Use a dark, high-contrast theme
        Color darkBg = new Color(24, 26, 32);
        Color brightGreen = new Color(60, 255, 120);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(darkBg);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Title ---
        JLabel titleLabel = createLabel("Pug's Admin Panel", brightGreen);
        titleLabel.setFont(new Font("Inter", Font.BOLD, 20));
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // --- Object Spawner Section ---
        mainPanel.add(createLabel("--- Spawn Object ---", new Color(255, 165, 0)));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Shape Selector
        shapeSelector = new JComboBox<>(new String[]{"CUBE", "SPHERE", "TABLE", "KEY"});
        shapeSelector.setMaximumSize(new Dimension(200, 30));
        shapeSelector.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(new JLabel("Shape:"));
        mainPanel.add(shapeSelector);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Texture Selector
        textureSelector = new JComboBox<>(new String[]{"WALL (Tunnel)", "ORB (Grass)", "NONE (Color)"});
        textureSelector.setMaximumSize(new Dimension(200, 30));
        textureSelector.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(new JLabel("Texture:"));
        mainPanel.add(textureSelector);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Spawn Button
        JButton spawnButton = createStyledButton("SPAWN at Player", new Color(255, 165, 0));
        spawnButton.addActionListener(e -> spawnObject());
        mainPanel.add(spawnButton);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // --- Cheats and Toggles Section ---
        mainPanel.add(createLabel("--- Cheats ---", brightGreen));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Auto Collect Toggle
        autoCollectToggle = new JToggleButton("Toggle Auto Collect (AI)");
        autoCollectToggle.setForeground(brightGreen);
        autoCollectToggle.setBackground(new Color(35, 35, 40));
        autoCollectToggle.setFont(new Font("Inter", Font.BOLD, 16));
        autoCollectToggle.setFocusPainted(false);
        autoCollectToggle.setAlignmentX(Component.CENTER_ALIGNMENT);

        autoCollectToggle.addActionListener(e -> {
            gamePanel.setAutoCollectActive(autoCollectToggle.isSelected());
            if (autoCollectToggle.isSelected()) {
                gamePanel.setFreeCamActive(false);
            }
        });
        mainPanel.add(autoCollectToggle);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // --- Debug Lines Toggle ---
        if (gamePanel.isDebugLinesFeatureAvailable()) {
            debugLinesToggle = new JToggleButton("Toggle Debug Lines (Path/Respawn)");
            debugLinesToggle.setForeground(new Color(0, 200, 255)); // Cyan
            debugLinesToggle.setBackground(new Color(35, 35, 40));
            debugLinesToggle.setFont(new Font("Inter", Font.BOLD, 16));
            debugLinesToggle.setFocusPainted(false);
            debugLinesToggle.setAlignmentX(Component.CENTER_ALIGNMENT);

            debugLinesToggle.addActionListener(e -> {
                gamePanel.setDebugLinesActive(debugLinesToggle.isSelected());
            });
            mainPanel.add(debugLinesToggle);
            mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        }

        // --- NEW: Player Teleport Section ---
        Color tpColor = new Color(100, 149, 237); // Cornflower Blue
        mainPanel.add(createLabel("--- Player Teleport ---", tpColor));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        currentCoordsLabel = createLabel("Pos: (0.0, 0.0, 0.0)", Color.WHITE);
        mainPanel.add(currentCoordsLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        JButton copyCoordsButton = createStyledButton("Copy Coords", tpColor);
        copyCoordsButton.addActionListener(e -> copyCoordsToClipboard());
        mainPanel.add(copyCoordsButton);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel tpPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        tpPanel.setBackground(darkBg);
        xField = new JTextField("0.0", 5);
        yField = new JTextField("1.5", 5);
        zField = new JTextField("0.0", 5);
        tpPanel.add(createLabel("X:", Color.WHITE));
        tpPanel.add(xField);
        tpPanel.add(createLabel("Y:", Color.WHITE));
        tpPanel.add(yField);
        tpPanel.add(createLabel("Z:", Color.WHITE));
        tpPanel.add(zField);
        mainPanel.add(tpPanel);

        JButton teleportButton = createStyledButton("TELEPORT", tpColor);
        teleportButton.addActionListener(e -> executeTeleport());
        mainPanel.add(teleportButton);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        // --- END NEW SECTION ---


        // --- Ground Color Sliders (RGB) ---
        mainPanel.add(createLabel("Ground Color (0-100) (Non-functional)", brightGreen));

        rSlider = createColorSlider("R:", new Color(255, 100, 100));
        gSlider = createColorSlider("G:", new Color(100, 255, 100));
        bSlider = createColorSlider("B:", new Color(100, 100, 255));

        mainPanel.add(createSliderPanel(rSlider, "R:"));
        mainPanel.add(createSliderPanel(gSlider, "G:"));
        mainPanel.add(createSliderPanel(bSlider, "B:"));

        rSlider.setValue(10);
        gSlider.setValue(50);
        bSlider.setValue(20);

        rSlider.addChangeListener(e -> updateGroundColor());
        gSlider.addChangeListener(e -> updateGroundColor());
        bSlider.addChangeListener(e -> updateGroundColor());

        add(mainPanel);
    }

    /**
     * Executes the object spawning command.
     */
    private void spawnObject() {
        String selectedShape = (String) shapeSelector.getSelectedItem();
        String selectedTexture = (String) textureSelector.getSelectedItem();

        int textureId = 0;
        if (selectedTexture.startsWith("WALL")) {
            textureId = gamePanel.getWallTextureID();
        } else if (selectedTexture.startsWith("ORB")) {
            textureId = gamePanel.getOrbTextureID();
        }
        gamePanel.addObjectAtPlayerPosition(selectedShape, textureId);
    }

    /**
     * NEW: Executes the teleport command.
     */
    private void executeTeleport() {
        try {
            float x = Float.parseFloat(xField.getText());
            float y = Float.parseFloat(yField.getText());
            float z = Float.parseFloat(zField.getText());
            gamePanel.teleportPlayer(x, y, z);
        } catch (NumberFormatException e) {
            System.err.println("Invalid coordinate format for teleport: " + e.getMessage());
        }
    }

    /**
     * NEW: Gets current coords from GamePanel, updates label, and copies to clipboard.
     */
    private void copyCoordsToClipboard() {
        float[] pos = gamePanel.getPlayerPosition();
        // Format as (X, Y, Z)
        String coords = String.format("(%.2f, %.2f, %.2f)", pos[0], pos[1], pos[2]);
        currentCoordsLabel.setText("Pos: " + coords); // Update label

        // Copy just the coords, e.g., "(1.23, 4.56, 7.89)"
        StringSelection stringSelection = new StringSelection(coords);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }

    /**
     * Helper to create a styled label.
     */
    private JLabel createLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(new Font("Inter", Font.BOLD, 14));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    /**
     * Helper to create a button with a custom color.
     */
    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Inter", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setPreferredSize(new Dimension(200, 30));
        return button;
    }

    /**
     * Helper to create and style a color slider.
     */
    private JSlider createColorSlider(String name, Color thumbColor) {
        JSlider slider = new JSlider(0, 100);
        slider.setMajorTickSpacing(25);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setBackground(new Color(24, 26, 32));
        slider.setForeground(Color.WHITE);
        slider.setPreferredSize(new Dimension(300, 50));
        return slider;
    }

    /**
     * Helper to wrap a slider and its label in a panel.
     */
    private JPanel createSliderPanel(JSlider slider, String labelText) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.setBackground(new Color(24, 26, 32));
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        panel.add(label);
        panel.add(slider);
        return panel;
    }

    /**
     * Reads slider values and updates the GamePanel's ground color state.
     */
    private void updateGroundColor() {
        float r = rSlider.getValue() / 100.0f;
        float g = gSlider.getValue() / 100.0f;
        float b = bSlider.getValue() / 100.0f;
        gamePanel.setGroundColor(r, g, b);
    }

    /**
     * Resets the Swing UI components state to reflect the game state.
     */
    public void syncToGameState() {
        // Sync toggles
        autoCollectToggle.setSelected(gamePanel.isAutoCollectActive());
        if (debugLinesToggle != null) {
            debugLinesToggle.setSelected(gamePanel.isDebugLinesActive());
        }

        // Sync colors
        float[] colors = gamePanel.getGroundColor();
        rSlider.setValue((int) (colors[0] * 100));
        gSlider.setValue((int) (colors[1] * 100));
        bSlider.setValue((int) (colors[2] * 100));

        // --- NEW: Update TP fields and label ---
        float[] pos = gamePanel.getPlayerPosition();
        currentCoordsLabel.setText(String.format("Pos: (%.2f, %.2f, %.2f)", pos[0], pos[1], pos[2]));
        xField.setText(String.format("%.2f", pos[0]));
        yField.setText(String.format("%.2f", pos[1]));
        zField.setText(String.format("%.2f", pos[2]));
    }
}