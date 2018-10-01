package org.matsim.contrib.minibus.agentReRouting;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.TripRouter;
import org.matsim.facilities.ActivityFacilities;

import javax.inject.Inject;
import javax.inject.Provider;

public class PReRoutingStrategy implements Provider<PlanStrategy> {

    @Inject private GlobalConfigGroup globalConfigGroup;
    @Inject private ActivityFacilities facilities;
    @Inject private Provider<TripRouter> tripRouterProvider;

    @Override
    public PlanStrategy get() {
        PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new RandomPlanSelector<Plan,Person>()) ;
        builder.addStrategyModule(new PReRoute(facilities, tripRouterProvider, globalConfigGroup));
        return builder.build() ;
    }

}
