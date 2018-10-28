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

package org.matsim.contrib.minibus.hook;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.minibus.PConfigGroup;
import org.matsim.contrib.minibus.PConstants.OperatorState;
import org.matsim.contrib.minibus.fare.StageContainerCreator;
import org.matsim.contrib.minibus.fare.TicketMachineI;
import org.matsim.contrib.minibus.operator.*;
import org.matsim.contrib.minibus.replanning.PStrategyManager;
import org.matsim.contrib.minibus.schedule.PStopsFactory;
import org.matsim.contrib.minibus.scoring.OperatorCostCollectorHandler;
import org.matsim.contrib.minibus.scoring.PScoreContainer;
import org.matsim.contrib.minibus.scoring.PScorePlansHandler;
import org.matsim.contrib.minibus.scoring.StageContainer2AgentMoneyEvent;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.ScoringEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV1;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import javax.inject.Inject;
import java.util.*;


/**
 * Black box for paratransit
 * 
 * @author aneumann
 *
 */

public final class PBox implements POperators {

	private final static Logger log = Logger.getLogger(PBox.class);

	private LinkedList<Operator> operators;

	private final PConfigGroup pConfig;
	private final PFranchise franchise;
	private OperatorInitializer operatorInitializer;

	private TransitSchedule pStopsOnly;
	private TransitSchedule pTransitSchedule;

	private final PScorePlansHandler scorePlansHandler;
	private final StageContainerCreator stageCollectorHandler;
	private final OperatorCostCollectorHandler operatorCostCollectorHandler;
	private final PStrategyManager strategyManager = new PStrategyManager();

	private final TicketMachineI ticketMachine;

	private PRouteOverlap routeOverlap;

	/**
	 * Constructor that allows to set the ticketMachine.  Deliberately in constructor and not as setter to keep the variable final.  Might be
	 * replaced by a builder and/or guice at some later point in time.  But stay with "direct" injection for the time being.  kai, jan'17
	 */
	@Inject PBox(PConfigGroup pConfig, TicketMachineI ticketMachine) {
		this.pConfig = pConfig;
		this.ticketMachine = ticketMachine;
		this.scorePlansHandler = new PScorePlansHandler(this.ticketMachine);
		this.stageCollectorHandler = new StageContainerCreator(this.pConfig.getPIdentifier());

		//this.operatorCostCollectorHandler = new OperatorCostCollectorHandler(this.pConfig.getPIdentifier(), this.pConfig.getCostPerVehicleAndDay(), this.pConfig.getCostPerKilometer() / 1000.0, this.pConfig.getCostPerHour() / 3600.0);
		this.operatorCostCollectorHandler = new OperatorCostCollectorHandler(this.pConfig.getPIdentifier(), this.pConfig.getPVehicleSettings());

		this.routeOverlap = new PRouteOverlap(true, pConfig.getGridSize());
		this.franchise = new PFranchise(this.pConfig.getUseFranchise(), pConfig.getGridSize());
	}

