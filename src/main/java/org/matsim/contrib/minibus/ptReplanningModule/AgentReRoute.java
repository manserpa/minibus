/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.minibus.ptReplanningModule;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.pt.router.TransitActsRemover;

import java.util.Set;

/**
 * Takes the plan set of all agents that have to reroute their trips and then selects the worst plan of those agents.
 *
 */
final class AgentReRoute extends AbstractAgentReRoute {


	private static final Logger log = Logger.getLogger(AgentReRoute.class);
	
	private final LegRemover legRemover;

	public AgentReRoute(final PlanAlgorithm router, final MutableScenario scenario, Set<Id<Person>> agentsToReRoute) {
		super(router, scenario, agentsToReRoute);
		this.legRemover = new LegRemover(); 
	}
	
	@Override
	public void run(final Person person) {
		
		double minScore = Double.POSITIVE_INFINITY;
		
		for (Plan p: person.getPlans())	{
			if (p.getScore() < minScore)	{
				minScore = p.getScore();
				
				person.setSelectedPlan(p);
			}
		}
		
		Plan selectedPlan = person.getSelectedPlan();
		
		if (selectedPlan == null) {
			// the only way no plan can be selected should be when the person has no plans at all
			log.warn("Person " + person.getId() + " has no plans!");
			return;
		}
		
		if(this.agentsToReRoute.contains(person.getId())){
			this.legRemover.run(selectedPlan);
			this.router.run(selectedPlan);
		}
	}
}