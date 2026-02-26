package ohio.pugnetgames.chad;

import ohio.pugnetgames.chad.game.GamePanel;

/**
 * GameApp — Entry point for Maze Escape 3D.
 *
 * REWRITTEN: No more Swing JFrame. The entire application runs inside a
 * single GLFW/OpenGL window managed by GamePanel's state machine.
 * Menus, gameplay, and game-over screens are all rendered in-engine.
 */
public class GameApp {

    public static void main(String[] args) {
        System.out.println("Starting Maze Escape 3D...");

        // GamePanel extends Thread and manages the entire app lifecycle:
        // MAIN_MENU -> PLAYING -> GAME_OVER -> MAIN_MENU
        GamePanel gamePanel = new GamePanel();
        gamePanel.start();

        // Wait for the game to finish before exiting the JVM
        try {
            gamePanel.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Maze Escape 3D has shut down.");
        System.exit(0);
    }
}