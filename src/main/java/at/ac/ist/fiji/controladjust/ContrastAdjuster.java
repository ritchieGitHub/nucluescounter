package at.ac.ist.fiji.controladjust;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Undo;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import ij.process.StackStatistics;

/**
 * This plugin implements the Brightness/Contrast, Window/level and Color
 * Balance commands, all in the Image/Adjust sub-menu. It allows the user to
 * interactively adjust the brightness and contrast of the active image. It is
 * multi-threaded to provide a more responsive user interface.
 */
public class ContrastAdjuster {

	class MinMax {
		int min;
		int max;
		ImageStatistics stats;
	}

	public static final String LOC_KEY = "b&c.loc";
	static final long AUTO_THRESHOLD = 500000L;
	static final String[] channelLabels = { "Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "All" };
	static final String[] altChannelLabels = { "Channel 1", "Channel 2", "Channel 3", "Channel 4", "Channel 5",
			"Channel 6", "All" };
	static final int[] channelConstants = { 4, 2, 1, 3, 5, 6, 7 };
	static final String[] ranges = { "Automatic", "8-bit (0-255)", "10-bit (0-1023)", "12-bit (0-4095)",
			"15-bit (0-32767)", "16-bit (0-65535)" };

	ContrastPlot plot = new ContrastPlot();
	private static ContrastAdjuster instance;

	int minSliderValue = -1, maxSliderValue = -1, brightnessValue = -1, contrastValue = -1;
	int sliderRange = 256;
	boolean doAutoAdjust, doApplyLut;

	int previousImageID;
	int previousType;
	int previousSlice = 1;
	Object previousSnapshot;
	ImageJ ij;
	double min, max;
	double previousMin, previousMax;
	double defaultMin, defaultMax;
	int contrast, brightness;
	boolean done;
	int y = 0;
	int channels = 7; // RGB

	public void run(boolean doAutoAdjust, boolean doApplyLut, double gaussianBlurParam) {
		this.doAutoAdjust = doAutoAdjust;
		this.doApplyLut = doApplyLut;
		instance = this;
		IJ.register(ContrastAdjuster.class);

		ij = IJ.getInstance();

		// plot
		y = 0;

		setup();
		doUpdate(gaussianBlurParam);
	}

	private void setup() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp != null) {
			setup(imp);
			updatePlot();
			imp.updateAndDraw();
		}
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		minSliderValue = 0;
		maxSliderValue = 255;
		contrastValue = 255;
		brightnessValue = 255;
	}

	public synchronized void actionPerformed(ActionEvent e) {
		if (1 == 1)
			doAutoAdjust = true;
		else if (1 == 1)
			doApplyLut = true;
	}

	private ImageProcessor setup(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		int type = imp.getType();
		int slice = imp.getCurrentSlice();
		boolean snapshotChanged = previousSnapshot != null
				&& ((ColorProcessor) ip).getSnapshotPixels() != previousSnapshot;
		if (imp.getID() != previousImageID || snapshotChanged || type != previousType || slice != previousSlice)
			setupNewImage(imp, ip);
		previousImageID = imp.getID();
		previousType = type;
		previousSlice = slice;
		return ip;
	}

	private void setupNewImage(ImagePlus imp, ImageProcessor ip) {
		// IJ.write("setupNewImage");
		Undo.reset();
		previousMin = min;
		previousMax = max;
		previousSnapshot = null;
		double min2 = imp.getDisplayRangeMin();
		double max2 = imp.getDisplayRangeMax();
		if (imp.getType() == ImagePlus.COLOR_RGB) {
			min2 = 0.0;
			max2 = 255.0;
		}
		if ((ip instanceof ShortProcessor) || (ip instanceof FloatProcessor)) {
			imp.resetDisplayRange();
			defaultMin = imp.getDisplayRangeMin();
			defaultMax = imp.getDisplayRangeMax();
		} else {
			defaultMin = 0;
			defaultMax = 255;
		}
		setMinAndMax(imp, min2, max2);
		min = imp.getDisplayRangeMin();
		max = imp.getDisplayRangeMax();
		if (IJ.debugMode) {
			IJ.log("min: " + min);
			IJ.log("max: " + max);
			IJ.log("defaultMin: " + defaultMin);
			IJ.log("defaultMax: " + defaultMax);
		}
		plot.defaultMin = defaultMin;
		plot.defaultMax = defaultMax;
		// plot.histogram = null;
		int valueRange = (int) (defaultMax - defaultMin);
		int newSliderRange = valueRange;
		if (newSliderRange > 640 && newSliderRange < 1280)
			newSliderRange /= 2;
		else if (newSliderRange >= 1280)
			newSliderRange /= 5;
		if (newSliderRange < 256)
			newSliderRange = 256;
		if (newSliderRange > 1024)
			newSliderRange = 1024;
		double displayRange = max - min;
		if (valueRange >= 1280 && valueRange != 0 && displayRange / valueRange < 0.25)
			newSliderRange *= 1.6666;
		// IJ.log(valueRange+" "+displayRange+" "+newSliderRange);
		if (newSliderRange != sliderRange) {
			sliderRange = newSliderRange;
		}
		if (imp.isComposite())
			IJ.setKeyUp(KeyEvent.VK_SHIFT);
	}

	private void setMinAndMax(ImagePlus imp, double min, double max) {
		imp.setDisplayRange(min, max);
		imp.getProcessor().setSnapshotPixels(null); // disable undo
	}

	private void updatePlot() {
		plot.min = min;
		plot.max = max;
		plot.repaint();
	}

	private void adjustMin(ImagePlus imp, ImageProcessor ip, double minvalue) {
		min = defaultMin + minvalue * (defaultMax - defaultMin) / (sliderRange - 1.0);
		if (max > defaultMax)
			max = defaultMax;
		if (min > max)
			max = min;
		setMinAndMax(imp, min, max);
		if (min == max)
			setThreshold(ip);
	}

	private void adjustMax(ImagePlus imp, ImageProcessor ip, double maxvalue) {
		max = defaultMin + maxvalue * (defaultMax - defaultMin) / (sliderRange - 1.0);
		// IJ.log("adjustMax: "+maxvalue+" "+max);
		if (min < defaultMin)
			min = defaultMin;
		if (max < min)
			min = max;
		setMinAndMax(imp, min, max);
		if (min == max)
			setThreshold(ip);
	}

	private void adjustBrightness(ImagePlus imp, ImageProcessor ip, double bvalue) {
		double center = defaultMin + (defaultMax - defaultMin) * ((sliderRange - bvalue) / sliderRange);
		double width = max - min;
		min = center - width / 2.0;
		max = center + width / 2.0;
		setMinAndMax(imp, min, max);
		if (min == max)
			setThreshold(ip);
	}

	private void adjustContrast(ImagePlus imp, ImageProcessor ip, int cvalue) {
		double slope;
		double center = min + (max - min) / 2.0;
		double range = defaultMax - defaultMin;
		double mid = sliderRange / 2;
		if (cvalue <= mid)
			slope = cvalue / mid;
		else
			slope = mid / (sliderRange - cvalue);
		if (slope > 0.0) {
			min = center - (0.5 * range) / slope;
			max = center + (0.5 * range) / slope;
		}
		setMinAndMax(imp, min, max);
	}

	private void reset(ImagePlus imp) {
		min = defaultMin;
		max = defaultMax;
	}

	private void plotHistogram(ImagePlus imp) {
		ImageStatistics stats;
		int range = imp.getType() == ImagePlus.GRAY16 ? ImagePlus.getDefault16bitRange() : 0;
		if (range != 0 && imp.getProcessor().getMax() == Math.pow(2, range) - 1
				&& !(imp.getCalibration().isSigned16Bit())) {
			ImagePlus imp2 = new ImagePlus("Temp", imp.getProcessor());
			stats = new StackStatistics(imp2, 256, 0, Math.pow(2, range));
		} else
			stats = imp.getStatistics();
		Color color = Color.gray;
		if (imp.isComposite())
			color = ((CompositeImage) imp).getChannelColor();
		plot.setHistogram(stats, color);
	}

	private void applyOne(ImagePlus imp, int min, int max) {
		int[] table = new int[256];
		for (int i = 0; i < 256; i++) {
			if (i <= min)
				table[i] = 0;
			else if (i >= max)
				table[i] = 255;
			else {
				double relativeValue = (double) (i - min) / (max - min);
				if (relativeValue > 0.7) {
					relativeValue = Math.max(relativeValue * 1.5, 1.0);
				} else if (relativeValue > 0.6) {
					relativeValue = Math.max(relativeValue * 1.45, 1.0);
				} else if (relativeValue > 0.5) {
					relativeValue = Math.max(relativeValue * 1.40, 1.0);
				} else if (relativeValue > 0.4) {
					relativeValue = Math.max(relativeValue * 1.30, 1.0);
				} else if (relativeValue > 0.3) {
					relativeValue = Math.max(relativeValue * 1.20, 1.0);
				} else if (relativeValue > 0.2) {
					relativeValue = Math.max(relativeValue * 1.10, 1.0);
				}
				table[i] = (int) (relativeValue * 255);
			}
		}
		ImageProcessor ip = imp.getProcessor();
		ip.applyTable(table);
		imp.changes = true;
	}

	private void apply(ImagePlus imp) {
		int[] table = new int[256];
		int min = (int) imp.getDisplayRangeMin();
		int max = (int) imp.getDisplayRangeMax();
		for (int i = 0; i < 256; i++) {
			if (i <= min)
				table[i] = 0;
			else if (i >= max)
				table[i] = 255;
			else
				table[i] = (int) (((double) (i - min) / (max - min)) * 255);
		}
		int current = imp.getCurrentSlice();
		ImageProcessor mask = imp.getMask();
		for (int i = 1; i <= imp.getStackSize(); i++) {
			imp.setSlice(i);
			ImageProcessor ip = imp.getProcessor();
			if (mask != null)
				ip.snapshot();
			ip.applyTable(table);
			ip.reset(mask);
		}
		imp.setSlice(current);
		reset(imp);
		imp.changes = true;
		imp.unlock();
	}

	private void setThreshold(ImageProcessor ip) {
		if (!(ip instanceof ByteProcessor))
			return;
		if (((ByteProcessor) ip).isInvertedLut())
			ip.setThreshold(max, 255, ImageProcessor.NO_LUT_UPDATE);
		else
			ip.setThreshold(0, max, ImageProcessor.NO_LUT_UPDATE);
	}

	private void autoAdjust(ImagePlus imp, double gaussianBlurParam) {
		MinMax[] minmaxes = new MinMax[imp.getNSlices() + 1];
		long[] pixelcount = new long[1];
		long[] sumHistogram = new long[256];
		for (int i = 1; i <= imp.getNSlices(); i++) {
			imp.setZ(i);
			IJ.run(imp, "Subtract...", "value=9");
			if (gaussianBlurParam > 0.1) {
				IJ.run(imp, "Gaussian Blur...", "sigma=" + gaussianBlurParam);
			}
		}
		byte[] beforeLastImage = null;
		byte[] lastImage = null;
		for (int i = 1; i <= imp.getNSlices() + 1; i++) {
			byte[] current;
			if (i <= imp.getNSlices()) {
				imp.setZ(i);
				current = (byte[]) imp.getProcessor().getPixels();
			} else {
				current = new byte[lastImage.length];
			}
			if (lastImage == null) {
				lastImage = new byte[current.length];
			}
			if (beforeLastImage != null) {
				for (int index = 0; index < current.length; index++) {
					int currentPixel = current[index] & 0xff;
					int beforeLastPixel = beforeLastImage[index] & 0xff;
					int lastPixel = lastImage[index] & 0xff;
					if (currentPixel > 64 || beforeLastPixel > 64) {
						int pixel = lastPixel //
								- currentPixel / 2 //
								- beforeLastPixel / 2;
						lastImage[index] = (byte) Math.max(0, pixel);
					}
				}
			}
			beforeLastImage = lastImage;
			lastImage = current;
		}
		for (int i = 1; i <= imp.getNSlices(); i++) {
			imp.setZ(i);
			IJ.run(imp, "Gaussian Blur...", "sigma=" + gaussianBlurParam);
			minmaxes[i] = calculateMinMax(imp, sumHistogram, pixelcount);
		}
		int limit = (int) (pixelcount[0] / (10L * imp.getNSlices()));
		int threshold = (int) (pixelcount[0] / (AUTO_THRESHOLD * imp.getNSlices()));

		for (int i = 1; i <= imp.getNSlices(); i++) {
			imp.setZ(i);
			MinMax minMax = minmaxes[i];
			basicMinMax(minMax, limit, minMax.stats.histogram, threshold);
			if (minMax.max >= minMax.min) {
				min = minMax.stats.histMin + minMax.min * minMax.stats.binSize;
				max = minMax.stats.histMin + minMax.max * minMax.stats.binSize;
				if (min == max) {
					min = minMax.stats.min;
					max = minMax.stats.max;
				}
				applyOne(imp, (int) min, (int) max);
			} else {
				applyOne(imp, (int) 255, (int) 255);
			}
		}
	}

	private MinMax calculateMinMax(ImagePlus imp, long[] sumHistogram, long[] pixelCount) {
		MinMax minMax = new MinMax();
		Calibration cal = imp.getCalibration();
		imp.setCalibration(null);
		minMax.stats = imp.getStatistics(); // get uncalibrated stats
		imp.setCalibration(cal);
		int limit = minMax.stats.pixelCount / 10;
		int[] histogram = minMax.stats.histogram;
		for (int i = 0; i < histogram.length; i++) {
			sumHistogram[i] += histogram[i];
		}
		int threshold = (int) (minMax.stats.pixelCount / AUTO_THRESHOLD);
		pixelCount[0] += minMax.stats.pixelCount;
		return basicMinMax(minMax, limit, histogram, threshold);
	}

	private MinMax basicMinMax(MinMax minMax, int limit, int[] histogram, int threshold) {
		int i = -1;
		boolean found = false;
		int count;
		do {
			i++;
			count = histogram[i];
			if (count > limit)
				count = 0;
			found = count > threshold;
		} while (!found && i < 255);
		minMax.min = i;
		i = 256;
		do {
			i--;
			count = histogram[i];
			if (count > limit)
				count = 0;
			found = count > threshold;
		} while (!found && i > 0);
		minMax.max = i;
		return minMax;
	}

	static final int RESET = 0, AUTO = 1, SET = 2, APPLY = 3, THRESHOLD = 4, MIN = 5, MAX = 6, BRIGHTNESS = 7,
			CONTRAST = 8, UPDATE = 9;

	void doUpdate(double gaussianBlurParam) {
		ImagePlus imp;
		imp = WindowManager.getCurrentImage();
		imp.setC(1);
		if (doAutoAdjust) {
			autoAdjust(imp, gaussianBlurParam);
		}
		if (doApplyLut) {
			apply(imp);
		}
		updatePlot();
		imp.updateChannelAndDraw();
	}

	/** Resets this ContrastAdjuster and brings it to the front. */
	public void updateAndDraw() {
		previousImageID = 0;
	}

	/** Updates the ContrastAdjuster. */
	public static void update() {
		if (instance != null) {
			ContrastAdjuster ca = ((ContrastAdjuster) instance);
			ca.previousImageID = 0;
			ca.setup();
		}
	}

} // ContrastAdjuster class

