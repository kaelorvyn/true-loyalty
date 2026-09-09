package com.bloodsoil.trueloyalty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class GeometryTest {

    @Test
    void fibonacciSphereReturnsUnitVectors() {
        List<Vector> points = Geometry.fibonacciSphere(30);
        assertEquals(30, points.size());
        for (Vector point : points) {
            assertEquals(1.0, point.length(), 1.0E-6);
        }
    }

    @Test
    void bezierEndpointsMatch() {
        Vector start = new Vector(0, 0, 0);
        Vector control = new Vector(0, 3, 0);
        Vector end = new Vector(0, 6, 0);
        assertTrue(Geometry.bezier(start, control, end, 0).distanceSquared(start) < 1.0E-9);
        assertTrue(Geometry.bezier(start, control, end, 1).distanceSquared(end) < 1.0E-9);
    }

    @Test
    void ringReturnsHorizontalUnitVectors() {
        List<Vector> points = Geometry.ring(30);
        assertEquals(30, points.size());
        for (Vector point : points) {
            assertEquals(1.0, point.length(), 1.0E-6);
            assertEquals(0.0, point.getY(), 1.0E-9);
        }
    }

    @Test
    void wouldDieThreshold() {
        assertTrue(Geometry.wouldDie(5, 2, 7));
        assertFalse(Geometry.wouldDie(5, 2, 6.9));
        assertTrue(Geometry.wouldDie(1, 0, 1));
    }
}
