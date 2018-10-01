/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package org.matsim.contrib.minibus.replanning;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.operation.buffer.BufferOp;
import com.vividsolutions.jts.operation.buffer.BufferParameters;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.minibus.operator.Operator;
import org.matsim.contrib.minibus.operator.PPlan;
import org.matsim.contrib.minibus.routeProvider.PRouteProvider;
import org.matsim.core.router.Dijkstra;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.*;

/**
 * Takes the route transformed into a lineString to calculate a buffer around it.
 * Excludes the part of the buffer going parallel/along the actual route, so only the end caps remain.
 * Chooses then randomly a new stop within the buffer and inserts it after the nearest existing stop.
 * 
 * @author aneumann
 *
 */
public final class EndRouteExtensionBF extends AbstractPStrategyModule {
	
	private final static Logger log = Logger.getLogger(EndRouteExtensionBF.class);
	public static final String STRATEGY_NAME = "EndRouteExtensionBF";
	private final double bufferSize;
	private final double ratio;
	private final double bufferSizeMin;
	private Network network;
	private LeastCostPathCalculator routingAlgo;
	private TransitSchedule pStops;
	
	public EndRouteExtensionBF(ArrayList<String> parameter, Network pNetwork, TransitSchedule pStopsOnly) {
		super();
		if(parameter.size() != 3){
			log.error("Parameter 1: Buffer size in meter");
			log.error("Parameter 2: Minimal buffer size in meter");
			log.error("Parameter 3: Ratio bufferSize to route's beeline length. If set to something very small, e.g. 0.01, the calculated buffer size may be smaller than the one specified in parameter 1. Parameter 1 will then be taken as minimal buffer size.");
		}
		this.bufferSize = Double.parseDouble(parameter.get(0));
		this.bufferSizeMin = Double.parseDouble(parameter.get(1));
		this.ratio = Double.parseDouble(parameter.get(2));
		this.network = pNetwork;
		this.pStops = pStopsOnly;
		
		FreespeedTravelTimeAndDisutility tC = new FreespeedTravelTimeAndDisutility(-6.0, 0.0, 0.0); // Here, it may make sense to use the variable cost parameters given in the config. Ihab/Daniel may'14
		this.routingAlgo = new DijkstraFactory().createPathCalculator(this.network, tC, tC);
	}

	@Override
	public PPlan run(Operator operator) {

		PPlan oldPlan = operator.getBestPlan();
		ArrayList<TransitStopFacility> currentStopsToBeServed = oldPlan.getStopsToBeServed();
		
		TransitStopFacility baseStop = currentStopsToBeServed.get(0);
		TransitStopFacility remoteStop = currentStopsToBeServed.get(currentStopsToBeServed.size() - 1);
		double bufferSizeBasedOnRatio = CoordUtils.calcEuclideanDistance(baseStop.getCoord(), remoteStop.getCoord()) * this.ratio;
		
		List<Geometry> lineStrings = this.createGeometryFromStops(currentStopsToBeServed);
		Geometry bufferWithoutEndCaps = this.createBuffer(lineStrings, Math.max(this.bufferSize, bufferSizeBasedOnRatio), true);
		Geometry bufferWithEndCaps = this.createBuffer(lineStrings, Math.max(this.bufferSize, bufferSizeBasedOnRatio), false);
		Geometry buffer1 = bufferWithEndCaps.difference(bufferWithoutEndCaps);
		
		Geometry bufferWithoutEndCapsMin = this.createBuffer(lineStrings, this.bufferSizeMin, true);
		Geometry bufferWithEndCapsMin = this.createBuffer(lineStrings, this.bufferSizeMin, false);
		Geometry bufferMin = bufferWithEndCapsMin.difference(bufferWithoutEndCapsMin);
		
		Geometry buffer = buffer1.difference(bufferMin);
		
		Set<Id<TransitStopFacility>> stopsUsed = this.getStopsUsed(oldPlan.getLine().getRoutes().values());
		TransitStopFacility newStop = this.drawRandomStop(buffer, operator.getRouteProvider(), stopsUsed);
		
		if (newStop == null) {
			return null;
		}
		
		ArrayList<TransitStopFacility> newStopsToBeServed = this.addStopToExistingStops(baseStop, remoteStop, currentStopsToBeServed, newStop);
		
		// create new plan
		PPlan newPlan = new PPlan(operator.getNewPlanId(), this.getStrategyName(), oldPlan.getId());
		newPlan.setNVehicles(1);
		newPlan.setStartTime(oldPlan.getStartTime());
		newPlan.setEndTime(oldPlan.getEndTime());
		newPlan.setPVehicleType(oldPlan.getPVehicleType());
		newPlan.setStopsToBeServed(newStopsToBeServed);
		newPlan.setHeadway(oldPlan.getHeadway());
		
		newPlan.setLine(operator.getRouteProvider().createTransitLineFromOperatorPlan(operator.getId(), newPlan));
		
		return newPlan;
	}


