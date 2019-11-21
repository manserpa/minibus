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
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * Calculates the fare for a given {@link StageContainer}.
 * 
 * @author aneumann
 *
 */
public final class TicketMachineDefaultImpl implements TicketMachineI {

	private final Collection<PVehicleSettings> pVehicleSettings;
	private boolean isSubsidized = false;
	private double amountOfSubsidies;
	private HashMap<Id<TransitStopFacility>, Double> actBasedSubs;
	
	@Inject public TicketMachineDefaultImpl(PConfigGroup pConfig ) {
		this.pVehicleSettings = pConfig.getPVehicleSettings();
	}
	
	@Override
	public double getFare(StageContainer stageContainer) {
		
		double earningsPerBoardingPassenger = 0.0;
		double earningsPerMeterAndPassenger = 0.0;
		
		for (PVehicleSettings pVS : this.pVehicleSettings) {
            if (stageContainer.getVehicleId().toString().contains(pVS.getPVehicleName())) {
            	earningsPerBoardingPassenger = pVS.getEarningsPerBoardingPassenger();
            	earningsPerMeterAndPassenger = pVS.getEarningsPerKilometerAndPassenger() / 1000.;
            }
        }

		if (this.actBasedSubs.containsKey(stageContainer.getStopEntered()))	{
			this.isSubsidized  = true;
			this.amountOfSubsidies = this.actBasedSubs.get(stageContainer.getStopEntered());
		}
		else	{
			this.amountOfSubsidies = 0;
			this.isSubsidized = false;
		}

		return earningsPerBoardingPassenger + this.amountOfSubsidies +
				earningsPerMeterAndPassenger * stageContainer.getDistanceTravelledInMeter();
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
