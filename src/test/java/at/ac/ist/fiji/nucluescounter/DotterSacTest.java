package at.ac.ist.fiji.nucluescounter;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import at.ac.ist.fiji.dottersack.DottersacDetection;
import de.lmu.ifi.dbs.jfeaturelib.features.Haralick;
import ij.process.ColorProcessor;

public class DotterSacTest {
	static DottersacDetection dottersacDetection = new DottersacDetection();
	
	

	public static void main(String[] args) throws IOException {
		BufferedImage img = ImageIO.read(DottersacDetection.class.getResourceAsStream("/testImage.png"));
		Graphics2D createGraphics = img.createGraphics();
		
		List<Point> points= new ArrayList<>();

		int windowWidth = dottersacDetection.windowWidth();
		int halfWindowWidth = (int) (((double) windowWidth) / 2.5d);

		int heigth = img.getHeight() - halfWindowWidth;
		int width = img.getWidth() - halfWindowWidth;
		for (int x = windowWidth; x < (width - windowWidth); x += halfWindowWidth) {
			for (int y = windowWidth; y < (heigth - windowWidth); y += halfWindowWidth) {
				if (dottersacDetection.isDottersac2(
						img.getSubimage(x - halfWindowWidth, y - halfWindowWidth, windowWidth, windowWidth))) {
					points.add(new Point(x, y));
					System.out.println("sac");
				}
			}
		}
		for (Point point : points) {
			createGraphics.drawOval(point.x, point.y, windowWidth, windowWidth);
		}

		createGraphics.dispose();
		ImageIO.write(img, "png", new File("target/result.png"));
	}

	private static double[] calculate(BufferedImage image, int x, int y) {
		// grayscale(image);
		ColorProcessor imageh = new ColorProcessor(
				image.getSubimage(x, y, dottersacDetection.windowWidth(), dottersacDetection.windowWidth()));

		// initialize the descriptor
		Haralick descriptor = new Haralick();
		descriptor.setHaralickDist(10);

		// run the descriptor and extract the features
		descriptor.run(imageh);
		// System.out.println(descriptor.getDescription());
		// obtain the features
		List<double[]> features = descriptor.getFeatures();
		return features.get(0);
	}
}
