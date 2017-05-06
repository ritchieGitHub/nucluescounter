package at.ac.ist.fiji.dottersack;

import java.util.Arrays;

import at.ac.ist.fiji.dottersack.DottersacDetection.ValueRange;

public class Pattern {
	private final double[] features;

	public Pattern(double[] features) {
		this.features = features;
	}

	public double distance(Pattern other, ValueRange[] ranges) {
		double total = 0;
		double counter = 0;
		for (int index = 0; index < features.length; index++) {
			double current = this.features[index];
			double avaerage = other.features[index];
			if (!Double.isNaN(current) && !Double.isNaN(avaerage) && //
					current < 10d && //
					avaerage < 10d) { // ignore
										// some
										// values
				double diff = Math.abs(avaerage - current) / Math.abs(ranges[index].diff());

				total += diff;
				counter += 1.0;
			}
		}
		if (counter < 0) {
			return 0;
		}
		return total / counter;
	}

	@Override
	public String toString() {
		return Arrays.toString(features) + "\n";
	}

	public ValueRange[] createValueRange() {
		ValueRange[] valueRanges = new ValueRange[features.length];
		for (int index = 0; index < valueRanges.length; index++) {
			valueRanges[index] = new ValueRange().update(features[index]);
		}
		return valueRanges;
	}

	public void updateValueRange(ValueRange[] valueRanges) {
		for (int index = 0; index < valueRanges.length; index++) {
			valueRanges[index] = new ValueRange().update(features[index]);
		}
	}
}
