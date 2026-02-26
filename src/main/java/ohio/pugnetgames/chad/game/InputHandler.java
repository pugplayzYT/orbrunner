package ohio.pugnetgames.chad.game;

import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Manages all keyboard and mouse input states.
 * GamePanel owns this and Player reads from it.
 *
 * REWRITTEN: No longer registers its own GLFW callbacks.
 * GamePanel sets up global callbacks and forwards events here.
 */
public class InputHandler {

    // Key states
    public boolean wPressed, sPressed, aPressed, dPressed, spacePressed, shiftPressed;

    // Mouse state
    public float yaw, pitch;
    private double lastMouseX, lastMouseY;
    private boolean firstMouseMovement = true;

    // Sensitivity (adjustable from pause menu)
    private float ROTATION_SENSITIVITY = 0.1f;

    public void setSensitivity(float s) {
        this.ROTATION_SENSITIVITY = s;
    }

    public float getSensitivity() {
        return ROTATION_SENSITIVITY;
    }

    /**
     * Lightweight constructor — no GLFW registration.
     * GamePanel manages the GLFW callbacks and forwards events.
     */
    public InputHandler() {
        // Nothing to do — GamePanel handles callback registration
        this.firstMouseMovement = true;
    }

    /**
     * Legacy constructor for compatibility — ignores the window parameter.
     * Use the no-arg constructor instead.
     */
    public InputHandler(long window) {
        this(); // just call no-arg
    }

    /**
     * The key callback logic — invoke this from GamePanel's global key handler.
     */
    public final GLFWKeyCallback keyCallback = new GLFWKeyCallback() {
        @Override
        public void invoke(long window, int key, int scancode, int action, int mods) {
            boolean pressed = (action != GLFW_RELEASE);
            if (key == GLFW_KEY_W)
                wPressed = pressed;
            else if (key == GLFW_KEY_S)
                sPressed = pressed;
            else if (key == GLFW_KEY_A)
                aPressed = pressed;
            else if (key == GLFW_KEY_D)
                dPressed = pressed;
            else if (key == GLFW_KEY_SPACE)
                spacePressed = pressed;
            else if (key == GLFW_KEY_LEFT_SHIFT)
                shiftPressed = pressed;
        }
    };

    /**
     * The cursor position callback logic — invoke from GamePanel's global cursor
     * handler.
     */
    public final GLFWCursorPosCallback cursorPosCallback = new GLFWCursorPosCallback() {
        @Override
        public void invoke(long window, double xpos, double ypos) {
            if (firstMouseMovement) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouseMovement = false;
                return;
            }

            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;

            yaw += dx * ROTATION_SENSITIVITY;
            pitch += dy * ROTATION_SENSITIVITY;
            pitch = Math.max(-89.0f, Math.min(89.0f, pitch));

            lastMouseX = xpos;
            lastMouseY = ypos;
        }
    };

    /**
     * Resets the mouse state, e.g., for when the game (re)starts.
     */
    public void resetMouse() {
        this.firstMouseMovement = true;
    }
}