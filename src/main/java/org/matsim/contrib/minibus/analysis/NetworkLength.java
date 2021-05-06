package org.matsim.contrib.minibus.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class NetworkLength {
    public static void main(String[] args) {
        Set<Id<Link>> linkIds = new HashSet<>();
        Set<String> stopIds = new HashSet<>();

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(args[0]);
        new TransitScheduleReader(scenario).readFile(args[1]);

        // reference case
        /*
        HashMap<Id<TransitLine>, Set<Id<TransitRoute>>> removedRoutes = Utils.readRemovedLines();
        for (TransitLine line: scenario.getTransitSchedule().getTransitLines().values()) {
            if (removedRoutes.containsKey(line.getId())) {
                for (TransitRoute route : line.getRoutes().values()) {
                    if (removedRoutes.get(line.getId()).contains(route.getId())) {
                        for (Id<Link> link : route.getRoute().getLinkIds()) {
                            linkIds.add(link);
                        }
                        for (TransitRouteStop stop: route.getStops()) {
                            if(!stopIds.contains(stop.getStopFacility().getName())) {
                                stopIds.add(stop.getStopFacility().getName());
                            }
                        }
                    }
                }
            }
        }
        */


        // para case
        for (TransitLine line: scenario.getTransitSchedule().getTransitLines().values()) {
            if (line.getId().toString().contains("para")) {
                for (TransitRoute route : line.getRoutes().values()) {
                    for (Id<Link> link : route.getRoute().getLinkIds()) {
                        linkIds.add(link);
                    }
                    for (TransitRouteStop stop: route.getStops()) {
                        stopIds.add(stop.getStopFacility().getId().toString());
                    }
                }
            }
        }


        double networkLength = 0.0;
        for(Id<Link> link: linkIds) {
            networkLength += scenario.getNetwork().getLinks().get(link).getLength();
        }
        System.out.println(networkLength / 1000.0);
        System.out.println(stopIds.size());
    }
}
