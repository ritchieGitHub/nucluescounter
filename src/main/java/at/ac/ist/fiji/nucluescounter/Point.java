package at.ac.ist.fiji.nucluescounter;

public class Point {
	double x;
	double y;
	double distance;

	public Point(float x, float y, double distance) {
		this((double) x, (double) y, distance);
	}

	public Point(double x, double y, double distance) {
		super();
		this.x = x;
		this.y = y;
		this.distance = distance;
	}

}
