package org.matsim.contrib.minibus.routeProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.matsim.contrib.minibus.PConfigGroup;
import org.matsim.contrib.minibus.PConfigGroup.PVehicleSettings;
import org.matsim.core.gbl.MatsimRandom;

public class RandomPVehicleProvider {
	
	//private final static Logger log = Logger.getLogger(RandomPVehicleProvider.class);
	
	private final Collection<PVehicleSettings> pVehicleSettings;

	public RandomPVehicleProvider(PConfigGroup pConfig){
		this.pVehicleSettings = pConfig.getPVehicleSettings();
	}
	
	public String getRandomPVehicle() {
		
		List<String> pVehicleTypes = new ArrayList<>();
		
		for (PVehicleSettings settings : this.pVehicleSettings) {
			pVehicleTypes.add(settings.getPVehicleName());
		}
		
		int i = pVehicleTypes.size();
		
		// returns a random vehicle type
		for (String pVehicleType : pVehicleTypes) {
			if(MatsimRandom.getRandom().nextDouble() < 1.0 / i){
				return pVehicleType;
			}
			i--;
		}
		
		return null;
	}
	
	public String getSmallestPVehicle() {
		
		double minCapacity = 1000;
		
		for (PVehicleSettings settings : this.pVehicleSettings) {
			
			double capacity = settings.getCapacityPerVehicle();
			
			if (capacity < minCapacity)
				minCapacity = capacity;
			
		}
		
		for (PVehicleSettings settings : this.pVehicleSettings) {
			
			if(settings.getCapacityPerVehicle() == minCapacity)
				return settings.getPVehicleName();
			
		}
		
		return null;
	}
}