	private ArrayList<TransitStopFacility> addStopToExistingStops(TransitStopFacility baseStop, TransitStopFacility remoteStop, ArrayList<TransitStopFacility> currentStopsToBeServed, TransitStopFacility newStop) {
		ArrayList<TransitStopFacility> newStopsToBeServed = new ArrayList<>(currentStopsToBeServed);
		
		if (CoordUtils.calcEuclideanDistance(baseStop.getCoord(), newStop.getCoord()) < CoordUtils.calcEuclideanDistance(remoteStop.getCoord(), newStop.getCoord())) {
			double distanceBack = getShortestPath(newStop, baseStop);
			double distanceForth = getShortestPath(this.pStops.getFacilities().get(reverseStopId(newStop.getId())), baseStop);

			if(distanceBack > distanceForth)
				newStopsToBeServed.add(0, newStop);
			else	
				newStopsToBeServed.add(0, this.pStops.getFacilities().get(reverseStopId(newStop.getId())));
		} 
		else {
			double distanceBack = getShortestPath(remoteStop, newStop);
			double distanceForth = getShortestPath(remoteStop, this.pStops.getFacilities().get(reverseStopId(newStop.getId())));

			if(distanceBack < distanceForth)	
				newStopsToBeServed.add(newStopsToBeServed.indexOf(remoteStop) + 1, newStop);
			else	
				newStopsToBeServed.add(newStopsToBeServed.indexOf(remoteStop) + 1, this.pStops.getFacilities().get(reverseStopId(newStop.getId())));
		}
		
		return newStopsToBeServed;
	}
	
	private double getShortestPath(TransitStopFacility fromStop, TransitStopFacility toStop)	{
		Path path = this.routingAlgo.calcLeastCostPath(this.network.getLinks().get(fromStop.getLinkId()).getFromNode(), this.network.getLinks().get(toStop.getLinkId()).getFromNode(), 0.0, null, null);
		double distance = 0.0;
		for (Link link : path.links) {
			distance += link.getLength();
		}
		return distance;
	}
	
	private Id<TransitStopFacility> reverseStopId(Id<TransitStopFacility> stopId)	{
		String[] stopIdSplit = stopId.toString().split("_");
		String reversedStop = "";
		for(int i = 0; i < stopIdSplit.length - 1; i++)	{
			reversedStop += stopIdSplit[i] + "_";
		}
		if(stopIdSplit[stopIdSplit.length - 1].equals("A"))
			reversedStop += "B";
		else
			reversedStop += "A";
		Id<TransitStopFacility> reversedStopId = Id.create(reversedStop, TransitStopFacility.class);
		
		return reversedStopId;
	}

	private Set<Id<TransitStopFacility>> getStopsUsed(Collection<TransitRoute> routes) {
		Set<Id<TransitStopFacility>> stopsUsed = new TreeSet<>();
		for (TransitRoute route : routes) {
			for (TransitRouteStop stop : route.getStops()) {
				stopsUsed.add(stop.getStopFacility().getId());
			}
		}
		return stopsUsed;
	}

	private TransitStopFacility drawRandomStop(Geometry buffer, PRouteProvider pRouteProvider, Set<Id<TransitStopFacility>> stopsUsed) {
		List<TransitStopFacility> choiceSet = new LinkedList<>();
		
		// find choice-set
		for (TransitStopFacility stop : pRouteProvider.getAllPStops()) {
			if (!stopsUsed.contains(stop.getId()) && !stopsUsed.contains(reverseStopId(stop.getId()))) {
				if (buffer.contains(MGC.coord2Point(stop.getCoord()))) {
					choiceSet.add(stop);
				}
			}
		}
		
		return pRouteProvider.drawRandomStopFromList(choiceSet);
	}


	private Geometry createBuffer(List<Geometry> lineStrings, double bufferSize, boolean excludeTermini) {
		BufferParameters bufferParameters = new BufferParameters();
		
		if (excludeTermini) {
			bufferParameters.setEndCapStyle(BufferParameters.CAP_FLAT);
		} else {
			bufferParameters.setEndCapStyle(BufferParameters.CAP_ROUND);
		}
		
		Geometry union = null;
		
		for (Geometry lineString : lineStrings) {
			Geometry buffer = BufferOp.bufferOp(lineString, bufferSize, bufferParameters);
			if (union == null) {
				union = buffer;
			} else {
				union = union.union(buffer);
			}
		}
		
		return union;
	}

	private List<Geometry> createGeometryFromStops(ArrayList<TransitStopFacility> stops) {
		List<Geometry> geometries = new LinkedList<>();
		
		ArrayList<Coordinate> coords = new ArrayList<>();
		for (TransitStopFacility stop : stops) {
				coords.add(new Coordinate(stop.getCoord().getX(), stop.getCoord().getY(), 0.0));
		}
		
		Coordinate[] coordinates = coords.toArray(new Coordinate[coords.size()]);
		Geometry lineString = new GeometryFactory().createLineString(coordinates);
		geometries.add(lineString);
		return geometries;
	}

	public String getStrategyName() {
		return EndRouteExtensionBF.STRATEGY_NAME;
	}
}
