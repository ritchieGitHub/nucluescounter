package at.ac.ist.fiji.dottersack;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;

import de.lmu.ifi.dbs.jfeaturelib.features.Haralick;
import ij.process.ColorProcessor;

public class DottersacDetection {

	static class ValueRange {
		double min;
		double max;

		ValueRange update(double value) {
			if (!Double.isNaN(value)) {
				min = Math.min(min, value);
				max = Math.max(max, value);
			}
			return this;
		}

		public double diff() {
			return max - min;
		}
	}

	static class Pair {
		public Pair(Pattern first, Pattern second, ValueRange[] ranges) {
			this.first = first;
			this.second = second;
			distance = first.distance(second, ranges);
		}

		Pattern first;
		Pattern second;
		double distance;

		public Pair setSecond(Pattern second, ValueRange[] ranges) {
			this.second = second;
			distance = first.distance(second, ranges);
			return this;
		}
	}

	static class PatternGroup implements Iterable<Pattern> {
		ArrayList<Pattern> patterns = new ArrayList<Pattern>();
		ValueRange[] range = null;
		double maxDistance = -1.0;
		private double tolerance;

		public PatternGroup(List<Pattern> features) {
			patterns.addAll(features);
		}

		public PatternGroup() {
			// TODO Auto-generated constructor stub
		}

		void add(Pattern pattern) {
			patterns.add(pattern);
			maxDistance = -1;
		}

		double maxDistance() {
			if (maxDistance < 0) {
				range = patterns.get(0).createValueRange();
				for (Pattern pattern : patterns) {
					pattern.updateValueRange(range);
				}
				if (patterns.size() > 2) {
					tolerance = 0.0;
					double count = 0;
					Pair biggest = new Pair(patterns.get(0), patterns.get(1), range);
					for (Pattern pattern : patterns) {
						for (Pattern other : patterns) {
							Pair current = new Pair(pattern, other, range);
							if (current.distance > biggest.distance) {
								biggest = current;
							}
							tolerance += current.distance;
							count += 1.0;
						}
					}
					tolerance = tolerance / count;
					maxDistance = biggest.distance;
				} else if (patterns.size() > 1) {
					maxDistance = new Pair(patterns.get(0), patterns.get(1), range).distance;
					tolerance = maxDistance / 2.0;
				} else {
					maxDistance = 0.0;
					tolerance = maxDistance;
				}
				tolerance = Math.max(tolerance, 0.01);
			}
			return maxDistance;
		}

		public Pattern first() {
			return patterns.get(0);
		}

		public Pattern second() {
			return patterns.get(1);
		}

		@Override
		public Iterator<Pattern> iterator() {
			return new ArrayList<>(patterns).iterator();
		}

		public int size() {
			return patterns.size();
		}

		public void remove(Pattern first) {
			patterns.remove(first);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName())//
					.append('(')//
					.append(maxDistance())//
					.append(',')//
					.append('\n')//
					.append(patterns)//
					.append(')');

			return builder.toString();
		}

		public boolean matches(Pair start) {
			for (Pattern pattern : patterns) {
				if (start.setSecond(pattern, range).distance < tolerance()) {
					return true;
				}
			}
			return false;
		}

