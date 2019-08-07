package org.vadere.simulator.models.osm.optimization;

import java.awt.Shape;
import java.util.ArrayList;

import org.vadere.simulator.models.osm.PedestrianOSM;
import org.vadere.util.config.VadereConfig;
import org.vadere.util.geometry.shapes.VPoint;

/**
 * Abstract Base Class for StepCircleOptimizer.
 *
 * The abstract functions need to be implemented by every StepCircleOptimizer.
 * The additional functions only serve to compute a true solution (obtained via computationally expensive brute force).
 * This allows to compute a metric that measures the quality of the respective concrete subclass of StepCircleOptimizer.
 *
 * Currently, only the StepCircleOptimizerNelderMead uses the metric functionality. To compute the brute force
 * solution only the "computeAndAddBruteForceSolutionMetric" function has to be called (NOTE: depending on the setting
 * in "getReachablePositions" this can be very expensive.
 */
public abstract class StepCircleOptimizer {

	private boolean computeMetric;
    private ArrayList<OptimizationMetric> currentMetricValues;

	protected StepCircleOptimizer(){

		this.computeMetric = VadereConfig.getConfig().getBoolean("Testing.stepCircleOptimization.compareBruteForceSolution");

		if(this.computeMetric){
			this.currentMetricValues = new ArrayList<>();
		}else{
			this.currentMetricValues = null;
		}
	}

	/** Returns the reachable position with the minimal potential. */
	public abstract VPoint getNextPosition(PedestrianOSM pedestrian, Shape reachableArea);
	public abstract StepCircleOptimizer clone();


	/** The following functions are to compute the "true" optimal value via brute force. This allows to check the
	 * quality of a optimization algorithm.
	 */

	protected class SolutionPair {
		/* Inner data class to store point and function value. */
		public final VPoint point;
		public final double funcValue;
		public SolutionPair(VPoint point, double funcValue){
			this.point = point;
			this.funcValue = funcValue;
		}
	}

	protected boolean getIsComputeMetric(){
		return computeMetric;
	}

	protected void computeAndAddBruteForceSolutionMetric(final PedestrianOSM pedestrian,
														 final SolutionPair foundSolution){

        var bruteForceSolution = new
				StepCircleOptimizerDiscrete(0.0, null).computeBruteForceSolution(pedestrian);

        var optimizationMetric = new OptimizationMetric(pedestrian.getId(), pedestrian.getTimeOfNextStep(),
                bruteForceSolution.point, bruteForceSolution.funcValue, foundSolution.point, foundSolution.funcValue);

        currentMetricValues.add(optimizationMetric);
    }

	public ArrayList<OptimizationMetric> getCurrentMetricValues(){
		return this.currentMetricValues;
	}

	public void clearMetricValues(){
		this.currentMetricValues = new ArrayList<>();
	}
}
