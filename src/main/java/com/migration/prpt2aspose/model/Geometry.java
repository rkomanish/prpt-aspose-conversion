package com.migration.prpt2aspose.model;

/** Absolute position/size in points, as PRPT stores it. */
public record Geometry(double x, double y, double width, double height) {

    public static final Geometry ZERO = new Geometry(0, 0, 0, 0);
}
