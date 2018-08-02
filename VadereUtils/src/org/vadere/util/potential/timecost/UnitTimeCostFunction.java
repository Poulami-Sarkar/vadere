package org.vadere.util.potential.timecost;

import org.vadere.util.geometry.shapes.VPoint;

/**
 * Provides unit (= 1) cost at every point in 2D space.
 * 
 */
public class UnitTimeCostFunction implements ITimeCostFunction {

	/**
	 * Returns one, independent of p.
	 * 
	 * @param p
	 *        point in space, ignored.
	 * @return one, independent of p.
	 */
	@Override
	public double costAt(VPoint p) {
		return 1;
	}

	@Override
	public void update() {}

	@Override
	public boolean needsUpdate() {
		return false;
	}

    @Override
    public ITimeCostFunction clone() {
        return new UnitTimeCostFunction();
    }

}
