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
		//addControlerListenerBinding().toInstance( pTransitRouterFactory ) ;

		// (needs to be injected _after_ PControlerListener, so that it is executed _before_ PControlerListener.
		// yyyy injecting the TransitRouterFactory besides the TransitRouter is a fix to re-configure the factory in every iteration.
		// A more general solution suggested by MZ would be to define an iteration scope.  Then the factory could be forced
		// to reconstruct itself in every iteration, thus pulling new information (in this case the updated transit schedule)
		// by itself.  Is on the "list", has not been done yet, will be done eventually, until then this remains the way it is.
		// kai, jan'17)

		// TODO (PM) Do we need this?
		/*
		String outputSubsidy = ConfigUtils.addOrGetModule(getConfig(), ControlerConfigGroup.class ).getOutputDirectory() + "/run.StopsToSubsidize.csv";
		ConfigUtils.addOrGetModule(getConfig(), PConfigGroup.class ).setInitialSubsidyFile(outputSubsidy);
		*/

		bind(TicketMachineI.class).to(TicketMachineDefaultImpl.class);
		bind(POperators.class).to(PBox.class).asEagerSingleton();
		//bindMobsim().toProvider(PQSimProvider.class);

		// TODO (PM) We can install PStats as an extra module, that's fine. But have another look at what we need and what we don't need
		install( new PStatsModule() );
	}
}