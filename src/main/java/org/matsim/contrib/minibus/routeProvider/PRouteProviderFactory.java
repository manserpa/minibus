/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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

package org.matsim.contrib.minibus.routeProvider;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.minibus.PConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import java.util.HashSet;
import java.util.Set;

/**
 * 
 * @author aneumann
 *
 */
public final class PRouteProviderFactory {
	private PRouteProviderFactory(){} // should not be instantiated

	private final static Logger log = Logger.getLogger(PRouteProviderFactory.class);

	public static PRouteProvider createRouteProvider(Config config, Population population, PConfigGroup pConfig, TransitSchedule pStopsOnly, String outputDir, EventsManager eventsManager) {

		RandomStopProvider randomStopProvider = new RandomStopProvider(pConfig, population, pStopsOnly, outputDir);
		
		RandomPVehicleProvider randomPVehicleProvider = new RandomPVehicleProvider(pConfig);

		// runs the new routing
		return new BackAndForthScheduleProvider(pStopsOnly, pConfig.getPNetwork(), randomStopProvider, randomPVehicleProvider, pConfig.getVehicleMaximumVelocity(), pConfig.getPlanningSpeedFactor(), pConfig.getDriverRestTime(), pConfig.getPIdentifier(), eventsManager, pConfig.getMode(), pConfig.getPVehicleSettings());
		
		/* old code by Neumann
		 * 
		if(pConfig.getRouteProvider().equalsIgnoreCase(SimpleBackAndForthScheduleProvider.NAME)){
			return new SimpleBackAndForthScheduleProvider(pConfig.getPIdentifier(), pStopsOnly, network, randomStopProvider, randomPVehicleProvider, pConfig.getVehicleMaximumVelocity(), pConfig.getDriverRestTime(), pConfig.getMode());
		} else if(pConfig.getRouteProvider().equalsIgnoreCase(SimpleCircleScheduleProvider.NAME)){
			return new SimpleCircleScheduleProvider(pConfig.getPIdentifier(), pStopsOnly, network, randomStopProvider, randomPVehicleProvider, pConfig.getVehicleMaximumVelocity(), pConfig.getDriverRestTime(), pConfig.getMode());
		} else if(pConfig.getRouteProvider().equalsIgnoreCase(ComplexCircleScheduleProvider.NAME)){
			return new ComplexCircleScheduleProvider(pStopsOnly, network, randomStopProvider, randomPVehicleProvider, pConfig.getVehicleMaximumVelocity(), pConfig.getPlanningSpeedFactor(), pConfig.getDriverRestTime(), pConfig.getMode());
		} else if(pConfig.getRouteProvider().equalsIgnoreCase(TimeAwareComplexCircleScheduleProvider.NAME)){
			return new TimeAwareComplexCircleScheduleProvider(pStopsOnly, network, randomStopProvider, randomPVehicleProvider, pConfig.getVehicleMaximumVelocity(), pConfig.getPlanningSpeedFactor(), pConfig.getDriverRestTime(), pConfig.getPIdentifier(), eventsManager, pConfig.getMode(), pConfig.getPVehicleSettings());
		} else {
			log.error("There is no route provider specified. " + pConfig.getRouteProvider() + " unknown");
			return null;
		}
		*/
	}
}
