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

package org.matsim.contrib.minibus.scoring;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.minibus.fare.StageContainer;
import org.matsim.contrib.minibus.fare.TicketMachineI;
import org.matsim.vehicles.Vehicle;

/**
 * Simple container class collecting all incomes and expenses for one single vehicle.
 * 
 * @author aneumann
 *
 */
public final class PScoreContainer {
	
	@SuppressWarnings("unused")
	private final static Logger log = Logger.getLogger(PScoreContainer.class);

	private final Id<Vehicle> vehicleId;
	private final TicketMachineI ticketMachine;
	private boolean isFirstTour = true;
	
	private int servedTrips = 0;
	private int numberOfSubsidizedTrips = 0;
	private double costs = 0;
	private double earnings = 0;

	private double totalMeterDriven = 0.0;
	private double totalTimeDriven = 0.0;
	private double passengerKilometer = 0.0;

	private double amountOfSubsidies;
	
	public PScoreContainer(Id<Vehicle> vehicleId, TicketMachineI ticketMachine) {
		this.vehicleId = vehicleId;
		this.ticketMachine = ticketMachine;
	}

	public void handleStageContainer(StageContainer stageContainer) {
		this.servedTrips++;
		this.amountOfSubsidies += this.ticketMachine.getAmountOfSubsidies(stageContainer);
		if(this.ticketMachine.isSubsidized(stageContainer))
			this.numberOfSubsidizedTrips++;
		this.passengerKilometer += this.ticketMachine.getPassengerDistanceKilometer(stageContainer);
		this.earnings += this.ticketMachine.getFare(stageContainer);
	}

	public void handleOperatorCostContainer(OperatorCostContainer operatorCostContainer) {
		//if (this.isFirstTour) {
		//	this.costs += operatorCostContainer.getFixedCostPerDay();
		//	this.isFirstTour = false;
		//}
		this.totalMeterDriven  += operatorCostContainer.getTotalMeterDriven();
		this.totalTimeDriven += operatorCostContainer.getTotalTimeDriven();
		this.costs += operatorCostContainer.getRunningCostDistance();
		this.costs += operatorCostContainer.getRunningCostTime();
	}

	public double getTotalRevenue(){
		return this.earnings - this.costs;
	}
	
	public double getTotalRevenuePerPassenger(){
		if(this.servedTrips == 0){
			return Double.NaN;
		} else {
			return (this.earnings - this.costs) / this.servedTrips;
		}
	}
	
	public int getTripsServed(){
		return this.servedTrips;
	}
	
	public double getTotalMeterDriven()	{
		return this.totalMeterDriven;
	}
	
	public double getTotalTimeDriven()	{
		return this.totalTimeDriven;
	}
	
	public int getNumberOfSubsidizedTrips()	{
		return this.numberOfSubsidizedTrips;
	}
	
	public double getAmountOfSubsidies()	{
		return this.amountOfSubsidies;
	}
	
	public double getTotalPassengerKilometer()	{
		return this.passengerKilometer;
	}
	
	@Override
	public String toString() {
		return "Paratransit vehicle " + this.vehicleId.toString() + " served " + this.servedTrips + " trips spending a total of " + this.costs + " vs. " + this.earnings + " earnings";
	}
}
