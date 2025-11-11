package ohio.pugnetgames.chad;

import ohio.pugnetgames.chad.menu.MainMenu;
import ohio.pugnetgames.chad.game.GamePanel; // This now represents the LWJGL runner
import ohio.pugnetgames.chad.core.Difficulty; // <-- IMPORT NEW ENUM

import javax.swing.*;
import java.awt.*;

/**
 * GameApp.java
 * * The main application window and entry point.
 * * This now launches the MainMenu (Swing) and, upon pressing PLAY,
 * * launches the TRUE OpenGL (LWJGL/GLFW) game loop on a new thread/window.
 *
 * MODIFIED: Passes selected difficulty to GamePanel.
 */
public class GameApp extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private MainMenu mainMenu;
    private GamePanel gamePanel; // Keep the reference to handle state cleanup

    // --- Core Game Constants ---
    // MODIFIED: Removed GAME_DURATION_SECONDS

    public GameApp() {
        // Set up the main Swing frame
        setTitle("3D Maze Escape - LWJGL OpenGL Launcher"); // MODIFIED: New Title
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // Initialize component panels
        mainMenu = new MainMenu(this);

        mainPanel.add(mainMenu, "MENU");

        add(mainPanel);
        cardLayout.show(mainPanel, "MENU");

        setVisible(true);
    }

    /**
     * Shows the main menu panel.
     */
    public void showMenu() {
        // Since LWJGL GamePanel creates its own window, we just need to ensure cleanup
        if (gamePanel != null) {
            gamePanel.stopGame();
            // The game panel thread should naturally finish after stopGame()
        }
        cardLayout.show(mainPanel, "MENU");
        mainMenu.updateBestScoreDisplay();
        // Bring Swing window to front
        toFront();
        repaint();
    }

    /**
     * Starts the LWJGL game loop in a new window on a separate thread.
     *
     * @param difficulty The selected difficulty (EASY or HARD).
     */
    public void startGame(Difficulty difficulty) { // <-- MODIFIED SIGNATURE
        // Hide Swing menu while game is running
        setVisible(false);

        // 1. Initialize the Game Controller
        // MODIFIED: Constructor now takes difficulty
        gamePanel = new GamePanel(this, difficulty); // <-- PASS DIFFICULTY

        // 2. Run the LWJGL loop on a new thread (GamePanel now extends Thread)
        gamePanel.start();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(GameApp::new);
    }
}