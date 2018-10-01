package org.matsim.contrib.minibus.ptReplanningModule;

import java.util.List;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.pt.PtConstants;

/**
 * Removes all legs from a plan and sets the mode to public transport
 *
 */

public class LegRemover implements PlanAlgorithm {

	public void run(final Plan plan) {
		List<PlanElement> planElements = plan.getPlanElements();
		for (int i = 0, n = planElements.size(); i < n; i++) {
			PlanElement pe = planElements.get(i);
			if (pe instanceof Activity) {
				Activity act = (Activity) pe;
				if (PtConstants.TRANSIT_ACTIVITY_TYPE.equals(act.getType())) {
					final int index = i;
					PopulationUtils.removeActivity(((Plan) plan), index);
					n -= 2;
					i--; // i will be incremented again in next loop-iteration, so we'll check the next act
				}
			} else if (pe instanceof Leg) {
				Leg leg = (Leg) pe;
				if (TransportMode.transit_walk.equals(leg.getMode())) {
					leg.setMode(TransportMode.pt);
					leg.setRoute(null);
				}
				if (TransportMode.car.equals(leg.getMode())) {
					leg.setMode(TransportMode.pt);
					leg.setRoute(null);
				}
			}
		}
	}
}