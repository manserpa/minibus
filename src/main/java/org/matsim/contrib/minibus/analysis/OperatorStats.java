package org.matsim.contrib.minibus.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkReaderMatsimV2;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class OperatorStats {
    public static void main(String[] args) {
        Set<Id<Link>> linkIds = new HashSet<>();
        Set<Id<TransitStopFacility>> stopIds = new HashSet<>();

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new NetworkReaderMatsimV2(scenario.getNetwork()).readFile(args[0]);
        new TransitScheduleReader(scenario).readFile(args[1]);

        // reference case
        /*
        HashMap<Id<TransitLine>, Set<Id<TransitRoute>>> removedRoutes = Utils.readRemovedLines();
        double totVehKM = 0.0;
        double totVehH = 0.0;
        for (TransitLine line: scenario.getTransitSchedule().getTransitLines().values()) {
            if (removedRoutes.containsKey(line.getId())) {
                for (TransitRoute route : line.getRoutes().values()) {
                    if (removedRoutes.get(line.getId()).contains(route.getId())) {
                        double vehKM = 0.0;
                        for (Id<Link> link : route.getRoute().getLinkIds()) {
                            vehKM += scenario.getNetwork().getLinks().get(link).getLength() / 1000;
                        }
                        double vehH = 0.0;
                        vehH = (route.getStops().get(route.getStops().size() - 1).getArrivalOffset() -
                                route.getStops().get(0).getDepartureOffset()) / 3600;

                        totVehKM += (route.getDepartures().size() * vehKM);
                        totVehH += (route.getDepartures().size() * vehH);
                    }
                }
            }
        }
*/

        // para case
        double totVehKM = 0.0;
        double totVehH = 0.0;
        for (TransitLine line: scenario.getTransitSchedule().getTransitLines().values()) {
            if (line.getId().toString().contains("para")) {
                for (TransitRoute route : line.getRoutes().values()) {
                    double vehKM = 0.0;
                    for (Id<Link> link : route.getRoute().getLinkIds()) {
                        vehKM += scenario.getNetwork().getLinks().get(link).getLength() / 1000;
                    }
                    double vehH = 0.0;
                    vehH = (route.getStops().get(route.getStops().size() - 1).getArrivalOffset() -
                            route.getStops().get(0).getDepartureOffset()) / 3600;

                    totVehKM += (route.getDepartures().size() * vehKM);
                    totVehH += (route.getDepartures().size() * vehH);

                }
            }
        }

        System.out.println(totVehKM);
        System.out.println(totVehH);
    }
}
