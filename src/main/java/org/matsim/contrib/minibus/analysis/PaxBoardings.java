package org.matsim.contrib.minibus.analysis;

import ch.ethz.matsim.baseline_scenario.transit.routing.DefaultEnrichedTransitRoute;
import ch.ethz.matsim.baseline_scenario.transit.routing.DefaultEnrichedTransitRouteFactory;
import ch.ethz.matsim.baseline_scenario.transit.routing.EnrichedTransitRoute;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.util.HashMap;
import java.util.Set;

public class PaxBoardings {
    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DefaultEnrichedTransitRoute.class,
                new DefaultEnrichedTransitRouteFactory());
        new PopulationReader(scenario).readFile(args[0]);

        System.out.println(getParaBoardings(scenario));
    }

    private static int getParaBoardings(Scenario scenario)  {
        int boardings = 0;
        for(Person person: scenario.getPopulation().getPersons().values())  {
            Plan plan = person.getSelectedPlan();
            for(Leg leg: TripStructureUtils.getLegs(plan))   {
                if(leg.getRoute() instanceof EnrichedTransitRoute)  {
                    if(((EnrichedTransitRoute) leg.getRoute()).getTransitLineId().toString().contains("para"))   {
                        boardings++;
                    }
                }
            }
        }
        return boardings;
    }

    private static int getRefBoardings(Scenario scenario)   {
        int boardings = 0;
        HashMap<Id<TransitLine>, Set<Id<TransitRoute>>> removedRoutes = Utils.readRemovedLines();
        for(Person person: scenario.getPopulation().getPersons().values())  {
            Plan plan = person.getSelectedPlan();
            for(Leg leg: TripStructureUtils.getLegs(plan))   {
                if(leg.getRoute() instanceof EnrichedTransitRoute)  {
                    if(removedRoutes.containsKey(((EnrichedTransitRoute) leg.getRoute()).getTransitLineId()))   {
                        if(removedRoutes.get(((EnrichedTransitRoute) leg.getRoute()).getTransitLineId()).contains(((EnrichedTransitRoute) leg.getRoute()).getTransitRouteId())) {
                            boardings++;
                        }
                    }
                }
            }
        }
        return boardings;
    }
}
