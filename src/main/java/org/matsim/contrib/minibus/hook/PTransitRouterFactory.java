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

package org.matsim.contrib.minibus.hook;

import ch.sbb.matsim.routing.pt.raptor.RaptorConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import org.apache.log4j.Logger;
import org.matsim.contrib.minibus.PConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterNetwork;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 *
 * @author aneumann
 *
 */
class PTransitRouterFactory implements Provider<TransitRouter> {
	// How is this working if nothing is injected?  But presumably it uses "Provider" only as a syntax clarifier, but the class
	// is not injectable. kai, jun'16

	private final static Logger log = Logger.getLogger(PTransitRouterFactory.class);
	private final Config config;
	private TransitRouterConfig transitRouterConfig;
	private final String ptEnabler;

	private boolean needToUpdateRouter = true;
	private TransitRouterNetwork routerNetwork = null;
	private Provider<TransitRouter> routerFactory = null;
	@Inject private TransitSchedule schedule;
	private SwissRailRaptorData raptorParams;

	public PTransitRouterFactory(Config config){
		this.config = config;
		PConfigGroup pConfig = ConfigUtils.addOrGetModule(config, PConfigGroup.class) ;
		this.ptEnabler = pConfig.getPtEnabler() ;

		this.createTransitRouterConfig(config);
	}

	private void createTransitRouterConfig(Config config) {
		this.transitRouterConfig = new TransitRouterConfig(config.planCalcScore(), config.plansCalcRoute(), config.transitRouter(), config.vspExperimental());
	}

	void updateTransitSchedule() {
		this.needToUpdateRouter = true;

		RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(this.config);
		this.raptorParams = SwissRailRaptorData.create(this.schedule, raptorConfig);

	}

	@Override
	public TransitRouter get() {
		if(needToUpdateRouter) {
			// okay update all routers
			needToUpdateRouter = false;
		}

		if (this.routerFactory == null) {
			return this.createRaptorRouter();
		} else {
			return this.routerFactory.get();
		}
	}

	private TransitRouter createRaptorRouter() {
		if ( this.raptorParams == null) {
			updateTransitSchedule();
		}
		return new SwissRailRaptor(this.raptorParams);
	}
}