	void notifyStartup(StartupEvent event) {
		TimeProvider timeProvider = new TimeProvider(this.pConfig, event.getServices().getControlerIO().getOutputPath());
		event.getServices().getEvents().addHandler(timeProvider);
		
		// init possible paratransit stops
		this.pStopsOnly = PStopsFactory.createPStops(event.getServices().getScenario().getNetwork(), this.pConfig, event.getServices().getScenario().getTransitSchedule());

		// initialize strategy manager
		this.strategyManager.init(this.pConfig, this.stageCollectorHandler, this.ticketMachine, timeProvider, event.getServices().getControlerIO().getOutputPath(), this.pStopsOnly);

		// init fare collector
		this.stageCollectorHandler.init(event.getServices().getScenario().getNetwork());
		event.getServices().getEvents().addHandler(this.stageCollectorHandler);
		event.getServices().addControlerListener(this.stageCollectorHandler);
		this.stageCollectorHandler.addStageContainerHandler(this.scorePlansHandler);

		// init operator cost collector
		this.operatorCostCollectorHandler.init(event.getServices().getScenario().getNetwork());
		event.getServices().getEvents().addHandler(this.operatorCostCollectorHandler);
		event.getServices().addControlerListener(this.operatorCostCollectorHandler);
		this.operatorCostCollectorHandler.addOperatorCostContainerHandler(this.scorePlansHandler);

		// init fare2moneyEvent
		StageContainer2AgentMoneyEvent fare2AgentMoney = new StageContainer2AgentMoneyEvent(event.getServices(), this.ticketMachine);
		this.stageCollectorHandler.addStageContainerHandler(fare2AgentMoney);

		// init operators
		this.operators = new LinkedList<>();
		this.operatorInitializer = new OperatorInitializer(this.pConfig, this.franchise, this.pStopsOnly, event.getServices(), timeProvider, this.routeOverlap);

		// init additional operators from a given transit schedule file
		// TODO (PM) not necessary
		LinkedList<Operator> operatorsFromSchedule = this.operatorInitializer.createOperatorsFromSchedule(event.getServices().getScenario().getTransitSchedule());
		this.operators.addAll(operatorsFromSchedule);

		// init initial set of operators - reduced by the number of preset operators
		LinkedList<Operator> initialOperators = this.operatorInitializer.createAdditionalOperators(this.strategyManager, event.getServices().getConfig().controler().getFirstIteration(), (this.pConfig.getNumberOfOperators() - operatorsFromSchedule.size()));
		this.operators.addAll(initialOperators);

		// collect the transit schedules from all operators
		this.pTransitSchedule = new TransitScheduleFactoryImpl().createTransitSchedule();
		for (TransitStopFacility stop : this.pStopsOnly.getFacilities().values()) {
			this.pTransitSchedule.addStopFacility(stop);
		}
		for (Operator operator : this.operators) {
			this.pTransitSchedule.addTransitLine(operator.getCurrentTransitLine());
		}

		// create subsidy distribution
		HashMap<Id<TransitStopFacility>, Double> actBasedSub = new HashMap<>();
		if(this.pConfig.getUseSubsidyApproach()) {
			HashMap<Coord, Integer> nbActivities = new HashMap<>();
			HashMap<TransitStopFacility, Integer> nbActivitiesAroundStop = new HashMap<>();
			for (Person person : event.getServices().getScenario().getPopulation().getPersons().values()) {
				for (PlanElement pE : person.getSelectedPlan().getPlanElements()) {
					if (pE instanceof Activity) {
						Activity act = (Activity) pE;
						if (!act.getType().equals("pt interaction") && !act.getType().equals("outside")) {
							nbActivities.putIfAbsent(act.getCoord(), 0);
							nbActivities.put(act.getCoord(), nbActivities.get(act.getCoord()) + 1);
						}
					}
				}
			}

			for (TransitStopFacility stop : this.pStopsOnly.getFacilities().values()) {
				nbActivitiesAroundStop.putIfAbsent(stop, 0);
				for (Coord actCoord : nbActivities.keySet()) {
					if (NetworkUtils.getEuclideanDistance(actCoord, stop.getCoord()) < 500) {
						int nbActs = nbActivities.get(actCoord);
						nbActivitiesAroundStop.put(stop, nbActivitiesAroundStop.get(stop) + nbActs);
					}
				}
			}

			int counter = 0;
			for(TransitStopFacility stop: nbActivitiesAroundStop.keySet())	{
				double subsidies = 30 - (0.5 * Math.pow(2,(nbActivitiesAroundStop.get(stop)*0.004)));
				if(subsidies > 0.0)	{
					counter++;
					actBasedSub.put(stop.getId(), subsidies);
				}
			}
			log.info("number of subsidized stops: " + counter);
		}

		this.ticketMachine.setActBasedSubs(actBasedSub);

		// Reset the franchise system - TODO necessary?
		this.franchise.reset(this.operators);
		this.routeOverlap.reset(this.operators);
	}

