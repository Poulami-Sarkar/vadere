package org.vadere.simulator.models.osm;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vadere.simulator.models.potential.combinedPotentials.CombinedPotentialStrategy;
import org.vadere.state.attributes.scenario.AttributesAgent;
import org.vadere.state.psychology.cognition.SelfCategory;
import org.vadere.state.psychology.perception.types.Bang;
import org.vadere.state.psychology.perception.types.ChangeTarget;
import org.vadere.state.psychology.perception.types.Stimulus;
import org.vadere.state.scenario.Pedestrian;
import org.vadere.state.scenario.Target;
import org.vadere.state.scenario.Topography;
import org.vadere.state.simulation.FootStep;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.Vector2D;
import org.vadere.util.logging.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A class to encapsulate the behavior of a single {@link PedestrianOSM}.
 *
 * This class can be used by {@link OptimalStepsModel} to react to
 * environmental stimuli (see {@link Stimulus}) and how an agent
 * has categorized itself in regard to other agents (see {@link SelfCategory}).
 *
 * For instance:
 * <pre>
 *     ...
 *     if (mostImportantStimulus instanceof Wait) {
 *         osmBehaviorController.wait()
 *     }
 * 	   ...
 * </pre>
 */
public class OSMBehaviorController {

    private static Logger logger = Logger.getLogger(OSMBehaviorController.class);

    /**
     * Prepare move of pedestrian inside the topography. The pedestrian object already has the new
     * location (Vpoint to) stored within its position attribute. This method only informs the
     * topography object of the change in state.
     *
     * !IMPORTANT! this function calls movePedestrian which must be called ONLY ONCE  for each
     * pedestrian for each position. To  allow preformat selection of a pedestrian the managing
     * destructure is not idempotent (cannot be applied multiple time without changing result).
     *
     * @param topography 	manages simulation data
     * @param pedestrian	moving pedestrian. This object's position is already set.
     * @param stepTime		time in seconds used for the step.
     */
    public void makeStep(@NotNull final PedestrianOSM pedestrian, @NotNull final Topography topography, final double stepTime) {
        VPoint currentPosition = pedestrian.getPosition();
        VPoint nextPosition = pedestrian.getNextPosition();

        // start time
        double stepStartTime = pedestrian.getTimeOfNextStep();

        // end time
        double stepEndTime = pedestrian.getTimeOfNextStep() + pedestrian.getDurationNextStep();

        assert stepEndTime >= stepStartTime && stepEndTime >= 0.0 && stepStartTime >= 0.0 : stepEndTime + "<" + stepStartTime;

        if (nextPosition.equals(currentPosition)) {
            pedestrian.setTimeCredit(0);
            pedestrian.setVelocity(new Vector2D(0, 0));

        } else {
            pedestrian.setTimeCredit(pedestrian.getTimeCredit() - pedestrian.getDurationNextStep());

            pedestrian.setPosition(nextPosition);
            synchronized (topography) {
                topography.moveElement(pedestrian, currentPosition);
            }

            // compute velocity by forward difference
            Vector2D pedVelocity = new Vector2D(nextPosition.x - currentPosition.x, nextPosition.y - currentPosition.y).multiply(1.0 / stepTime);
            pedestrian.setVelocity(pedVelocity);
        }

        // strides and foot steps have no influence on the simulation itself, i.e. they are saved to analyse trajectories
        pedestrian.getStrides().add(Pair.of(currentPosition.distance(nextPosition), stepStartTime));

        FootStep currentFootstep = new FootStep(currentPosition, nextPosition, stepStartTime, stepEndTime);
        pedestrian.getTrajectory().add(currentFootstep);
        pedestrian.getFootstepHistory().add(currentFootstep);
    }

    public void wait(PedestrianOSM pedestrian, double timeStepInSec) {
        // Satisfy event-driven and sequential update scheme.
        pedestrian.setTimeOfNextStep(pedestrian.getTimeOfNextStep() + timeStepInSec);
        pedestrian.setTimeCredit(0);
    }

    // Watch out: A bang event changes only the "CombinedPotentialStrategy".
    // I.e., a new target is set for the agent. The agent does not move here!
    // Therefore, trigger only a single bang event and then use "ElapsedTime" afterwards
    // to let the agent walk.
    public void reactToBang(PedestrianOSM pedestrian, Topography topography) {
        Stimulus mostImportantStimulus = pedestrian.getMostImportantStimulus();

        if (mostImportantStimulus instanceof Bang) {
            Bang bang = (Bang) pedestrian.getMostImportantStimulus();
            Target bangOrigin = topography.getTarget(bang.getOriginAsTargetId());

            LinkedList<Integer> nextTarget = new LinkedList<>();
            nextTarget.add(bangOrigin.getId());

            pedestrian.setTargets(nextTarget);
            pedestrian.setCombinedPotentialStrategy(CombinedPotentialStrategy.TARGET_DISTRACTION_STRATEGY);
        } else {
            logger.debug(String.format("Expected: %s, Received: %s"),
                    Bang.class.getSimpleName(),
                    mostImportantStimulus.getClass().getSimpleName());
        }
    }

