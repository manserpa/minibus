/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package org.matsim.contrib.minibus.operator;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.minibus.genericUtils.GridNode;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Simple Franchise system rejecting all intended plan that do not overlap with existing plans
 * 
 * @author manserpa
 *
 */
public final class PRouteOverlap {

	private final static Logger log = Logger.getLogger(PFranchise.class);
	
	private final boolean activated;
	private HashMap<Id<Operator>, List<String>> stopsToBeServedHashes = new HashMap<Id<Operator>,  List<String>>();
	private double gridSize;

	
	public PRouteOverlap(boolean useRouteOverlapping, double gridSize) {
		this.activated = useRouteOverlapping;
		this.gridSize = gridSize;
		if(this.activated){
			log.info("Route overlapping system activated");
		} else{
			log.info("Route overlapping system NOT activated");
		}
	}

	public boolean planRejected(PPlan plan, Id<Operator> operatorId) {
		
		if(!this.activated){
			return false;
		}

		boolean reject = false;
		
		String routeHash = generateRouteHash(plan);
		String[] desiredStops = routeHash.split("===");
		
		List<String> getRouteHashes = new ArrayList<String>();
		if(this.stopsToBeServedHashes.containsKey(operatorId))
			getRouteHashes = this.stopsToBeServedHashes.get(operatorId);
		
		for(String getAllExistingRoutes: getRouteHashes)	{
			int k = 0;
			
			for(String e: desiredStops)	{
				if(getAllExistingRoutes.contains(e))
					k++;
			}
			
			if(k < 2)	{
				reject = true;
				return reject;
			}
		}
		
		if(!reject)	{
			getRouteHashes.add(routeHash);
			this.stopsToBeServedHashes.put(operatorId, getRouteHashes);
		}
		
		return reject;
	}

	/**
	 * Reset all route hashes to the routes currently in use
	 * 
	 * @param operators
	 */
	public void reset(LinkedList<Operator> operators) {
		
		if(this.activated){
			
			this.stopsToBeServedHashes = new HashMap<>();
			
			for (Operator operator : operators) {
				
				List<String> routePlanList = new ArrayList<String>();
				
				for (PPlan plan : operator.getAllPlans()) {
					String routeHash = generateRouteHash(plan);				
					routePlanList.add(routeHash);
				}
				
				this.stopsToBeServedHashes.put(operator.getId(), routePlanList);
			}
		}
	}
	
	private String generateRouteHash(PPlan plan) {
		return generateRoute(plan.getStopsToBeServed());
	}

	/**
	 * Generates a unique String from the stops given
	 * 
	 * @param stopsToBeServed
	 * @return
	 */
	
	private String generateRoute(ArrayList<TransitStopFacility> stopsToBeServed) {
		StringBuffer sB = new StringBuffer();
		
		String lastGridNodeId = null;
		for (TransitStopFacility transitStopFacility : stopsToBeServed) {
			String gridNodeId = GridNode.getGridNodeIdForCoord(transitStopFacility.getCoord(), this.gridSize);
			
			if (lastGridNodeId == null) {
				lastGridNodeId = gridNodeId;
			} else {
				if (gridNodeId.equalsIgnoreCase(lastGridNodeId)) {
					// still in same gridSquare
					continue;
				}
				lastGridNodeId = gridNodeId;
			}

			sB.append("===");
			sB.append(lastGridNodeId); 
		}

		return sB.toString();
	}
}
