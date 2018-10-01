package org.matsim.contrib.minibus.ptReplanningModule;

import org.apache.log4j.Logger;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.population.algorithms.AbstractPersonAlgorithm;
import org.matsim.core.population.algorithms.ParallelPersonAlgorithmUtils;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.scenario.MutableScenario;

/**
 * This is to PT-Replanning module
 * 
 * @author manserpa
 * 
 * !!! Very important !!! a change in the core replanning module has been made. Otherwise, this feature does not work properly
 * 
 * A big problem is that the files are in the playground, but they should be better in the contrib. Now, the running file is in the playground,
 * the execution happens in the contrib, which targets again to the playground for the replanning module -> results in a circle (Eclipse complains)
 *
 */

public class PseudoReplanning  {

	private final static Logger log = Logger.getLogger(PseudoReplanning.class);
	private MatsimServices controler;
	
	
	public PseudoReplanning(final MatsimServices controler, int iteration)	{
		this.controler = controler;
		
		final AgentReRouteHandlerImpl agentsToReRoute = new AgentReRouteHandlerImpl(this.controler.getScenario().getPopulation().getPersons(), iteration);
		
		final AgentReRouteFactoryImpl stuckFactory = new AgentReRouteFactoryImpl();
		
		ParallelPersonAlgorithmUtils.run(controler.getScenario().getPopulation(), controler.getConfig().global().getNumberOfThreads(), new ParallelPersonAlgorithmUtils.PersonAlgorithmProvider() {
			@Override
			public AbstractPersonAlgorithm getPersonAlgorithm() {
				return stuckFactory.getReRouteStuck(new PlanRouter(
						controler.getTripRouterProvider().get(),
						controler.getScenario().getActivityFacilities()
						), ((MutableScenario) controler.getScenario()), agentsToReRoute.getAgentsToReRoute());
			}
		});
	
	}
}