package ohio.pugnetgames.chad.game;

import static org.lwjgl.opengl.GL11.*;

/**
 * A generalized class to handle rendering, transformation, and collision for
 * basic 3D shapes. This allows GamePanel to manage objects generically.
 */
public class GameObject {

    public enum ShapeType {
        CUBE, SPHERE, PLANE
    }

    // --- FIX: RESTORED THE MISSING VARIABLE DECLARATION ---
    private ShapeType shape;
    // -----------------------------------------------------
    private float posX, posY, posZ;
    private float scaleX, scaleY, scaleZ;
    private float rotationY;
    private float colorR, colorG, colorB;
    private int textureID;

    private boolean isCollidable;
    private boolean isRendered;

    /**
     * Constructor for a colored object. (Defaults to collidable, rendered)
     */
    public GameObject(ShapeType shape, float x, float y, float z, float sx, float sy, float sz, float r, float g, float b) {
        this.shape = shape;
        this.posX = x;
        // Shift Y position down by half of the object's height so objects are placed on the floor (Y=0)
        this.posY = y + sy / 2.0f;
        this.posZ = z;
        this.scaleX = sx;
        this.scaleY = sy;
        this.scaleZ = sz;
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.textureID = 0; // 0 means no texture
        this.rotationY = 0.0f;
        this.isCollidable = true;
        this.isRendered = true;
    }

    /**
     * Constructor for a textured object. (Defaults to collidable, rendered)
     */
    public GameObject(ShapeType shape, float x, float y, float z, float sx, float sy, float sz, int textureID) {
        this.shape = shape;
        this.posX = x;
        // Shift Y position down by half of the object's height so objects are placed on the floor (Y=0)
        this.posY = y + sy / 2.0f;
        this.posZ = z;
        this.scaleX = sx;
        this.scaleY = sy;
        this.scaleZ = sz;
        this.textureID = textureID;
        this.colorR = 1.0f; // White for texture modulation
        this.colorG = 1.0f;
        this.colorB = 1.0f;
        this.rotationY = 0.0f;
        this.isCollidable = true;
        this.isRendered = true;
    }

    /**
     * --- NEW: Constructor for a colored object with custom collision and rendering ---
     */
    public GameObject(ShapeType shape, float x, float y, float z, float sx, float sy, float sz, float r, float g, float b, boolean collidable, boolean renderable) {
        this.shape = shape;
        this.posX = x;
        this.posY = y + sy / 2.0f;
        this.posZ = z;
        this.scaleX = sx;
        this.scaleY = sy;
        this.scaleZ = sz;
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.textureID = 0;
        this.rotationY = 0.0f;
        this.isCollidable = collidable;
        this.isRendered = renderable;
    }

    /**
     * Constructor for a textured object with custom collision and rendering.
     */
    public GameObject(ShapeType shape, float x, float y, float z, float sx, float sy, float sz, int textureID, boolean collidable, boolean renderable) {
        this.shape = shape;
        this.posX = x;
        this.posY = y + sy / 2.0f;
        this.posZ = z;
        this.scaleX = sx;
        this.scaleY = sy;
        this.scaleZ = sz;
        this.textureID = textureID;
        this.colorR = 1.0f;
        this.colorG = 1.0f;
        this.colorB = 1.0f;
        this.rotationY = 0.0f;
        this.isCollidable = collidable;
        this.isRendered = renderable;
    }

    // --- Setters for runtime changes ---

    public void setRotationY(float rotationY) {
        this.rotationY = rotationY;
    }

    public void setColor(float r, float g, float b) {
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.textureID = 0;
        this.isRendered = true;
    }

    // --- NEW: Public setters for collidable and renderable ---
    public void setCollidable(boolean collidable) {
        this.isCollidable = collidable;
    }

    public void setRendered(boolean renderable) {
        this.isRendered = renderable;
    }
    // --- END NEW ---


    // --- Rendering ---