		private double tolerance() {
			maxDistance();
			return tolerance;
		}
	}

	List<Pattern> features = new ArrayList<>();
	List<Pattern> negativeFeatures = new ArrayList<>();
	List<PatternGroup> groups;
	ValueRange[] ranges;

	public DottersacDetection() {
		try {
			BufferedImage image = ImageIO.read(DottersacDetection.class.getResourceAsStream("/25x25_detection.png"));
			analyseExampleImage(image, 25);
		//	image = ImageIO.read(DottersacDetection.class.getResourceAsStream("/positiv.png"));
		//	analyseExampleImage(image, 5);
		//	image = ImageIO.read(DottersacDetection.class.getResourceAsStream("/positiv2.png"));
		//	analyseExampleImage(image, 5);

			image = ImageIO.read(DottersacDetection.class.getResourceAsStream("/negativeImage.png"));
			analyseExampleNegativeImage(image);
			image = ImageIO.read(DottersacDetection.class.getResourceAsStream("/negativeImage2.png"));
			analyseExampleNegativeImage(image);

			// analyseExampleImage(rotate(image, 90));
			// analyseExampleImage(rotate(image, 180));
		} catch (IOException e) {
			throw new RuntimeException("could not read muster");
		}
		ranges = this.features.get(0).createValueRange();
		for (Pattern feature : this.features) {
			feature.updateValueRange(ranges);
		}

		// List<PatternGroup> lists = new ArrayList<>();
		// lists.add(new PatternGroup(features));
		// groups = organizePatterns(lists);
		// List<PatternGroup> lists2 = new ArrayList<>();
		// lists2.add(new PatternGroup(negativeFeatures));
		// List<PatternGroup> groups2 = organizePatterns(lists2);
		// negativeFeatures.clear();
		// for (PatternGroup patternGroup : groups2) {
		// negativeFeatures.add(patternGroup.first());
		// }
		//
		// for (PatternGroup list : groups) {
		// System.out.println("----------------------------------------------------------");
		// System.out.println(list);
		//
		// }
	}

	private void analyseExampleImage(BufferedImage image, int step) throws IOException {
		int width = image.getWidth() - 25;
		int height = image.getHeight() - 25;
		for (int x = 0; x < width; x += step) {
			for (int y = 0; y < height; y += step) {
				BufferedImage subimage = image.getSubimage(x, y, 25, 25);
				int small = 25 - 1;
				if (subimage.getRGB(0, 0) != -16777216 || //
						subimage.getRGB(0, small) != -16777216 || //
						subimage.getRGB(small, 0) != -16777216 || //
						subimage.getRGB(small, small) != -16777216) {
					double[] features = calculate(subimage);
					this.features.add(new Pattern(features));
				}
			}
		}
	}

	private double[] calculate(BufferedImage subimage) {
		ColorProcessor imageh = new ColorProcessor(subimage);
		// initialize the descriptor
		Haralick descriptor = new Haralick();
		descriptor.setHaralickDist(1);
		// run the descriptor and extract the features
		descriptor.run(imageh);
		// System.out.println(descriptor.getDescription());
		// obtain the features
		List<double[]> features = descriptor.getFeatures();
		return features.get(0);
	}

	private void analyseExampleNegativeImage(BufferedImage image) throws IOException {
		int width = image.getWidth() - 25;
		int heigth = image.getHeight() - 25;
		for (int x = 0; x < width; x += 12) {
			for (int y = 0; y < heigth; y += 12) {
				BufferedImage subimage = image.getSubimage(x, y, 25, 25);
				if (subimage.getRGB(0, 0) != -16777216 || //
						subimage.getRGB(0, 24) != -16777216 || //
						subimage.getRGB(24, 0) != -16777216 || //
						subimage.getRGB(24, 24) != -16777216) {
					double[] features = calculate(subimage);
					this.negativeFeatures.add(new Pattern(features));
				}
			}
		}
	}

	private List<PatternGroup> organizePatterns(List<PatternGroup> lists) {
		while (splittAllBig(lists)) {
			;
		}
		double sum = 0.0;
		double max = 0.0;
		int count = 0;
		for (PatternGroup group : changeableIterator(lists)) {
			sum += group.maxDistance();
			if (max < group.maxDistance()) {
				max = group.maxDistance();
			}
			count++;
		}
		// all lists that have more than average distance
		double limit = 0.01;
		boolean anySplitt = true;
		while (anySplitt) {
			anySplitt = false;
			for (PatternGroup group : changeableIterator(lists)) {
				if (limit < group.maxDistance()) {
					PatternGroup firstList = new PatternGroup();
					PatternGroup secondList = new PatternGroup();
					divideInTwoGroups(group, firstList, secondList);
					lists.remove(group);
					lists.add(firstList);
					lists.add(secondList);
					anySplitt = true;
				}
			}
		}
		return lists;
	}

	private PatternGroup[] changeableIterator(List<PatternGroup> lists) {
		return lists.toArray(new PatternGroup[lists.size()]);
	}

	private boolean splittAllBig(List<PatternGroup> lists) {
		for (PatternGroup list : changeableIterator(lists)) {
			if (list.size() > 5) {
				PatternGroup firstList = new PatternGroup();
				PatternGroup secondList = new PatternGroup();
				divideInTwoGroups(list, firstList, secondList);
				lists.remove(list);
				lists.add(firstList);
				lists.add(secondList);
				return true;
			}
		}
		return false;
	}

	private void divideInTwoGroups(PatternGroup startFeatures, PatternGroup firstList, PatternGroup secondList) {
		Pair biggest = new Pair(startFeatures.first(), startFeatures.second(), ranges);
		for (Pattern pattern : startFeatures) {
			for (Pattern other : startFeatures) {
				Pair current = new Pair(pattern, other, ranges);
				if (current.distance > biggest.distance) {
					biggest = current;
				}
			}
		}
		startFeatures.remove(biggest.first);
		startFeatures.remove(biggest.second);
		firstList.add(biggest.first);
		secondList.add(biggest.second);
		for (Pattern pattern : startFeatures) {
			Pair first = new Pair(pattern, biggest.first, ranges);
			Pair second = new Pair(pattern, biggest.second, ranges);
			if (first.distance < second.distance) {
				firstList.add(pattern);
			} else {
				secondList.add(pattern);
			}
		}
	}

	public static void main(String[] args) {
		new DottersacDetection();
	}

	public int windowWidth() {
		return 25;
	}

	public boolean isDottersac(BufferedImage subimage) {
		double[] features = calculate(subimage);
		Pattern basePattern = new Pattern(features);
		Pair start = new Pair(basePattern, basePattern, ranges);
		for (PatternGroup group : groups) {
			if (group.matches(start)) {
				return true;
			}
		}
		return false;
	}

	public boolean isDottersac2(BufferedImage subimage) {
		double[] features = calculate(subimage);
		Pattern basePattern = new Pattern(features);
		Pair start = new Pair(basePattern, basePattern, ranges);
		double minimum = Double.MAX_VALUE;
		for (Pattern pattern : this.features) {
			start.setSecond(pattern, ranges);
			if (start.distance < minimum) {
				minimum = start.distance;
			}
		}
		double minimumNegative = Double.MAX_VALUE;
		for (Pattern pattern : negativeFeatures) {
			start.setSecond(pattern, ranges);
			if (start.distance < minimumNegative) {
				minimumNegative = start.distance;
			}
		}
		if (Math.abs(minimum - minimumNegative) < 0.01) {
			return false;
		}
		return minimum < minimumNegative;
	}
}
