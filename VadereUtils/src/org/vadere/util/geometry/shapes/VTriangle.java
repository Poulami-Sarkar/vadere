package org.vadere.util.geometry.shapes;

import java.util.Arrays;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.vadere.util.geometry.GeometryUtils;

/**
 * A triangle. Points must be given in counter clockwise manner to get correct
 * inward facing normals.
 *
 */
public class VTriangle extends VPolygon {

    /**
     * generated serial version uid
     */
    private static final long serialVersionUID = 5864412321949258915L;

    public final VPoint p1;
    public final VPoint p2;
    public final VPoint p3;

    public final VLine[] lines;

    /**
     * The centroid will be saved for performance boost, since this object is immutable.
     */
    private VPoint centroid;

    private VPoint center;

    private VPoint incenter;

    private VPoint orthocenter;

    /**
     * Creates a triangle. Points must be given in ccw order.
     *
     * @param p1 first point of the triangle
     * @param p2 second point of the triangle
     * @param p3 third point of the triangle
     */
    public VTriangle(@NotNull final VPoint p1, @NotNull final VPoint p2, @NotNull final VPoint p3) {
        super(GeometryUtils.polygonFromPoints2D(p1, p2, p3));

        if(p1.equals(p2) || p1.equals(p3) || p2.equals(p3)) {
            throw new IllegalArgumentException("" + p1 + p2 + p3 + " is not a feasible set of points.");
        }
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;

        lines = new VLine[]{ new VLine(p1, p2), new VLine(p2, p3), new VLine(p3,p1) };
    }

    @Override
    public boolean contains(final IPoint point) {
        return GeometryUtils.triangleContains(p1, p2, p3, point);

    }

    // TODO: find better name
    public boolean isPartOf(final IPoint point, final double eps) {
        double d1 = GeometryUtils.ccw(point, p1, p2);
        double d2 = GeometryUtils.ccw(point, p2, p3);
        double d3 = GeometryUtils.ccw(point, p3, p1);
        return (d1 <= eps && d2 <= eps && d3 <= eps) || (d1 >= -eps && d2 >= -eps && d3 >= -eps);
    }

    public VPoint midPoint() {
        return new VPoint((p1.getX() + p2.getX() + p3.getX()) / 3.0,
                (p1.getY() + p2.getY() + p3.getY()) / 3.0);
    }

    public boolean isLine() {
        VLine l1 = new VLine(p1, p2);
        VLine l2 = new VLine(p1, p3);
        VLine l3 = new VLine(p2, p3);

        return l1.ptSegDist(p3) < GeometryUtils.DOUBLE_EPS
                || l2.ptSegDist(p2) < GeometryUtils.DOUBLE_EPS
                || l3.ptSegDist(p1) < GeometryUtils.DOUBLE_EPS;
    }

    public boolean isNonAcute() {
        double angle1 = GeometryUtils.angle(p1, p2, p3);
        double angle2 = GeometryUtils.angle(p2, p3, p1);
        double angle3 = GeometryUtils.angle(p3, p1, p2);

        // non-acute triangle
        double maxAngle = Math.max(Math.max(angle1, angle2), angle3);
        double rightAngle = Math.PI/2;
        return maxAngle > rightAngle;
    }

    @Override
    public VPoint getCentroid() {
        if(centroid == null) {
            centroid = super.getCentroid();
        }
        return centroid;
    }

    public VPoint getIncenter(){
        if(incenter == null) {
            incenter = GeometryUtils.getIncenter(p1, p2, p3);
        }

        return incenter;
    }

    public VPoint getOrthocenter() {
        if(orthocenter == null) {
            double slope = -1 / ((p2.getY() - p1.getY()) / (p2.getX() - p1.getX()));
            // y  = slope * (x - p3.x) + p3.y

            double slope2 = -1 / ((p1.getY() - p3.getY()) / (p1.getX() - p3.getX()));// y = slope2 * (x - p2.x) + p2.y

            // slope2 * (x - p2.x) + p2.y  = slope * (x - p3.x) + p3.y
            // slope2 * (x - p2.x) - slope * (x - p3.x)  =  + p3.y - p2.y
            // slope2 * x - slope2 * p2.x - slope * x + slope * p3.x =  + p3.y - p2.y
            // slope2 * x  - slope * x  =  + p3.y - p2.y + slope2 * p2.x - slope * p3.x
            // x * (slope2 - slope) =  + p3.y - p2.y + slope2 * p2.x - slope * p3.x
            double x = (p3.getY() - p2.getY() + slope2 * p2.getX() - slope * p3.getX()) / (slope2 - slope);
            double y = slope * (x - p3.getX()) + p3.getY();
            orthocenter = new VPoint(x, y);
        }

        return orthocenter;
    }

    public VPoint closestPoint(final IPoint point) {

        VPoint currentClosest = null;
        double currentMinDistance = java.lang.Double.MAX_VALUE;

        for(VLine line : lines) {
            VPoint p = GeometryUtils.closestToSegment(line, point);
            if(p.distance(point) < currentMinDistance) {
                currentMinDistance = p.distance(point);
                currentClosest = p;
            }
        }

        return currentClosest;
    }

    public VPoint getCircumcenter(){
        if(center == null) {
            center = GeometryUtils.getCircumcenter(p1, p2, p3);
        }
        return center;
    }

    public double getCircumscribedRadius() {
        return getCircumcenter().distance(p1);
    }

    public boolean isInCircumscribedCycle(final IPoint point) {
        return getCircumcenter().distance(point) < getCircumscribedRadius();
    }

    public Stream<VLine> getLineStream() {
        return Arrays.stream(getLines());
    }

    public double maxCoordinate() {
        double max = Math.max(Math.abs(p1.getX()), Math.abs(p1.getY()));
        max = Math.max(max, Math.max(Math.abs(p2.getX()), Math.abs(p2.getY())));
        max = Math.max(max, Math.max(Math.abs(p3.getX()), Math.abs(p3.getY())));
        return max;
    }

    public VLine[] getLines() {
        return lines;
    }

    @Override
    public String toString() {
        return p1 + "-" + p2 + "-" + p3;
    }
}
