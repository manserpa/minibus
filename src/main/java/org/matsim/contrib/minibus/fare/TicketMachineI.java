package org.matsim.contrib.minibus.fare;

import java.util.HashMap;
import java.util.HashSet;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public interface TicketMachineI {

	double getFare(StageContainer stageContainer);
	
	boolean isSubsidized(StageContainer stageContainer);
	
	double getAmountOfSubsidies(StageContainer stageContainer);
	
	void setActBasedSubs(HashMap<Id<TransitStopFacility>, Double> actBasedSubs);
	
	double getPassengerDistanceKilometer(StageContainer stageContainer);

}