class ContrastPlot extends Canvas implements MouseListener {

	static final int WIDTH = 128, HEIGHT = 64;
	double defaultMin = 0;
	double defaultMax = 255;
	double min = 0;
	double max = 255;
	int[] histogram;
	int hmax;
	Image os;
	Graphics osg;
	Color color = Color.gray;

	public ContrastPlot() {
		addMouseListener(this);
		setSize(WIDTH + 1, HEIGHT + 1);
	}

	/**
	 * Overrides Component getPreferredSize(). Added to work around a bug in
	 * Java 1.4.1 on Mac OS X.
	 */
	public Dimension getPreferredSize() {
		return new Dimension(WIDTH + 1, HEIGHT + 1);
	}

	void setHistogram(ImageStatistics stats, Color color) {
		this.color = color;
		histogram = stats.histogram;
		if (histogram.length != 256) {
			histogram = null;
			return;
		}
		for (int i = 0; i < 128; i++)
			histogram[i] = (histogram[2 * i] + histogram[2 * i + 1]) / 2;
		int maxCount = 0;
		int mode = 0;
		for (int i = 0; i < 128; i++) {
			if (histogram[i] > maxCount) {
				maxCount = histogram[i];
				mode = i;
			}
		}
		int maxCount2 = 0;
		for (int i = 0; i < 128; i++) {
			if ((histogram[i] > maxCount2) && (i != mode))
				maxCount2 = histogram[i];
		}
		hmax = stats.maxCount;
		if ((hmax > (maxCount2 * 2)) && (maxCount2 != 0)) {
			hmax = (int) (maxCount2 * 1.5);
			histogram[mode] = hmax;
		}
		os = null;
	}

