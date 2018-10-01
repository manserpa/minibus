package org.matsim.contrib.minibus.agentReRouting;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.TripRouter;
import org.matsim.facilities.ActivityFacilities;

import javax.inject.Provider;

/**
 * Uses the routing algorithm provided by the {@linkplain Controler} for
 * calculating the routes of plans during Replanning.
 *
 * @author mrieser
 */
public class PReRoute extends AbstractMultithreadedModule {

    private ActivityFacilities facilities;

    private final Provider<TripRouter> tripRouterProvider;

    public PReRoute(ActivityFacilities facilities, Provider<TripRouter> tripRouterProvider, GlobalConfigGroup globalConfigGroup) {
        super(globalConfigGroup);
        this.facilities = facilities;
        this.tripRouterProvider = tripRouterProvider;
    }

    public PReRoute(Scenario scenario, Provider<TripRouter> tripRouterProvider) {
        this(scenario.getActivityFacilities(), tripRouterProvider, scenario.getConfig().global());
    }

    @Override
    public final PlanAlgorithm getPlanAlgoInstance() {
        return new PPlanRouter(
                tripRouterProvider.get(),
                facilities);
    }

}
