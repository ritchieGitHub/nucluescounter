package at.ac.ist.fiji.nucluescounter;

import java.awt.geom.PathIterator;

import ij.gui.OvalRoi;
import ij.gui.ShapeRoi;

public class ExtendedShapeRoi implements Comparable<ExtendedShapeRoi> {
	private final ShapeRoi roi;
	private double diameter;
	private double angle;
	private double min;
	private double feretX;
	private double feretY;

	public ExtendedShapeRoi(ShapeRoi roi) {
		this.roi = roi;
		init();
	}

	private void init() {
		double[] feretValues = this.roi.getFeretValues();
		diameter = feretValues[0];
		angle = feretValues[1];
		min = feretValues[2];
		feretX = feretValues[3];
		feretY = feretValues[4];
	}

	@Override
	public int compareTo(ExtendedShapeRoi o) {
		return (int) Math.round(diameter - o.diameter);
	}

	public boolean extendTo(ExtendedShapeRoi other) {
		float[] coords = new float[6];
		PathIterator iterator = roi.getPolygon().getPathIterator(null);
		Point minMine = new Point(Double.NaN, Double.NaN, Double.MAX_VALUE);
		Point minOther = new Point(Double.NaN, Double.NaN, Double.MAX_VALUE);
		while (!iterator.isDone()) {
			int type = iterator.currentSegment(coords);
			coords[0] += roi.getXBase();
			coords[1] += roi.getYBase();
			coords[2] += roi.getXBase();
			coords[3] += roi.getYBase();
			coords[4] += roi.getXBase();
			coords[5] += roi.getYBase();
			Point distance = new Point(Double.NaN, Double.NaN, Double.MAX_VALUE);
			switch (type) {
			case PathIterator.SEG_CUBICTO:
				distance = other.distanceTo(coords[4], coords[5]);
				if (distance.distance < minMine.distance) {
					minMine = distance;
					minOther = new Point(coords[4], coords[5], distance.distance);
				}
			case PathIterator.SEG_QUADTO:
				distance = other.distanceTo(coords[2], coords[3]);
				if (distance.distance < minMine.distance) {
					minMine = distance;
					minOther = new Point(coords[4], coords[5], distance.distance);
				}
			case PathIterator.SEG_LINETO:
			case PathIterator.SEG_MOVETO:
				distance = other.distanceTo(coords[0], coords[1]);
				if (distance.distance < minMine.distance) {
					minMine = distance;
					minOther = new Point(coords[4], coords[5], distance.distance);
				}
			}
			iterator.next();
		}
		double shortest = distanceTo(minMine.x, minMine.y, minOther.x, minOther.y);
		if (shortest < 40d) {
			OvalRoi e1 = new OvalRoi(minMine.x - 20d / 2d, minMine.y - 20d / 2d, //
					20, 20);
			OvalRoi e2 = new OvalRoi(minOther.x - 20d / 2d, minOther.y - 20d / 2d, //
					20, 20);
			OvalRoi e3 = new OvalRoi((minOther.x + minMine.x) / 2d - 20d / 2d, (minOther.y + minMine.y) / 2d - 20d / 2d, //
					20, 20);
			roi.or(new ShapeRoi(e1));
			roi.or(new ShapeRoi(e2));
			roi.or(new ShapeRoi(e3));
			roi.or(other.roi);
		}
		return false;
	}

	private Point distanceTo(double x, double y) {
		float[] coords = new float[6];
		PathIterator iterator = roi.getPolygon().getPathIterator(null);
		Point min = new Point(Double.NaN, Double.NaN, Double.MAX_VALUE);
		while (!iterator.isDone()) {
			int type = iterator.currentSegment(coords);
			coords[0] += roi.getXBase();
			coords[1] += roi.getYBase();
			coords[2] += roi.getXBase();
			coords[3] += roi.getYBase();
			coords[4] += roi.getXBase();
			coords[5] += roi.getYBase();
			double distance = Double.MAX_VALUE;
			switch (type) {
			case PathIterator.SEG_CUBICTO:
				distance = distanceTo(coords[4], coords[5], x, y);
				if (distance < min.distance) {
					min = new Point(coords[4], coords[5], distance);
				}
			case PathIterator.SEG_QUADTO:
				distance = distanceTo(coords[2], coords[3], x, y);
				if (distance < min.distance) {
					min = new Point(coords[2], coords[3], distance);
				}
			case PathIterator.SEG_LINETO:
			case PathIterator.SEG_MOVETO:
				distance = distanceTo(coords[0], coords[1], x, y);
				if (distance < min.distance) {
					min = new Point(coords[0], coords[1], distance);
				}
			}
			iterator.next();
		}
		return min;
	}

	private double distanceTo(double x1, double y1, double x2, double y2) {
		return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
	}

}
