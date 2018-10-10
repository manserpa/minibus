/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  * Builder.java
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2014 by the members listed in the COPYING, *
 *  *                   LICENSE and WARRANTY file.                            *
 *  * email           : info at matsim dot org                                *
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  *   This program is free software; you can redistribute it and/or modify  *
 *  *   it under the terms of the GNU General Public License as published by  *
 *  *   the Free Software Foundation; either version 2 of the License, or     *
 *  *   (at your option) any later version.                                   *
 *  *   See also COPYING, LICENSE and WARRANTY file                           *
 *  *                                                                         *
 *  * ***********************************************************************
 */

package org.matsim.contrib.minibus.hook;

import org.matsim.contrib.minibus.fare.TicketMachineDefaultImpl;
import org.matsim.contrib.minibus.fare.TicketMachineI;
import org.matsim.contrib.minibus.operator.POperators;
import org.matsim.contrib.minibus.stats.PStatsModule;
import org.matsim.core.controler.AbstractModule;
import org.matsim.pt.router.TransitRouter;

public final class PModule extends AbstractModule {

	@Override
	public void install() {
		final PTransitRouterFactory pTransitRouterFactory = new PTransitRouterFactory(this.getConfig());
		bind(TransitRouter.class).toProvider(pTransitRouterFactory);
		bind(PTransitRouterFactory.class).toInstance(pTransitRouterFactory);

		addControlerListenerBinding().to(PControlerListener.class);

		bind(TicketMachineI.class).to(TicketMachineDefaultImpl.class);
		bind(POperators.class).to(PBox.class).asEagerSingleton();

		install( new PStatsModule() );
	}
}