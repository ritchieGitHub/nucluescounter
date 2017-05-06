package at.ac.ist.fiji.nucluescounter;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import at.ac.ist.fiji.cellcounter.CellCounter;
import at.ac.ist.fiji.controladjust.ContrastAdjuster;
import at.ac.ist.fiji.dottersack.DottersacDetection;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.PlugIn;
import ij.plugin.RoiEnlarger;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class NucleusCounter implements PlugIn {
	private DottersacDetection dottersacDetection;

	@Override
	public void run(final String arg) {
		final int[] wList = WindowManager.getIDList();
		if (wList == null) {
			IJ.error("No images are open.");
			return;
		}

		final GenericDialog gd = new GenericDialog("Nucleus Counter Julia");
		gd.addCheckbox("use auto adjust", true);
		gd.addNumericField("Subtract", 50, 0);
		gd.addNumericField("Gaussian Blur", 0.6, 1);
		gd.addNumericField("Multiply", 2, 0);
		gd.addNumericField("Z-upper-limit 0=default", 0, 0);
		gd.addNumericField("Z-lower-limit 0=default", 0, 0);
		gd.addCheckbox("keep referencial area", false);
		gd.addCheckbox("detect germ band", false);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		boolean useAutoAdjust = gd.getNextBoolean();
		double subtractParam = gd.getNextNumber();
		double gaussianBlurParam = gd.getNextNumber();
		double multiplyParam = gd.getNextNumber();
		int zMin = (int) Math.round(gd.getNextNumber());
		int zMax = (int) Math.round(gd.getNextNumber());
		boolean keepReferencialArea = gd.getNextBoolean();
		boolean detectGermBand = gd.getNextBoolean();

		if (CellCounter.isInitialized()) {
			CellCounter.close();
		}
		IJ.run("Cell Counter Julia", " add");
		CellCounter.initializeCellImage();
		CellCounter.setType("1");
		ImagePlus imp1 = WindowManager.getCurrentImage();
		// exampleAnalysis(imp1, 30, 15);
		if (useAutoAdjust) {
			new ContrastAdjuster().run(true, false, gaussianBlurParam);
		}
		imp1.setC(1);
		int nSlices = imp1.getNSlices();
		for (int index = 1; index <= nSlices; index++) {
			imp1 = WindowManager.getCurrentImage();
			imp1.setZ(index);
			int currentSlice = imp1.getCurrentSlice();
			final ImageProcessor ip1 = imp1.getProcessor();
			if (imp1.getType() != ImagePlus.GRAY8 && imp1.getType() != ImagePlus.GRAY16) {
				IJ.showMessage("Error", "Plugin requires 8- or 16-bit image");
				return;
			}
			int minthreshold = (int) ip1.getMinThreshold();
			int maxthreshold = (int) ip1.getMaxThreshold();

			// fullStats=gd.getNextBoolean();
			// duplicate and bg subtract subtract BG
			final ImageProcessor ip2 = ip1.duplicate();

			new ImagePlus("Analysis", ip2).show();
			final ImagePlus imp2 = WindowManager.getCurrentImage();
			if (useAutoAdjust) {
				IJ.run(imp2, "Subtract...", "value=" + subtractParam);
				IJ.run(imp2, "Gaussian Blur...", "sigma=" + gaussianBlurParam);
				IJ.run(imp2, "Multiply...", "value=" + multiplyParam);
			} else {
				IJ.run(imp2, "Subtract...", "value=" + subtractParam);
				IJ.run(imp2, "Gaussian Blur...", "sigma=" + gaussianBlurParam);
				IJ.run(imp2, "Multiply...", "value=" + multiplyParam);
			}
			IJ.run(imp2, "Grays", "");

			// IJ.showMessage("phase1");
			final ImageWindow winimp2 = imp2.getWindow();

			// duplicate this for image thresholding
			final ImageProcessor ip3 = ip2.duplicate();
			ip3.setThreshold(minthreshold, maxthreshold, 4);
			new ImagePlus("Threshold", ip3).show();
			final ImagePlus imp3 = WindowManager.getCurrentImage();
			final ImageWindow winimp3 = imp3.getWindow();
			// IJ.showMessage("phase2");

			WindowManager.setCurrentWindow(winimp3);

			minthreshold = (int) ip3.getMinThreshold();
			maxthreshold = (int) ip3.getMaxThreshold();
			WindowManager.setCurrentWindow(winimp3);
			IJ.setThreshold(minthreshold, maxthreshold);

			// IJ.showMessage("phase3");

			IJ.run("Convert to Mask");
			// watershed

			IJ.run("Watershed");
			IJ.setThreshold(128, 255);

			// IJ.showMessage("phase4");

			final ImagePlus mask = WindowManager.getCurrentImage();

			// IJ.run("Analyze Particles...", "size=0.65-647.67
			// circularity=0.00-1.00 show=Outlines display exclude clear
			// summarize");

			// analyze particles
			String analyseStr = "size=9-800 circularity=0.00-2.00 show=Nothing exclude clear add";

			// IJ.showMessage("phase5");

			IJ.run("Analyze Particles...", analyseStr + " add");

			imp1.setSlice(currentSlice);
			WindowManager.setCurrentWindow(imp1.getWindow());

			for (Roi roi : RoiManager.getRoiManager().getRoisAsArray()) {
				double[] center = roi.getContourCentroid();
				CellCounter.add((int) center[0], (int) center[1], currentSlice);
			}
			RoiManager.getRoiManager().reset();
			imp2.changes = false;
			winimp2.close();
			WindowManager.setCurrentWindow(winimp3);
			IJ.setThreshold(minthreshold, maxthreshold);
			mask.changes = false;
			mask.close();
		}
		if (!detectGermBand) {
			return;
		}
		ImagePlus img = WindowManager.getCurrentImage();
		imp1.setC(2);
		// exampleAnalysis(img);

		if (zMin < 1) {
			zMin = nSlices / 4;
		}
		if (zMax < 1) {
			zMax = nSlices - zMin;
		}
		int windowWidth = getDottersacDetection().windowWidth();
		int halfWindowWidth = (int) (((double) windowWidth) / 2.5d);
		// exampleAnalysis(img, windowWidth, halfWindowWidth);
		List<ShapeRoi> rois = new ArrayList<>();
		// RoiManager.getRoiManager().setVisible(true);

		for (int index = zMin; index <= zMax; index++) {
			img.setZ(index);
			BufferedImage bufferedImage = img.getBufferedImage();
			int heigth = img.getHeight() - halfWindowWidth;
			int width = img.getWidth() - halfWindowWidth;
			for (int x = windowWidth; x < (width - windowWidth); x += halfWindowWidth) {
				for (int y = windowWidth; y < (heigth - windowWidth); y += halfWindowWidth) {
					if (isDotterSack(bufferedImage.getSubimage(x - halfWindowWidth, y - halfWindowWidth, windowWidth,
							windowWidth))) {
						addRoi(rois, windowWidth, halfWindowWidth, x, y);
					}
				}
			}
			RoiManager.getInstance().reset();
			for (ShapeRoi shapeRoi : rois) {
				RoiManager.getInstance().addRoi(shapeRoi);
			}
		}
		ShapeRoi biggest = rois.get(0);
		double boxSize = size(biggest);
		for (ShapeRoi shapeRoi : rois) {
			double shapeSize = size(shapeRoi);
			if (shapeSize > boxSize) {
				boxSize = shapeSize;
				biggest = shapeRoi;
			}
		}
		rois.remove(biggest);
		for (ShapeRoi shapeRoi : rois) {
			ShapeRoi test = new ShapeRoi(shapeRoi);
			RoiEnlarger.enlarge(test, 5d);
			if (test.and(biggest).getBounds().getWidth() > 0) {
				biggest.or(shapeRoi);
			}
		}
		RoiManager.getInstance().reset();
		RoiManager.getInstance().addRoi(biggest);

		double[] center = biggest.getContourCentroid();
		OvalRoi centerRoi = new OvalRoi(center[0] - 10, center[1] - 1, 20, 20);
		// RoiManager.getInstance().addRoi(centerRoi);

		double cutSize = biggest.getFeretsDiameter() / 3d;

		OvalRoi cutRoi = new OvalRoi(center[0] - cutSize / 2d, center[1] - cutSize / 2d, cutSize, cutSize);
		// RoiManager.getInstance().addRoi(cutRoi);

		ShapeRoi xorShape = new ShapeRoi(cutRoi).not(biggest);
		// RoiManager.getInstance().addRoi(xorShape);

		double[] contourCentroidOfXor = xorShape.getContourCentroid();
		OvalRoi secondCenterRoi = new OvalRoi(contourCentroidOfXor[0] - 10, contourCentroidOfXor[1] - 10, 20, 20);

		// RoiManager.getInstance().addRoi(secondCenterRoi);

		double deltax = secondCenterRoi.getXBase() - centerRoi.getXBase();
		double deltay = secondCenterRoi.getYBase() - centerRoi.getYBase();

		double width = cutSize * 2.5;
		OvalRoi master = new OvalRoi(centerRoi.getXBase() + deltax * 2d - width / 2d,
				centerRoi.getYBase() + deltay * 2d - width / 2d, //
				width, width);

		OvalRoi masterCenter = new OvalRoi(centerRoi.getXBase() + deltax * 2d - 20d / 2d,
				centerRoi.getYBase() + deltay * 2d - 20d / 2d, //
				20, 20);

		// RoiManager.getInstance().addRoi(master);

		Roi hitPart = null;
		Roi[] cuttedPartsOfRoi = new ShapeRoi(master).not(biggest).getRois();
		for (Roi part : cuttedPartsOfRoi) {
			if (part.contains((int) masterCenter.getXBase(), (int) masterCenter.getYBase())) {
				hitPart = part;
			}
		}
		if (hitPart == null) {
			hitPart = cuttedPartsOfRoi[0];
			int size = 0;
			// bad case but lets take the biggest one
			for (Roi part : cuttedPartsOfRoi) {
				int partSize = part.getContainedPoints().length;
				if (partSize > size) {
					partSize = size;
					hitPart = part;
				}
			}
		}
		// bigger and smaller again to make the line smoother
		Roi larger = RoiEnlarger.enlarge(hitPart, -15d);
		larger = RoiEnlarger.enlarge(larger, 30d);
		Roi type2 = RoiEnlarger.enlarge(hitPart, 15d);

		if (!keepReferencialArea) {
			RoiManager.getInstance().reset();
		}
		RoiManager.getInstance().addRoi(larger);

		CellCounter.checkInRegion(larger, type2);

		rois.clear();

	}

	private double size(ShapeRoi biggest) {
		Rectangle bounds = biggest.getBounds();
		return bounds.getWidth() * bounds.getHeight();
	}

	private void addRoi(List<ShapeRoi> rois, int windowWidth, int halfWindowWidth, int x, int y) {
		OvalRoi oval = new OvalRoi(x - halfWindowWidth, y - halfWindowWidth, //
				windowWidth, windowWidth);
		List<ShapeRoi> toutch = new ArrayList<>();
		for (ShapeRoi other : rois) {
			ShapeRoi shape = new ShapeRoi(oval);
			if (shape.and(other).getBounds().getWidth() > 0) {
				toutch.add(other);
			}
		}
		rois.removeAll(toutch);
		ShapeRoi shape = new ShapeRoi(oval);
		for (ShapeRoi other : toutch) {
			shape.or(other);
		}
		rois.add(shape);
	}

	private boolean isDotterSack(BufferedImage colorProcessor) {
		return getDottersacDetection().isDottersac2(colorProcessor);

	}

	DottersacDetection getDottersacDetection() {
		if (dottersacDetection == null) {
			dottersacDetection = new DottersacDetection();
		}
		return dottersacDetection;
	}

}
