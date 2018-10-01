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
 * Chooses then randomly a new stop within the buffer and inserts it in both directions of the line.
 * This is done assuming the most distant stops to be the termini.
 * 
 * @author aneumann
 *
 */
public final class SidewaysRouteExtensionBF extends AbstractPStrategyModule {
	
	private final static Logger log = Logger.getLogger(SidewaysRouteExtensionBF.class);
	public static final String STRATEGY_NAME = "SidewaysRouteExtensionBF";
	private final double bufferSize;
	private final double bufferSizeMin;
	private final double ratio;
	private final boolean excludeTermini;
	private Network network;
	private TransitSchedule pStops;
	private LeastCostPathCalculator routingAlgo;
	
	public SidewaysRouteExtensionBF(ArrayList<String> parameter, Network pNetwork, TransitSchedule pStopsOnly) {
		super();
		if(parameter.size() != 4){
			log.error("Parameter 1: Buffer size in meter");
			log.error("Parameter 2: Minimal buffer size in meter");
			log.error("Parameter 3: Ratio bufferSize to route's beeline length. If set to something very small, e.g. 0.01, the calculated buffer size may be smaller than the one specified in parameter 1. Parameter 1 will then be taken as minimal buffer size.");
			log.error("Parameter 4: Remove buffer from termini - true/false");
		}
		this.bufferSize = Double.parseDouble(parameter.get(0));
		this.bufferSizeMin = Double.parseDouble(parameter.get(1));
		this.ratio = Double.parseDouble(parameter.get(2));
		this.excludeTermini = Boolean.parseBoolean(parameter.get(3));
		if(bufferSize <= bufferSizeMin)	{
			log.error("Minimal buffer size has to be smaller than the buffer size");
		}
		this.network = pNetwork;
		this.pStops = pStopsOnly;
		
		FreespeedTravelTimeAndDisutility tC = new FreespeedTravelTimeAndDisutility(-6.0, 0.0, 0.0); // Here, it may make sense to use the variable cost parameters given in the config. Ihab/Daniel may'14
		this.routingAlgo = new DijkstraFactory().createPathCalculator(this.network, tC, tC);
	}

	@Override
	public PPlan run(Operator operator) {

		PPlan oldPlan = operator.getBestPlan();
		//log.info("Sideways" + operator.getBestPlan().getId().toString());
		ArrayList<TransitStopFacility> currentStopsToBeServed = oldPlan.getStopsToBeServed();
		
		TransitStopFacility baseStop = currentStopsToBeServed.get(0);
		TransitStopFacility remoteStop = currentStopsToBeServed.get(currentStopsToBeServed.size() - 1);
		double bufferSizeBasedOnRatio = CoordUtils.calcEuclideanDistance(baseStop.getCoord(), remoteStop.getCoord()) * this.ratio;
		
		List<Geometry> lineStrings = this.createGeometryFromStops(currentStopsToBeServed);
		List<Geometry> stopPoints = this.createPointGeometryFromStops(currentStopsToBeServed);
		
		Geometry buffer = this.createBuffer(lineStrings, stopPoints, Math.max(this.bufferSize, bufferSizeBasedOnRatio), this.bufferSizeMin, this.excludeTermini);
		
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
		newPlan.setHeadway(operator.getBestPlan().getHeadway());
		
		newPlan.setLine(operator.getRouteProvider().createTransitLineFromOperatorPlan(operator.getId(), newPlan));
		
		return newPlan;
	}


