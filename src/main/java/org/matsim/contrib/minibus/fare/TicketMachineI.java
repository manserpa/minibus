package org.matsim.contrib.minibus.fare;

import java.util.HashMap;
import java.util.HashSet;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public interface TicketMachineI {

	double getFare(StageContainer stageContainer);
	
	boolean isSubsidized(StageContainer stageContainer);
	
	double getAmountOfSubsidies(StageContainer stageContainer);
	
	void setSubsidizedStops100(HashSet<String> subsidizedStops);
	
	void setSubsidizedStops150(HashSet<String> subsidizedStops);
	
	void setSubsidizedStops225(HashSet<String> subsidizedStops);
	
	void setSubsidizedStops300(HashSet<String> subsidizedStops);
	
	void setActBasedSubs(HashMap<Id<TransitStopFacility>, Double> actBasedSubs);
	
	double getPassengerDistanceKilometer(StageContainer stageContainer);

}