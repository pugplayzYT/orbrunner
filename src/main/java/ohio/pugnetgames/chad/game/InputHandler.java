package ohio.pugnetgames.chad.game;

import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Manages all keyboard and mouse input states.
 * GamePanel owns this and Player reads from it.
 */
public class InputHandler {

    // Key states
    public boolean wPressed, sPressed, aPressed, dPressed, spacePressed, shiftPressed;

    // Mouse state
    public float yaw, pitch;
    private double lastMouseX, lastMouseY;
    private boolean firstMouseMovement = true;

    // Constants
    private final float ROTATION_SENSITIVITY = 0.1f;

    /**
     * Registers the callbacks with the GLFW window.
     */
    public InputHandler(long window) {
        // Read initial cursor position
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer xBuf = stack.mallocDouble(1);
            DoubleBuffer yBuf = stack.mallocDouble(1);
            glfwGetCursorPos(window, xBuf, yBuf);
            lastMouseX = xBuf.get(0);
            lastMouseY = yBuf.get(0);
        }

        // --- FIX: Set the public keyCallback here ---
        glfwSetKeyCallback(window, keyCallback);
        glfwSetCursorPosCallback(window, cursorPosCallback);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        firstMouseMovement = true;
    }

    // --- FIX: Made this public so GamePanel can access it ---
    public final GLFWKeyCallback keyCallback = new GLFWKeyCallback() {
        @Override
        public void invoke(long window, int key, int scancode, int action, int mods) {
            boolean pressed = (action != GLFW_RELEASE);
            if (key == GLFW_KEY_W) wPressed = pressed;
            else if (key == GLFW_KEY_S) sPressed = pressed;
            else if (key == GLFW_KEY_A) aPressed = pressed;
            else if (key == GLFW_KEY_D) dPressed = pressed;
            else if (key == GLFW_KEY_SPACE) spacePressed = pressed;
            else if (key == GLFW_KEY_LEFT_SHIFT) shiftPressed = pressed;
        }
    };

    // --- FIX: Made this public so GamePanel can reset it ---
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