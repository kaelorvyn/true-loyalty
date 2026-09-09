package com.bloodsoil.trueloyalty;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

public final class Geometry {

    private Geometry() {
    }

    public static List<Vector> fibonacciSphere(int count) {
        List<Vector> points = new ArrayList<>(count);
        if (count <= 1) {
            points.add(new Vector(0, 1, 0));
            return points;
        }
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < count; i++) {
            double y = 1 - (i / (double) (count - 1)) * 2;
            double r = Math.sqrt(Math.max(0, 1 - y * y));
            double theta = golden * i;
            points.add(new Vector(r * Math.cos(theta), y, r * Math.sin(theta)));
        }
        return points;
    }

    public static List<Vector> ring(int count) {
        List<Vector> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = i * 2.0 * Math.PI / count;
            points.add(new Vector(Math.cos(angle), 0, Math.sin(angle)));
        }
        return points;
    }

    public static Vector bezier(Vector start, Vector control, Vector end, double t) {
        double u = 1 - t;
        return start.clone().multiply(u * u)
                .add(control.clone().multiply(2 * u * t))
                .add(end.clone().multiply(t * t));
    }

    public static Vector tangent(Vector direction) {
        Vector up = new Vector(0, 1, 0);
        Vector cross = direction.clone().crossProduct(up);
        return cross.lengthSquared() < 1.0E-6 ? new Vector(1, 0, 0) : cross.normalize();
    }

    public static boolean wouldDie(double health, double absorption, double finalDamage) {
        return finalDamage >= health + absorption;
    }
}
