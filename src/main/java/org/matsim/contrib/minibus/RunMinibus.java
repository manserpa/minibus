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
import org.matsim.contrib.minibus.agentReRouting.PReRoutingStrategy;
import org.matsim.contrib.minibus.hook.PModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Random;

/**
 * 
 * @author manserpa based on original contrib written by aneumann
 */

public final class RunMinibus {

	private final static Logger log = Logger.getLogger(RunMinibus.class);

	public static void main(final String[] args) {
		Config config = ConfigUtils.loadConfig( args[0], new PConfigGroup() ) ;

		Scenario scenario = ScenarioUtils.loadScenario(config);

		Controler controler = new Controler(scenario);

		controler.addOverridingModule(new PModule());

		// add custom routing strategy
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addPlanStrategyBinding("PReRoute").toProvider(PReRoutingStrategy.class);
			}
		});

		// if desired, add subsidy approach here
		PConfigGroup pConfig = ConfigUtils.addOrGetModule(config, PConfigGroup.class);
		pConfig.setUseSubsidyApproach(false);

		controler.run();
	}		
}