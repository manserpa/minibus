/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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

package org.matsim.contrib.minibus.fare;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import javax.inject.Inject;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.minibus.PConfigGroup;
import org.matsim.contrib.minibus.PConfigGroup.PVehicleSettings;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * Calculates the fare for a given {@link StageContainer}.
 * 
 * @author aneumann
 *
 */
public final class TicketMachineDefaultImpl implements TicketMachineI {
	
	private final double subsidiesPerBoardingPassenger;
	private final Collection<PVehicleSettings> pVehicleSettings;
	private boolean isSubsidized = false;
	private HashSet<String> subsidizedStops100;
	private double amountOfSubsidies;
	private HashSet<String> subsidizedStops150;
	private HashSet<String> subsidizedStops300;
	private HashSet<String> subsidizedStops225;
	private HashMap<Id<TransitStopFacility>, Double> actBasedSubs;
	
	@Inject public TicketMachineDefaultImpl(PConfigGroup pConfig ) {
		this.pVehicleSettings = pConfig.getPVehicleSettings();
		this.subsidiesPerBoardingPassenger = pConfig.getSubsidiesPerBoardingPassenger();
	}
	
	@Override
	public double getFare(StageContainer stageContainer) {
		
		double earningsPerBoardingPassenger = 0.0;
		double earningsPerMeterAndPassenger = 0.0;
	
		// manserpa: earnings could be set differently for the different vehicle types
		
		for (PVehicleSettings pVS : this.pVehicleSettings) {
            if (stageContainer.getVehicleId().toString().contains(pVS.getPVehicleName())) {
            	earningsPerBoardingPassenger = pVS.getEarningsPerBoardingPassenger();
            	earningsPerMeterAndPassenger = pVS.getEarningsPerKilometerAndPassenger() / 1000.;
            }
        }
        
		
		// subsidy approach by manserpa: the operators get a configurable amount of subsidies after a certain number of iterations, if the passenger
		// boards at a subsidized stop.
		// TODO make the subsidy approach nicer, now everything is hard-coded (move everything into the config.xml)
		
		/*
		if (this.subsidizedStops100.contains(stageContainer.getStopEntered().toString()) && !this.subsidizedStops150.contains(stageContainer.getStopEntered().toString())
				&& !this.subsidizedStops225.contains(stageContainer.getStopEntered().toString()) && !this.subsidizedStops300.contains(stageContainer.getStopEntered().toString()))	{
			this.isSubsidized  = true;
			this.amountOfSubsidies = (int) this.subsidiesPerBoardingPassenger;
			return earningsPerBoardingPassenger + earningsPerMeterAndPassenger * stageContainer.getDistanceTravelledInMeter() + 
					this.subsidiesPerBoardingPassenger;
		}
		if (this.subsidizedStops150.contains(stageContainer.getStopEntered().toString())
				&& !this.subsidizedStops225.contains(stageContainer.getStopEntered().toString()) && !this.subsidizedStops300.contains(stageContainer.getStopEntered().toString()))	{
			this.isSubsidized  = true;
			this.amountOfSubsidies = (int) this.subsidiesPerBoardingPassenger + 5;
			return earningsPerBoardingPassenger + earningsPerMeterAndPassenger * stageContainer.getDistanceTravelledInMeter() + 
					this.subsidiesPerBoardingPassenger + 5;
		}
		if (this.subsidizedStops225.contains(stageContainer.getStopEntered().toString()) && !this.subsidizedStops300.contains(stageContainer.getStopEntered().toString()))	{
			this.isSubsidized  = true;
			this.amountOfSubsidies = (int) this.subsidiesPerBoardingPassenger + 10;
			return earningsPerBoardingPassenger + earningsPerMeterAndPassenger * stageContainer.getDistanceTravelledInMeter() + 
					this.subsidiesPerBoardingPassenger + 10;
		}
		if (this.subsidizedStops300.contains(stageContainer.getStopEntered().toString()))	{
			this.isSubsidized  = true;
			this.amountOfSubsidies = (int) this.subsidiesPerBoardingPassenger + 15;
			return earningsPerBoardingPassenger + earningsPerMeterAndPassenger * stageContainer.getDistanceTravelledInMeter() + 
					this.subsidiesPerBoardingPassenger + 15;
		}
		else {
			this.isSubsidized  = false;
			this.amountOfSubsidies = 0;
			*/
			//return earningsPerBoardingPassenger + earningsPerMeterAndPassenger * stageContainer.getDistanceTravelledInMeter();
		//}


		/*
		this.amountOfSubsidies = 0;
		if (this.actBasedSubs.containsKey(stageContainer.getStopEntered()))	{
			this.isSubsidized  = true;
			this.amountOfSubsidies = this.actBasedSubs.get(stageContainer.getStopEntered());
		}
		*/

		return earningsPerBoardingPassenger + earningsPerMeterAndPassenger * stageContainer.getDistanceTravelledInMeter();

		// new subsidy approach: Eine Schwierigkeit ist, dass eine Linie nur einmal am Tag Subventionen bekommt -> wie macht man das mit dem TimeProvider und dem StopProvider?
	}
	
	@Override
	public void setSubsidizedStops100(HashSet<String> subsidizedStops) {
		this.subsidizedStops100 = subsidizedStops;
	}
	
	@Override
	public void setSubsidizedStops150(HashSet<String> subsidizedStops) {
		this.subsidizedStops150 = subsidizedStops;
	}
	
	@Override
	public void setSubsidizedStops225(HashSet<String> subsidizedStops) {
		this.subsidizedStops225 = subsidizedStops;
	}
	
	@Override
	public void setSubsidizedStops300(HashSet<String> subsidizedStops) {
		this.subsidizedStops300 = subsidizedStops;
	}
	
	@Override
	public void setActBasedSubs(HashMap<Id<TransitStopFacility>, Double> actBasedSubs) {
		this.actBasedSubs = actBasedSubs;
	}
	
	@Override
	public boolean isSubsidized(StageContainer stageContainer) {
		return this.isSubsidized;
	}
	
	@Override
	public double getAmountOfSubsidies(StageContainer stageContainer) {
		return this.amountOfSubsidies;
	}
	
	@Override
	public double getPassengerDistanceKilometer(StageContainer stageContainer) {
		return stageContainer.getDistanceTravelledInMeter() / 1000;
	}
}