    public void reactToTargetChange(PedestrianOSM pedestrian, Topography topography) {
        Stimulus mostImportantStimulus = pedestrian.getMostImportantStimulus();

        if (mostImportantStimulus instanceof ChangeTarget) {
            ChangeTarget changeTarget = (ChangeTarget) pedestrian.getMostImportantStimulus();
            pedestrian.setTargets(changeTarget.getNewTargetIds());

        } else {
            logger.debug(String.format("Expected: %s, Received: %s"),
                    ChangeTarget.class.getSimpleName(),
                    mostImportantStimulus.getClass().getSimpleName());
        }
    }

    // TODO: Write unit tests for the critical methods below!
    @Nullable
    public PedestrianOSM findSwapCandidate(PedestrianOSM pedestrian, Topography topography) {
        // Agents with no targets don't want to swap places.
        if (pedestrian.hasNextTarget() == false) {
            return null;
        }

        List<Pedestrian> closestPedestrians = getClosestPedestriansWhichAreCloserToTarget(pedestrian, topography);

        if (closestPedestrians.size() > 0) {
            for (Pedestrian closestPedestrian : closestPedestrians) {
                if (closestPedestrian.hasNextTarget()) {
                    boolean closestPedIsCooperative = closestPedestrian.getSelfCategory() == SelfCategory.COOPERATIVE;
                    boolean targetOrientationDiffers = false;

                    // TODO: Make both options configurable in JSON file.
                    // double angleInRadian = calculateAngleBetweenTargets(pedestrian, closestPedestrian, topography);
                    double angleInRadian = calculateAngleBetweenTargetGradients(pedestrian, (PedestrianOSM)closestPedestrian);

                    if (angleInRadian == -1 || Math.toDegrees(angleInRadian) > pedestrian.getAttributes().getTargetOrientationAngleThreshold()) {
                        targetOrientationDiffers = true;
                    }

                    if (closestPedIsCooperative && targetOrientationDiffers) {
                        return (PedestrianOSM)closestPedestrian;
                    }
                } else {
                    return (PedestrianOSM)closestPedestrian;
                }
            }
        }

        return null;
    }

    @NotNull
    private List<Pedestrian> getClosestPedestriansWhichAreCloserToTarget(PedestrianOSM pedestrian, Topography topography) {
        VPoint positionOfPedestrian = pedestrian.getPosition();

        List<Pedestrian> closestPedestrians = topography.getSpatialMap(Pedestrian.class)
                .getObjects(positionOfPedestrian, pedestrian.getAttributes().getSearchRadius());

        // Filter out "me" and pedestrians which are further away from target than "me".
        closestPedestrians = closestPedestrians.stream()
                .filter(candidate -> pedestrian.getId() != candidate.getId())
                .filter(candidate -> pedestrian.getTargetPotential(candidate.getPosition()) < pedestrian.getTargetPotential(pedestrian.getPosition()))
                .collect(Collectors.toList());

        // Sort by distance away from "me".
        closestPedestrians = closestPedestrians.stream()
                .sorted((pedestrian1, pedestrian2) ->
                        Double.compare(
                                positionOfPedestrian.distance(pedestrian1.getPosition()),
                                positionOfPedestrian.distance(pedestrian2.getPosition())
                        ))
                .collect(Collectors.toList());

        return closestPedestrians;
    }

    /**
     * Calculate the angle between the two vectors v1 and v2 where
     * v1 = (TargetPedestrian1 - pedestrian1) and v2 = (TargetPedestrian2 - pedestrian2):
     *
     * <pre>
     *     T2 o   o T1
     *        ^   ^
     *         \a/
     *          x
     *         / \
     *     P1 o   o P2
     *
     *     T1: target of pedestrian 1
     *     T2: target of pedestrian 2
     *     P1: pedestrian 1
     *     P2: pedestrian 2
     *     a : angle between the two vectors
     * </pre>
     *
     * This is required to decide if pedestrian1 and pedestrian2 can be swapped because they have different walking
     * directions.
     *
     * @return An angle between 0 and <i>pi</i> radian or -1 if at least one of the given pedestrians has no target.
     */
    public double calculateAngleBetweenTargets(Pedestrian pedestrian1, Pedestrian pedestrian2, Topography topography) {
        double angleInRadian = -1;

        if (pedestrian1.hasNextTarget() && pedestrian2.hasNextTarget()) {
            Target targetPed1 = topography.getTarget(pedestrian1.getNextTargetId());
            Target targetPed2 = topography.getTarget(pedestrian2.getNextTargetId());

            VPoint targetVectorPed1 = calculateVectorPedestrianToTarget(pedestrian1, targetPed1);
            VPoint targetVectorPed2 = calculateVectorPedestrianToTarget(pedestrian2, targetPed2);

            double dotProduct = targetVectorPed1.dotProduct(targetVectorPed2);
            double multipliedMagnitudes = targetVectorPed1.distanceToOrigin() * targetVectorPed2.distanceToOrigin();

            angleInRadian = Math.acos(dotProduct / multipliedMagnitudes);
        }

        return angleInRadian;
    }

