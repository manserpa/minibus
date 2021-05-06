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

package org.matsim.contrib.minibus;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.minibus.agentReRouting.PReRoutingStrategy;
import org.matsim.contrib.minibus.hook.PModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.TripsToLegsAlgorithm;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.MainModeIdentifierImpl;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.*;
import org.matsim.pt.PtConstants;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 * @author manserpa based on original contrib written by aneumann
 */

public final class RunMinibus {

	private final static Logger log = Logger.getLogger(RunMinibus.class);

	public static void main(final String[] args) {
		Config config = ConfigUtils.loadConfig( args[0], new PConfigGroup() ) ;

		Scenario scenario = ScenarioUtils.createScenario(config);
		//scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DefaultEnrichedTransitRoute.class,
		//		new DefaultEnrichedTransitRouteFactory());
		ScenarioUtils.loadScenario(scenario);

		MainModeIdentifier mainModeIdentifier = new MainModeIdentifierImpl();
		TripsToLegsAlgorithm algorithm = new TripsToLegsAlgorithm(mainModeIdentifier);
		for (Person person: scenario.getPopulation().getPersons().values())	{
			Plan plan = person.getSelectedPlan();
			algorithm.run(plan);
			
		}

		/*
		for(TransitLine line: scenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				route.setTransportMode("detPt");
			}
		}
		*/

		Controler controler = new Controler(scenario);

		// controler.addOverridingModule(new BaselineModule());
		// controler.addOverridingModule(new BaselineTransitModule());
		// controler.addOverridingModule(new BaselineTrafficModule(3.0));
		// controler.addOverridingModule(new ZurichModule());
		// controler.addOverridingModule(new CustomModeChoiceModule(cmd));

		controler.addOverridingModule(new PModule());
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addPlanStrategyBinding("PReRoute").toProvider(PReRoutingStrategy.class);
			}
		});

		boolean subsidies = false;
		if (subsidies) {
			PConfigGroup pConfig = ConfigUtils.addOrGetModule(config, PConfigGroup.class);
			pConfig.setUseSubsidyApproach(true);
		}

		controler.run();
	}		
}