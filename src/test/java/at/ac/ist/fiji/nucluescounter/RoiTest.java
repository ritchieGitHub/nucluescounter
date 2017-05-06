package at.ac.ist.fiji.nucluescounter;

import java.awt.Point;

import ij.gui.OvalRoi;
import ij.gui.ShapeRoi;

public class RoiTest {
	public static void main(String[] args) {
		ShapeRoi first = new ShapeRoi(new OvalRoi(10, 10, 25, 25));
		ShapeRoi second = new ShapeRoi(new OvalRoi(30, 10, 25, 25));
		first.and(second);
		for (Point point:first.getContainedPoints()) {
			System.out.println(point);
		}
		System.out.println("----------------------------");
		ShapeRoi first2 = new ShapeRoi(new OvalRoi(10, 10, 25, 25));
		ShapeRoi second2 = new ShapeRoi(new OvalRoi(30, 10, 25, 25));
		first2.or(second2);
		for (Point point:first2.getContainedPoints()) {
			System.out.println(point);
		}
	}
}