    private VPoint calculateVectorPedestrianToTarget(Pedestrian pedestrian, Target target) {
        VPoint vectorPedestrianToTarget = null;

        if (pedestrian.getAttributes().getAngleCalculationType() == AttributesAgent.AngleCalculationType.USE_CENTER) {
            vectorPedestrianToTarget = target.getShape().getCentroid().subtract(pedestrian.getPosition());
        } else if (pedestrian.getAttributes().getAngleCalculationType() == AttributesAgent.AngleCalculationType.USE_CLOSEST_POINT) {
            VPoint closestTargetPoint = target.getShape().closestPoint(pedestrian.getPosition());
            vectorPedestrianToTarget = closestTargetPoint.subtract(pedestrian.getPosition());
        } else {
            throw new IllegalArgumentException(String.format("Unsupported angle calculation type: \"%s\"", pedestrian.getAttributes().getAngleCalculationType()));
        }

        return vectorPedestrianToTarget;
    }

    public double calculateAngleBetweenTargetGradients(PedestrianOSM pedestrian1, PedestrianOSM pedestrian2) {
        double angleInRadian = -1;

        Vector2D targetGradientPedestrian1 = pedestrian1.getTargetGradient(pedestrian1.getPosition());
        Vector2D targetGradientPedestrian2 = pedestrian2.getTargetGradient(pedestrian2.getPosition());

        double dotProduct = targetGradientPedestrian1.dotProduct(targetGradientPedestrian2);
        double multipliedMagnitudes = targetGradientPedestrian1.distanceToOrigin() * targetGradientPedestrian2.distanceToOrigin();

        angleInRadian = Math.acos(dotProduct / multipliedMagnitudes);

        return angleInRadian;
    }

    /**
     * Swap two pedestrians.
     *
     * Watch out: This method manipulates pedestrian2 which is contained in a queue
     * sorted by timeOfNextStep! The calling code must re-add pedestrian2 after
     * invoking this method.
     */
    public void swapPedestrians(PedestrianOSM pedestrian1, PedestrianOSM pedestrian2, Topography topography) {
        VPoint newPosition = pedestrian2.getPosition().clone();
        VPoint oldPosition = pedestrian1.getPosition().clone();

        pedestrian1.setNextPosition(newPosition);
        pedestrian2.setNextPosition(oldPosition);

        // Synchronize movement of both pedestrians
        double startTimeStep = pedestrian1.getTimeOfNextStep();
        double durationStep = Math.max(pedestrian1.getDurationNextStep(), pedestrian2.getDurationNextStep());
        double endTimeStep = startTimeStep + durationStep;

        // We interrupt the current footstep of pedestrian 2 to sync it with
        // pedestrian 1. It is only required for the sequential update scheme
        // since pedestrian 2 might have done some steps in this time step and
        // is ahead (with respect to the time) of pedestrian 1.
        // We remove those steps which is not a good solution!
        if(!pedestrian2.getTrajectory().isEmpty()) {
            pedestrian2.getTrajectory().adjustEndTime(startTimeStep);
        }

        pedestrian1.setTimeOfNextStep(startTimeStep);
        pedestrian2.setTimeOfNextStep(startTimeStep);

        makeStep(pedestrian1, topography, durationStep);
        makeStep(pedestrian2, topography, durationStep);

        pedestrian1.setTimeOfNextStep(endTimeStep);
        pedestrian2.setTimeOfNextStep(endTimeStep);

        // TODO:
        //  "makeStep()" already invokes
        //  "pedestrian.setTimeCredit(pedestrian.getTimeCredit() - pedestrian.getDurationNextStep())"
        //  => Ask BZ if is it really necessary to call it twice (and subtract duration twice)?
        pedestrian1.setTimeCredit(pedestrian1.getTimeCredit() - durationStep);
        pedestrian2.setTimeCredit(pedestrian1.getTimeCredit());
    }
}
