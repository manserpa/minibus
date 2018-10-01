/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

/**
 * 
 */
package org.matsim.contrib.minibus.operator;

import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.minibus.PConfigGroup;
import org.matsim.contrib.minibus.PConstants.OperatorState;
import org.matsim.contrib.minibus.replanning.PStrategy;
import org.matsim.contrib.minibus.replanning.PStrategyManager;
import org.matsim.contrib.minibus.routeProvider.PRouteProvider;
import org.matsim.contrib.minibus.scoring.PScoreContainer;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.vehicles.Vehicle;

/**
 * @author ikaddoura, dhosse
 *
 */
public final class WelfareCarefulMultiPlanOperator extends AbstractOperator {

	public static final String OPERATOR_NAME = "WelfareCarefulMultiPlanOperator";

	private CarefulMultiPlanOperator delegate;
	private WelfareAnalyzer welfareAnalyzer;
	
	WelfareCarefulMultiPlanOperator(Id<Operator> id, PConfigGroup pConfig, PFranchise franchise, WelfareAnalyzer welfareAnalyzer, PRouteOverlap pRouteOverlap) {
		super(id, pConfig, franchise, pRouteOverlap);
		delegate = new CarefulMultiPlanOperator(id, pConfig, franchise, pRouteOverlap);
		this.welfareAnalyzer = welfareAnalyzer;
	}

	@Override
	public boolean init(PRouteProvider pRouteProvider, PStrategy initialStrategy, int iteration, double initialBudget) {
		return delegate.init(pRouteProvider, initialStrategy, iteration, initialBudget);
	}

	@Override
	public List<PPlan> getAllPlans() {
		return delegate.getAllPlans();
	}

	@Override
	public PPlan getBestPlan() {
		return delegate.getBestPlan();
	}

	@Override
	public void replan(PStrategyManager pStrategyManager, int iteration) {
		delegate.replan(pStrategyManager, iteration);
	}

	@Override
	public void score(Map<Id<Vehicle>, PScoreContainer> driverId2ScoreMap) {
				
		delegate.scoreLastIteration = delegate.score;
		delegate.score = 0;
		
		// score all plans
		for (PPlan plan : delegate.getAllPlans()) {
			scorePlan(driverId2ScoreMap, plan);
			double welfareCorrection = getWelfareCorrection(plan);
			plan.setScore(plan.getScore() + welfareCorrection);
			
			delegate.score += plan.getScore();
			for (TransitRoute route : plan.getLine().getRoutes().values()) {
				StringBuffer sB = new StringBuffer();
				sB.append(plan.toString(delegate.budget + delegate.score));
				sB.append(", welfare_correction: " + welfareCorrection);
				sB.append(", expenses: " + Double.toString(welfareCorrection - plan.getScore()));
				route.setDescription(sB.toString());
			}
		}
		
		delegate.processScore("BeHappy");
		
	}

	private double getWelfareCorrection(PPlan plan) {
		Id<PPlan> pplanId = Id.create(plan.getLine().getId().toString() + "-" + plan.getId().toString(), PPlan.class);
		return welfareAnalyzer.getLineId2welfareCorrection(pplanId);
	}

	@Override
	public Id<Operator> getId() {
		return delegate.getId();
	}

	@Override
	public Id<PPlan> getNewPlanId() {
		return delegate.getNewPlanId();
	}

	@Override
	public PFranchise getFranchise() {
		return delegate.getFranchise();
	}

	@Override
	public double getMinOperationTime() {
		return delegate.getMinOperationTime();
	}

	@Override
	public TransitLine getCurrentTransitLine() {
		return delegate.getCurrentTransitLine();
	}

	@Override
	public double getBudget() {
		return delegate.getBudget();
	}

	@Override
	public int getNumberOfVehiclesOwned() {
		return delegate.getNumberOfVehiclesOwned();
	}

	@Override
	public int getCurrentIteration() {
		return delegate.getCurrentIteration();
	}

	@Override
	public PRouteProvider getRouteProvider() {
		return delegate.getRouteProvider();
	}

	@Override
	public OperatorState getOperatorState() {
		return delegate.getOperatorState();
	}

	@Override
	public int hashCode() {
		return delegate.hashCode();
	}

	@Override
	public void setBudget(double budget) {
		delegate.setBudget(budget);
	}

	@Override
	public String toString() {
		return delegate.toString();
	}
	
}