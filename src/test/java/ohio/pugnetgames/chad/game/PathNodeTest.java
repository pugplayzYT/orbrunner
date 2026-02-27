package ohio.pugnetgames.chad.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PathNode construction, neighbor linking, and reset behaviour.
 */
class PathNodeTest {

    @Test
    void constructorStoresGridCoordinates() {
        PathNode node = new PathNode(3, 7, 0.6f, 1.4f, true);
        assertEquals(3, node.x);
        assertEquals(7, node.z);
    }

    @Test
    void constructorStoresWorldCoordinates() {
        PathNode node = new PathNode(0, 0, 12.5f, -4.2f, true);
        assertEquals(12.5f, node.worldX, 0.001f);
        assertEquals(-4.2f, node.worldZ, 0.001f);
    }

    @Test
    void constructorStoresWalkableTrue() {
        PathNode node = new PathNode(0, 0, 0, 0, true);
        assertTrue(node.isWalkable);
    }

    @Test
    void constructorStoresWalkableFalse() {
        PathNode node = new PathNode(0, 0, 0, 0, false);
        assertFalse(node.isWalkable);
    }

    @Test
    void neighborsListInitiallyEmpty() {
        PathNode node = new PathNode(0, 0, 0, 0, true);
        assertNotNull(node.neighbors);
        assertTrue(node.neighbors.isEmpty());
    }

    @Test
    void parentInitiallyNull() {
        PathNode node = new PathNode(0, 0, 0, 0, true);
        assertNull(node.parent);
    }

    @Test
    void addNeighborAddsWalkableNode() {
        PathNode node     = new PathNode(0, 0, 0, 0, true);
        PathNode neighbor = new PathNode(1, 0, 0.2f, 0, true);
        node.addNeighbor(neighbor);
        assertEquals(1, node.neighbors.size());
        assertTrue(node.neighbors.contains(neighbor));
    }

    @Test
    void addNeighborIgnoresNullNode() {
        PathNode node = new PathNode(0, 0, 0, 0, true);
        node.addNeighbor(null);
        assertTrue(node.neighbors.isEmpty());
    }

    @Test
    void addNeighborIgnoresUnwalkableNode() {
        PathNode node     = new PathNode(0, 0, 0, 0, true);
        PathNode wall     = new PathNode(1, 0, 0.2f, 0, false);
        node.addNeighbor(wall);
        assertTrue(node.neighbors.isEmpty());
    }

    @Test
    void addMultipleNeighbors() {
        PathNode node  = new PathNode(1, 1, 0, 0, true);
        PathNode north = new PathNode(1, 0, 0, -0.2f, true);
        PathNode south = new PathNode(1, 2, 0,  0.2f, true);
        PathNode east  = new PathNode(2, 1, 0.2f, 0, true);
        node.addNeighbor(north);
        node.addNeighbor(south);
        node.addNeighbor(east);
        assertEquals(3, node.neighbors.size());
    }

    @Test
    void resetClearsParent() {
        PathNode node   = new PathNode(0, 0, 0, 0, true);
        PathNode parent = new PathNode(1, 0, 0, 0, true);
        node.parent = parent;
        node.reset();
        assertNull(node.parent);
    }

    @Test
    void resetDoesNotClearNeighbors() {
        PathNode node     = new PathNode(0, 0, 0, 0, true);
        PathNode neighbor = new PathNode(1, 0, 0.2f, 0, true);
        node.addNeighbor(neighbor);
        node.reset();
        assertEquals(1, node.neighbors.size(),
            "reset() should clear parent but NOT remove existing neighbors");
    }
}