	private ArrayList<TransitStopFacility> addStopToExistingStops(TransitStopFacility baseStop, TransitStopFacility remoteStop, ArrayList<TransitStopFacility> currentStopsToBeServed, TransitStopFacility newStop) {
		
		double smallestDistance = Double.MAX_VALUE;
		TransitStopFacility stopWithSmallestDistance = currentStopsToBeServed.get(0);
		TransitStopFacility stopWithSecondSmallestDistance = currentStopsToBeServed.get(0);
		
		for (TransitStopFacility transitStopFacility : currentStopsToBeServed) {
			double currentDistance = CoordUtils.calcEuclideanDistance(newStop.getCoord(), transitStopFacility.getCoord());
			if (currentDistance < smallestDistance) {
				smallestDistance = currentDistance;
				stopWithSecondSmallestDistance = stopWithSmallestDistance;
				stopWithSmallestDistance = transitStopFacility;
			}
		}
		
		// find index to insert
		int index = Math.min(currentStopsToBeServed.indexOf(stopWithSecondSmallestDistance), currentStopsToBeServed.indexOf(stopWithSmallestDistance));
		
		ArrayList<TransitStopFacility> newStopsToBeServed = new ArrayList<>(currentStopsToBeServed);
		
		double distanceBack = getShortestPath(currentStopsToBeServed.get(index), newStop);
		double distanceForth = getShortestPath(currentStopsToBeServed.get(index), this.pStops.getFacilities().get(reverseStopId(newStop.getId())));

		if(distanceBack < distanceForth)
			newStopsToBeServed.add(index + 1, newStop);
		else	
			newStopsToBeServed.add(index + 1, this.pStops.getFacilities().get(reverseStopId(newStop.getId())));

		return newStopsToBeServed;

		
		/*
		
		for (TransitStopFacility transitStopFacility : currentStopsToBeServed) {
			double distanceBack = getShortestPath(transitStopFacility, newStop);
			double distanceForth = getShortestPath(transitStopFacility, this.pStops.getFacilities().get(reverseStopId(newStop.getId())));
			
			if(distanceBack < distanceForth)	{
				if (distanceBack < smallestDistance) {
					smallestDistance = distanceBack;
					stopWithSecondSmallestDistance = stopWithSmallestDistance;
					stopWithSmallestDistance = transitStopFacility;
					stopToInsert = newStop;
				}
			}
			else	{
				if (distanceForth < smallestDistance) {
					smallestDistance = distanceForth;
					stopWithSecondSmallestDistance = stopWithSmallestDistance;
					stopWithSmallestDistance = transitStopFacility;
					stopToInsert = this.pStops.getFacilities().get(reverseStopId(newStop.getId()));
				}
			}
		}
		
		ArrayList<TransitStopFacility> newStopsToBeServed = currentStopsToBeServed;
		// find index to insert
		int index = Math.min(currentStopsToBeServed.indexOf(stopWithSecondSmallestDistance), currentStopsToBeServed.indexOf(stopWithSmallestDistance));
				
		// insert
		newStopsToBeServed.add(index + 1, stopToInsert);
		//newStopsToBeServed.add(currentStopsToBeServed.indexOf(stopWithSmallestDistance) + 1, stopToInsert);
		
		return newStopsToBeServed;
		*/
	}
	
	
	private double getShortestPath(TransitStopFacility fromStop, TransitStopFacility toStop)	{
		Path path = this.routingAlgo.calcLeastCostPath(this.network.getLinks().get(fromStop.getLinkId()).getToNode(), this.network.getLinks().get(toStop.getLinkId()).getFromNode(), 0.0, null, null);
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


	private Geometry createBuffer(List<Geometry> lineStrings, List<Geometry> stopPoints, double bufferSize, double bufferSizeMin, boolean excludeTermini) {
		BufferParameters bufferParameters = new BufferParameters();

		if (excludeTermini) {
			bufferParameters.setEndCapStyle(BufferParameters.CAP_FLAT);
		} else {
			bufferParameters.setEndCapStyle(BufferParameters.CAP_ROUND);
		}


		Geometry unionmax = null;
		
		for (Geometry lineString : lineStrings) {
			Geometry buffer = BufferOp.bufferOp(lineString, bufferSize, bufferParameters);
			if (unionmax == null) {
				unionmax = buffer;
			} else {
				unionmax = unionmax.union(buffer);
			}
		}
		
		
		Geometry unionmin = null;
		
		// exclude first and last stop from buffer
		for(int i = 1; i < stopPoints.size(); i++)	{
			
			Geometry buff = stopPoints.get(i).buffer(bufferSizeMin);
			
			if (unionmin == null)	{
				unionmin = buff;
			}
			else {
				unionmin = unionmin.union(buff);
			}
		}
		
		Geometry union = null;
		
		if(unionmin == null)	{
			union = unionmax;
		}
		else	{		
			union = unionmax.difference(unionmin);
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
	
	// manserpa: return stop points as geometry list
	private List<Geometry> createPointGeometryFromStops(ArrayList<TransitStopFacility> stops) {
		List<Geometry> geometries = new LinkedList<>();
		
		Coordinate coord = new Coordinate();
		for (TransitStopFacility stop : stops) {
			coord = new Coordinate(stop.getCoord().getX(), stop.getCoord().getY(), 0.0);
			Geometry lineString = new GeometryFactory().createPoint(coord);
			geometries.add(lineString);
		}
		
		return geometries;
	}


	@Override
	public String getStrategyName() {
		return SidewaysRouteExtensionBF.STRATEGY_NAME;
	}
}


