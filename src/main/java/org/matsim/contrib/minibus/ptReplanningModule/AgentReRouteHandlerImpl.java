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
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.MatsimRandom;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Returns the number and the IDs of the agents that have to reroute their trip using PT.
 *
 */
class AgentReRouteHandlerImpl	{

	private static final Logger log = Logger.getLogger(AgentReRouteHandlerImpl.class);

	private Set<Id<Person>> agentsToReRoute;

	private Map<Id<Person>, ? extends Person> agents;

	public AgentReRouteHandlerImpl(Map<Id<Person>, ? extends Person> agents, int iteration) {
		this.agents = agents;
		
		this.agentsToReRoute = new TreeSet<>();
		
		double percentageToReRoute = 0;
		if(iteration <= 200)
			percentageToReRoute = -1 * Math.pow(2,0.02 * iteration) / 35 + 0.65;
		else 
			percentageToReRoute = -1 * Math.pow(2,0.02 * 200) / 35 + 0.65;
		
		for( Id<Person> e : this.agents.keySet())	{
			double rand = MatsimRandom.getRandom().nextDouble();
			if ( rand > (1 - percentageToReRoute) )
				this.agentsToReRoute.add(e);
		}
		
		log.info("initialized " + (1 - percentageToReRoute));
	}
	
	public Set<Id<Person>> resetAgentsToReRoute() {
		this.agentsToReRoute = new TreeSet<>();
		return this.agentsToReRoute;
	}

	public Set<Id<Person>> getAgentsToReRoute() {
		log.info("Returning " + this.agentsToReRoute.size() + " agent ids");
		return this.agentsToReRoute;
	}
}