	public void update(Graphics g) {
		paint(g);
	}

	public void paint(Graphics g) {
		int x1, y1, x2, y2;
		double scale = (double) WIDTH / (defaultMax - defaultMin);
		double slope = 0.0;
		if (max != min)
			slope = HEIGHT / (max - min);
		if (min >= defaultMin) {
			x1 = (int) (scale * (min - defaultMin));
			y1 = HEIGHT;
		} else {
			x1 = 0;
			if (max > min)
				y1 = HEIGHT - (int) ((defaultMin - min) * slope);
			else
				y1 = HEIGHT;
		}
		if (max <= defaultMax) {
			x2 = (int) (scale * (max - defaultMin));
			y2 = 0;
		} else {
			x2 = WIDTH;
			if (max > min)
				y2 = HEIGHT - (int) ((defaultMax - min) * slope);
			else
				y2 = 0;
		}
		if (histogram != null) {
			if (os == null && hmax != 0) {
				os = createImage(WIDTH, HEIGHT);
				osg = os.getGraphics();
				osg.setColor(Color.white);
				osg.fillRect(0, 0, WIDTH, HEIGHT);
				osg.setColor(color);
				for (int i = 0; i < WIDTH; i++)
					osg.drawLine(i, HEIGHT, i, HEIGHT - ((int) (HEIGHT * histogram[i]) / hmax));
				osg.dispose();
			}
			if (os != null)
				g.drawImage(os, 0, 0, this);
		} else {
			g.setColor(Color.white);
			g.fillRect(0, 0, WIDTH, HEIGHT);
		}
		g.setColor(Color.black);
		g.drawLine(x1, y1, x2, y2);
		g.drawLine(x2, HEIGHT - 5, x2, HEIGHT);
		g.drawRect(0, 0, WIDTH, HEIGHT);
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

} // ContrastPlot class