	void notifyIterationStarts(IterationStartsEvent event) {

		this.strategyManager.updateStrategies(event.getIteration());

		// Adapt number of operators
		this.handleBankruptOperators(event.getIteration());

		// Replan all operators
		for (Operator operator : this.operators) {
			operator.replan(this.strategyManager, event.getIteration());
		}

		// Collect current lines offered
		// why is the following done twice (see notifyScoring)?
		this.pTransitSchedule = new TransitScheduleFactoryImpl().createTransitSchedule();
		for (TransitStopFacility stop : this.pStopsOnly.getFacilities().values()) {
			this.pTransitSchedule.addStopFacility(stop);
		}
		for (Operator operator : this.operators) {
			this.pTransitSchedule.addTransitLine(operator.getCurrentTransitLine());
		}

		// Reset the franchise system
		this.franchise.reset(this.operators);
		this.routeOverlap.reset(this.operators);
	}

	void notifyScoring(ScoringEvent event) {

		Map<Id<Vehicle>, PScoreContainer> driverId2ScoreMap = this.scorePlansHandler.getDriverId2ScoreMap();
		for (Operator operator : this.operators) {
			operator.score(driverId2ScoreMap);
		}

		// why is the following done twice (see notifyIterationstarts)?
		this.pTransitSchedule = new TransitScheduleFactoryImpl().createTransitSchedule();
		for (TransitStopFacility stop : this.pStopsOnly.getFacilities().values()) {
			this.pTransitSchedule.addStopFacility(stop);
		}
		for (Operator operator : this.operators) {
			this.pTransitSchedule.addTransitLine(operator.getCurrentTransitLine());
		}

		writeScheduleToFile(this.pTransitSchedule, event.getServices().getControlerIO().getIterationFilename(event.getIteration(), "transitScheduleScored.xml.gz"));
	}

	private void handleBankruptOperators(int iteration) {

		LinkedList<Operator> operatorsToKeep = new LinkedList<>();
		int operatorsProspecting = 0;
		int operatorsInBusiness = 0;
		int operatorsBankrupt = 0;

		// Get operators with positive budget
		for (Operator operator : this.operators) {
			if(operator.getOperatorState().equals(OperatorState.PROSPECTING)){
				operatorsToKeep.add(operator);
				operatorsProspecting++;
			}

			if(operator.getOperatorState().equals(OperatorState.INBUSINESS)){
				operatorsToKeep.add(operator);
				operatorsInBusiness++;
			}

			if(operator.getOperatorState().equals(OperatorState.BANKRUPT)){
				operatorsBankrupt++;
			}
		}

		// get the number of new operators
		int numberOfNewOperators = operatorsBankrupt;

		if(this.pConfig.getUseAdaptiveNumberOfOperators()){
			// adapt the number of operators by calculating the exact number necessary
			numberOfNewOperators = (int) (operatorsInBusiness * (1.0/this.pConfig.getShareOfOperatorsWithProfit() - 1.0) + 0.0000000000001) - operatorsProspecting;
		}

		// delete bankrupt ones
		this.operators = operatorsToKeep;

		if (this.pConfig.getDisableCreationOfNewOperatorsInIteration() > iteration) {
			// recreate all other
			LinkedList<Operator> newOperators1 = this.operatorInitializer.createAdditionalOperators(this.strategyManager, iteration, numberOfNewOperators);
			this.operators.addAll(newOperators1);

			// too few operators in play, increase to the minimum specified in the config
			LinkedList<Operator> newOperators2 = this.operatorInitializer.createAdditionalOperators(this.strategyManager, iteration, (this.pConfig.getNumberOfOperators() - this.operators.size()));
			this.operators.addAll(newOperators2);

			// all operators are in business, increase by one to ensure minimal mutation
			if (this.operators.size() == operatorsInBusiness) {
				LinkedList<Operator> newOperators3 = this.operatorInitializer.createAdditionalOperators(this.strategyManager, iteration, 1);
				this.operators.addAll(newOperators3);
			}
		}
	}

	TransitSchedule getpTransitSchedule() {
		return this.pTransitSchedule;
	}

	public List<Operator> getOperators() {
		return operators;
	}

	private void writeScheduleToFile(TransitSchedule schedule, String iterationFilename) {
		TransitScheduleWriterV1 writer = new TransitScheduleWriterV1(schedule);
		writer.write(iterationFilename);		
	}
}