    public void render() {
        if (!isRendered) return; // FIX: Skip rendering if invisible (Courtyard walls)

        glPushMatrix();
        // Translate to object center
        glTranslatef(posX, posY, posZ);
        // Rotate (only Y axis supported for simple AABB collision)
        glRotatef(rotationY, 0.0f, 1.0f, 0.0f);

        // --- FIX: We remove glScalef() from here. ---
        // The scale is now handled by drawPlane/drawCube to fix texture stretching.

        if (textureID != 0) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, textureID);
            glColor3f(1.0f, 1.0f, 1.0f);
        } else {
            glDisable(GL_TEXTURE_2D);
            glColor3f(colorR, colorG, colorB);
        }

        // Render based on shape
        switch (shape) {
            case PLANE:
                // --- FIX: Pass scaleX and scaleZ to drawPlane ---
                drawPlane(scaleX, scaleZ);
                break;
            case CUBE:
                // --- FIX: Pass all scales to drawCube ---
                drawCube(scaleX, scaleY, scaleZ);
                break;
            case SPHERE:
                // --- FIX: Add glScalef back ONLY for sphere ---
                // (Since spheres are usually scaled uniformly and not tiled)
                glScalef(scaleX, scaleY, scaleZ);
                drawSphere(1.0f, 16, 16);
                break;
        }

        glPopMatrix();
    }

    /**
     * --- FIX: Draws a plane using its actual size for vertices AND texture coords ---
     */
    private void drawPlane(float sizeX, float sizeZ) {
        glBegin(GL_QUADS);
        // START FIX: Add normal for correct lighting (upward facing plane)
        glNormal3f(0.0f, 1.0f, 0.0f);
        // END FIX
        // Plane is centered at 0,0,0 and draws on the XZ axis (Y=0)
        // Texture coords now use sizeX/sizeZ to tile the texture.
        glTexCoord2f(0.0f, 0.0f); glVertex3f(-sizeX / 2, 0.0f, -sizeZ / 2);
        glTexCoord2f(sizeX, 0.0f); glVertex3f(sizeX / 2, 0.0f, -sizeZ / 2);
        glTexCoord2f(sizeX, sizeZ); glVertex3f(sizeX / 2, 0.0f, sizeZ / 2);
        glTexCoord2f(0.0f, sizeZ); glVertex3f(-sizeX / 2, 0.0f, sizeZ / 2);
        glEnd();
    }

    /**
     * --- FIX: Draws a cube using its actual size for vertices AND texture coords ---
     */
    private void drawCube(float sizeX, float sizeY, float sizeZ) {
        glBegin(GL_QUADS);
        // Front face (+Z) - Uses X and Y scales
        glNormal3f(0.0f, 0.0f, 1.0f);
        glTexCoord2f(0.0f, 0.0f); glVertex3f(-sizeX/2, -sizeY/2, sizeZ/2);
        glTexCoord2f(sizeX, 0.0f); glVertex3f( sizeX/2, -sizeY/2, sizeZ/2);
        glTexCoord2f(sizeX, sizeY); glVertex3f( sizeX/2,  sizeY/2, sizeZ/2);
        glTexCoord2f(0.0f, sizeY); glVertex3f(-sizeX/2,  sizeY/2, sizeZ/2);

        // Back face (-Z) - Uses X and Y scales
        glNormal3f(0.0f, 0.0f, -1.0f);
        glTexCoord2f(sizeX, 0.0f); glVertex3f(-sizeX/2, -sizeY/2, -sizeZ/2);
        glTexCoord2f(sizeX, sizeY); glVertex3f(-sizeX/2,  sizeY/2, -sizeZ/2);
        glTexCoord2f(0.0f, sizeY); glVertex3f( sizeX/2,  sizeY/2, -sizeZ/2);
        glTexCoord2f(0.0f, 0.0f); glVertex3f( sizeX/2, -sizeY/2, -sizeZ/2);

        // Top face (+Y) - Uses X and Z scales
        glNormal3f(0.0f, 1.0f, 0.0f);
        glTexCoord2f(0.0f, sizeZ); glVertex3f(-sizeX/2, sizeY/2, -sizeZ/2);
        glTexCoord2f(0.0f, 0.0f); glVertex3f(-sizeX/2, sizeY/2,  sizeZ/2);
        glTexCoord2f(sizeX, 0.0f); glVertex3f( sizeX/2, sizeY/2,  sizeZ/2);
        glTexCoord2f(sizeX, sizeZ); glVertex3f( sizeX/2, sizeY/2, -sizeZ/2);

        // Bottom face (-Y) - Uses X and Z scales
        glNormal3f(0.0f, -1.0f, 0.0f);
        glTexCoord2f(sizeX, sizeZ); glVertex3f(-sizeX/2, -sizeY/2, -sizeZ/2);
        glTexCoord2f(0.0f, sizeZ); glVertex3f( sizeX/2, -sizeY/2, -sizeZ/2);
        glTexCoord2f(0.0f, 0.0f); glVertex3f( sizeX/2, -sizeY/2,  sizeZ/2);
        glTexCoord2f(sizeX, 0.0f); glVertex3f(-sizeX/2, -sizeY/2,  sizeZ/2);

        // Right face (+X) - Uses Z and Y scales
        glNormal3f(1.0f, 0.0f, 0.0f);
        glTexCoord2f(sizeZ, 0.0f); glVertex3f( sizeX/2, -sizeY/2, -sizeZ/2);
        glTexCoord2f(sizeZ, sizeY); glVertex3f( sizeX/2,  sizeY/2, -sizeZ/2);
        glTexCoord2f(0.0f, sizeY); glVertex3f( sizeX/2,  sizeY/2,  sizeZ/2);
        glTexCoord2f(0.0f, 0.0f); glVertex3f( sizeX/2, -sizeY/2,  sizeZ/2);

        // Left face (-X) - Uses Z and Y scales
        glNormal3f(-1.0f, 0.0f, 0.0f);
        glTexCoord2f(0.0f, 0.0f); glVertex3f(-sizeX/2, -sizeY/2, -sizeZ/2);
        glTexCoord2f(sizeZ, 0.0f); glVertex3f(-sizeX/2, -sizeY/2,  sizeZ/2);
        glTexCoord2f(sizeZ, sizeY); glVertex3f(-sizeX/2,  sizeY/2,  sizeZ/2);
        glTexCoord2f(0.0f, sizeY); glVertex3f(-sizeX/2,  sizeY/2, -sizeZ/2);
        glEnd();
    }

    /**
     * Draws a sphere using GL_QUADS.
     * (This is scaled in render() and will stretch if scaled non-uniformly)
     */
    private void drawSphere(float radius, int rings, int sectors) {
        float R = 1f / (float) (rings - 1);
        float S = 1f / (float) (sectors - 1);
        glBegin(GL_QUADS);
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                // Calculate Texture Coordinates (u, v)
                float u0 = (float) s * S;
                float u1 = (float) (s + 1) * S;
                float v0 = (float) r * R;
                float v1 = (float) (r + 1) * R;

                // Vertex Calculations
                float y0 = (float) Math.cos(Math.PI * (r + 0) * R);
                float y1 = (float) Math.cos(Math.PI * (r + 1) * R);
                float r0 = (float) Math.sin(Math.PI * (r + 0) * R);
                float r1 = (float) Math.sin(Math.sin(Math.PI * (r + 1) * R));
                float x0 = (float) Math.cos(2 * Math.PI * (s + 0) * S);
                float x1 = (float) Math.cos(2 * Math.PI * (s + 1) * S);
                float z0 = (float) Math.sin(2 * Math.PI * (s + 0) * S);
                float z1 = (float) Math.sin(2 * Math.PI * (s + 1) * S);

                // The Normal (N) is the same as the position on the UNIT sphere.
                // START FIX: Add Normals for correct lighting.

                // Quad 1: Top-Left (r0, s0)
                glNormal3f(r0 * x0, y0, r0 * z0);
                glTexCoord2f(u0, v0);
                glVertex3f(r0 * x0 * radius, y0 * radius, r0 * z0 * radius);

                // Quad 2: Top-Right (r0, s1)
                glNormal3f(r0 * x1, y0, r0 * z1);
                glTexCoord2f(u1, v0);
                glVertex3f(r0 * x1 * radius, y0 * radius, r0 * z1 * radius);

                // Quad 3: Bottom-Right (r1, s1)
                glNormal3f(r1 * x1, y1, r1 * z1);
                glTexCoord2f(u1, v1);
                glVertex3f(r1 * x1 * radius, y1 * radius, r1 * z1 * radius);

                // Quad 4: Bottom-Left (r1, s0)
                glNormal3f(r1 * x0, y1, r1 * z0);
                glTexCoord2f(u0, v1);
                glVertex3f(r1 * x0 * radius, y1 * radius, r1 * z0 * radius);
                // END FIX
            }
        }
        glEnd();
    }


    /**
     * Checks if the player's AABB collides with this object's AABB.
     * This method assumes the object is non-rotated (AABB).
     * * @param pX Player's X position.
     * @param pY Player's Y position.
     * @param pZ Player's Z position.
     * @param pR Player's half-width for collision.
     * @param pH Player's half-height for collision.
     * @return true if collision detected.
     */
    public boolean isColliding(float pX, float pY, float pZ, float pR, float pH) {
        // Player's AABB
        float pMinX = pX - pR;
        float pMaxX = pX + pR;
        float pMinY = pY - pH; // Player is centered on Y-axis (or offset)
        float pMaxY = pY + pH;
        float pMinZ = pZ - pR;
        float pMaxZ = pZ + pR;

        // Object's AABB
        float oMinX = posX - scaleX / 2.0f;
        float oMaxX = posX + scaleX / 2.0f;
        float oMinY = posY - scaleY / 2.0f;
        float oMaxY = posY + scaleY / 2.0f;
        float oMinZ = posZ - scaleZ / 2.0f;
        float oMaxZ = posZ + scaleZ / 2.0f;

        // Check for overlap on all three axes
        boolean overlapX = pMinX < oMaxX && pMaxX > oMinX;
        boolean overlapY = pMinY < oMaxY && pMaxY > oMinY;
        boolean overlapZ = pMinZ < oMaxZ && pMaxZ > oMinZ;

        return overlapX && overlapY && overlapZ;
    }


    /**
     * Resolves collision and returns the corrected position along an axis.
     * This is a simple separation axis algorithm for non-rotated AABB.
     */
    public float resolveCollision(float pCurrent, float pPrev, float oCenter, float oScale, float pRadius) {
        // 1. Check which side the player is coming from
        float oMin = oCenter - oScale / 2.0f;
        float oMax = oCenter + oScale / 2.0f;

        if (pCurrent > oCenter) {
            // Player is to the right/front of the object. New position should be oMax + pRadius
            // We only resolve if the player was on the outside previously
            if (pPrev > oMax) {
                return oMax + pRadius;
            }
        } else {
            // Player is to the left/back of the object. New position should be oMin - pRadius
            if (pPrev < oMin) {
                return oMin - pRadius;
            }
        }
        return pCurrent; // No major correction needed, or coming from inside/along the axis.
    }

    // --- Getters for GamePanel to use in collision resolution ---
    public float getPosX() { return posX; }
    public float getPosZ() { return posZ; }
    public float getScaleX() { return scaleX; }
    public float getScaleZ() { return scaleZ; }
    public float getScaleY() { return scaleY; }
    public float getPosY() { return posY; }

    public ShapeType getShape() {
        return shape;
    }

    public boolean isCollidable() {
        return isCollidable;
    }

    // --- 💥 FIX: ADD MISSING GETTER FOR TEXTURE ID 💥 ---
    public int getTextureID() {
        return textureID;
    }
    // --- 💥 END FIX 💥 ---
}