package at.ac.ist.fiji.nucluescounter;

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
		return (int) Math.round(o.diameter - diameter);
	}

	public boolean extendTo(ExtendedShapeRoi other) {
		Point minMine = new Point(Double.NaN, Double.NaN, Double.MAX_VALUE);
		Point minOther = new Point(Double.NaN, Double.NaN, Double.MAX_VALUE);
		for (java.awt.Point point : roi.getContainedPoints()) {
			Point distance = new Point(Double.NaN, Double.NaN, Double.MAX_VALUE);
			distance = other.distanceTo(point.x, point.y);
			if (distance.distance < minMine.distance) {
				minMine = distance;
				minOther = new Point(point.x, point.y, distance.distance);
			}
		}
		double shortest = distanceTo(minMine.x, minMine.y, minOther.x, minOther.y);
		double circleDiameter = Math.max(20d, shortest / 2);
		if (shortest < 80d) {
			OvalRoi e1 = new OvalRoi(//
					minMine.x - circleDiameter / 2d, //
					minMine.y - circleDiameter / 2d, //
					circleDiameter, circleDiameter);
			OvalRoi e2 = new OvalRoi(//
					minOther.x - circleDiameter / 2d, //
					minOther.y - circleDiameter / 2d, //
					circleDiameter, circleDiameter);
			OvalRoi e3 = new OvalRoi(//
					(minOther.x + minMine.x) / 2d - circleDiameter / 2d, //
					(minOther.y + minMine.y) / 2d - circleDiameter / 2d, //
					circleDiameter, circleDiameter);
			roi.or(new ShapeRoi(e1));
			roi.or(new ShapeRoi(e2));
			roi.or(new ShapeRoi(e3));
			roi.or(other.roi);
			return true;
		}
		return false;
	}

	private Point distanceTo(double x, double y) {
		Point min = new Point(Double.NaN, Double.NaN, Double.MAX_VALUE);
		for (java.awt.Point point : roi.getContainedPoints()) {
			double distance = distanceTo(point.x, point.y, x, y);
			if (distance < min.distance) {
				min = new Point(point.x, point.y, distance);
			}
		}
		return min;
	}

	private double distanceTo(double x1, double y1, double x2, double y2) {
		return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
	}

}
