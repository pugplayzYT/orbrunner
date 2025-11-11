package ohio.pugnetgames.chad.menu;

import ohio.pugnetgames.chad.GameApp;
import ohio.pugnetgames.chad.core.ScoreManager;
import ohio.pugnetgames.chad.core.Difficulty; // <-- IMPORT NEW ENUM

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * MainMenu is the initial screen for the game, featuring the Play and Quit buttons.
 * It uses ScoreManager to display the best score.
 *
 * MODIFIED: Now shows Easy and Hard mode buttons.
 */
public class MainMenu extends JPanel {
    private final GameApp app;
    private JLabel bestScoreLabel;

    public MainMenu(GameApp app) {
        this.app = app;
        setLayout(new GridBagLayout());
        setBackground(new Color(24, 26, 32)); // Dark theme

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 10, 15, 10);
        gbc.gridx = 0;

        JLabel titleLabel = new JLabel("MAZE ESCAPE 3D");
        titleLabel.setFont(new Font("Inter", Font.BOLD, 36));
        titleLabel.setForeground(new Color(60, 255, 120)); // Bright green title
        gbc.gridy = 0;
        add(titleLabel, gbc);

        bestScoreLabel = new JLabel();
        bestScoreLabel.setFont(new Font("Inter", Font.PLAIN, 20));
        bestScoreLabel.setForeground(new Color(200, 200, 200));
        gbc.gridy = 1;
        add(bestScoreLabel, gbc);
        updateBestScoreDisplay();

        // --- NEW: EASY MODE BUTTON ---
        JButton easyButton = createStyledButton("EASY (3 Keys, +1 Win)");
        easyButton.addActionListener(e -> app.startGame(Difficulty.EASY)); // Pass EASY
        gbc.gridy = 2;
        add(easyButton, gbc);

        // --- NEW: HARD MODE BUTTON ---
        JButton hardButton = createStyledButton("HARD (10 Keys, +5 Wins)");
        hardButton.addActionListener(e -> app.startGame(Difficulty.HARD)); // Pass HARD
        gbc.gridy = 3;
        add(hardButton, gbc);

        // --- MODIFIED: Moved Quit button down ---
        JButton quitButton = createStyledButton("QUIT");
        quitButton.addActionListener(e -> System.exit(0));
        gbc.gridy = 4; // Moved to gridy 4
        add(quitButton, gbc);
    }

    /**
     * Updates the display of the securely loaded best score.
     */
    public void updateBestScoreDisplay() {
        long bestScore = ScoreManager.loadBestScore();
        // This text is still correct for the new goal
        bestScoreLabel.setText("BEST WINS: " + bestScore);
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);

        // --- STYLING FIX: Ensures high contrast on buttons ---
        button.setFont(new Font("Inter", Font.BOLD, 18));
        button.setForeground(new Color(60, 255, 120)); // Bright Green Text for contrast
        button.setBackground(new Color(35, 35, 40)); // Dark Gray/Blue Background
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(300, 50)); // Made buttons wider for new text

        // Custom look for that slick feel
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 255, 120), 2), // Bright Green Border
                BorderFactory.createEmptyBorder(10, 30, 10, 30)
        ));

        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(50, 50, 55)); // Lighter on hover
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(35, 35, 40)); // Back to dark
            }
        });
        return button;
    }
}