package org.matsim.contrib.minibus.replanning;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.minibus.PConfigGroup;
import org.matsim.contrib.minibus.PConstants;
import org.matsim.contrib.minibus.PConfigGroup.PVehicleSettings;
import org.matsim.contrib.minibus.operator.Operator;
import org.matsim.contrib.minibus.operator.PPlan;
import org.matsim.contrib.minibus.stats.operatorLogger.LogElement;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

/**
 * Provide a new vehicle type. This is calibrated to the vehicle sizes used in the Zurich application.
 * See Manser, P. (2017) for more information.
 *
 * @author manserpa
 */
public final class ChooseVehicleType extends AbstractPStrategyModule {
	
	private final static Logger log = Logger.getLogger(ChooseVehicleType.class);
	public static final String STRATEGY_NAME = "ChooseVehicleType";
	private PConfigGroup pConfig;
	private BufferedWriter writer;
	private String outputDir;
	
	public ChooseVehicleType(ArrayList<String> parameter)  {
		super();
		if(parameter.size() != 0){
			log.error("No parameter needed here");
		}
	}

	@Override
	public PPlan run(Operator operator) {

		PPlan oldPlan = operator.getBestPlan();
		
		// calculate the timetable intervals of the old plan (maybe this can be easier done)
		Id<TransitRoute> routeIdForth = Id.create(Id.create(operator.getId(), TransitLine.class) + "-" + Id.create(oldPlan.getId() + "-Forth", TransitRoute.class), TransitRoute.class);
		Id<TransitRoute> routeIdBack = Id.create(Id.create(operator.getId(), TransitLine.class) + "-" + Id.create(oldPlan.getId() + "-Back", TransitRoute.class), TransitRoute.class);
		TransitRoute routeForth = oldPlan.getLine().getRoutes().get(routeIdForth);
		TransitRoute routeBack = oldPlan.getLine().getRoutes().get(routeIdBack);
		double numberOfVehiclesOld = oldPlan.getTotalPassengerKilometer() / oldPlan.getPassengerKilometerPerVehicle();
		double headway = (2 * this.pConfig.getDriverRestTime() + routeBack.getStops().get(routeBack.getStops().size() - 1).getDepartureOffset().seconds() +
				routeForth.getStops().get(routeForth.getStops().size() - 1).getDepartureOffset().seconds()) / numberOfVehiclesOld;
		//double headway =  (this.pConfig.getDriverRestTime() + route.getStops().get(route.getStops().size() - 1).getDepartureOffset()) / numberOfVehiclesOld;
		//double headway = oldPlan.getHeadway();
		double vehiclesPerHourOld = 3600 / headway;
		
		
		String pVehicleTypeOld = oldPlan.getPVehicleType();
		String pVehicleTypeNew = pVehicleTypeOld;
		
		// choose the desired vehicle type (may not be the best)
		while(pVehicleTypeOld.equals(pVehicleTypeNew))	{
			pVehicleTypeNew = operator.getRouteProvider().getRandomPVehicle();
		}
		
		// create new plan with the same route and operation time as the old plan and set the vehicle type to the new vehicle type
		PPlan newPlan = new PPlan(operator.getNewPlanId(), this.getStrategyName(), oldPlan.getId());
		
		newPlan.setStartTime(oldPlan.getStartTime());
		newPlan.setEndTime(oldPlan.getEndTime());
		newPlan.setStopsToBeServed(oldPlan.getStopsToBeServed());
		
		newPlan.setScore(0.0);
		newPlan.setPVehicleType(pVehicleTypeNew);
		
		
		// if the score is 0, the operator already changed the vehicle type and this should only happen once
		if(oldPlan.getScore() != 0)	{
		
			// get costs of old and new vehicle type
			double costsOld = 0.0;
			double costsNew = 0.0;
			double capacityOld = 0.0;
			double capacityNew = 0.0;
			double earningsOld = 0.0;
			double earningsNew = 0.0;
			
			// modify that according to the departure intervals
			double occupancy = oldPlan.getPassengerKilometerPerVehicle() / oldPlan.getTotalKilometersDrivenPerVehicle();
			
			// this is for the decision between old and new vehicle type
			double nVehiclesOld = oldPlan.getNVehicles();
			
			for (PVehicleSettings pVS : this.pConfig.getPVehicleSettings()) {
	            if (pVehicleTypeOld.equals(pVS.getPVehicleName())) {
	            	costsOld = pVS.getCostPerKilometer() * oldPlan.getTotalKilometersDrivenPerVehicle() + 
	            			pVS.getCostPerHour() * oldPlan.getTotalHoursDrivenPerVehicle();
	            	earningsOld = pVS.getEarningsPerKilometerAndPassenger() * oldPlan.getTotalKilometersDrivenPerVehicle();
	            	capacityOld = pVS.getCapacityPerVehicle();
	            }
	            if (pVehicleTypeNew.equals(pVS.getPVehicleName())) {
	            	costsNew = pVS.getCostPerKilometer() * oldPlan.getTotalKilometersDrivenPerVehicle() + 
	            			pVS.getCostPerHour() * oldPlan.getTotalHoursDrivenPerVehicle();
	            	earningsNew = pVS.getEarningsPerKilometerAndPassenger() * oldPlan.getTotalKilometersDrivenPerVehicle();
	            	capacityNew = pVS.getCapacityPerVehicle();
	            }
	        }
			
			double totalCostsOld = costsOld * nVehiclesOld;
			double totalCostsNew = 0.0;
			
			// Kapazität über Kosten ausgleichen. Der Betreiber soll nachher immer weniger bezahlen.
			// ab hier gilt die Entscheidung: setze ich beispielsweise einen Standardbus ein oder einen Minibus??
			do	{
				nVehiclesOld = nVehiclesOld - 1;
				totalCostsOld = costsOld * nVehiclesOld;
				
				newPlan.setNVehicles(newPlan.getNVehicles() + 1);
				totalCostsNew = costsNew * newPlan.getNVehicles();	
			} while (totalCostsNew < totalCostsOld);
			
			int nVehicles = 0;
			if (oldPlan.getNVehicles() == 0)	{
				nVehicles = 1;
			}
			else {
				nVehicles = oldPlan.getNVehicles();
			}
			
			double seatCostsOld = costsOld * nVehicles * capacityOld;
			double seatEarningsOld = earningsOld * nVehicles * capacityOld;
			
			double seatCostsNew = costsNew * newPlan.getNVehicles() * capacityNew;
			double seatEarningsNew = earningsNew * newPlan.getNVehicles() * capacityNew;
			
			// add time dependency and encourage the operators to operator longer 
			double timeFactor = 1.0;
			
			double operationTime = (oldPlan.getEndTime() - oldPlan.getStartTime()) / 3600;
			if (operationTime <= 3)	
				timeFactor = 1.0;
			else if(operationTime >= 12)
				timeFactor = 0.7;
			else
				timeFactor = -.04444444 * operationTime + 1.133333333; 
			
			
			// calculate the marginal occupancy
			double marginalOccupancy = timeFactor * (seatCostsNew - seatCostsOld) / (seatEarningsNew - seatEarningsOld);
			
			
			// calculation of the EXPECTED new occupancy 
			
			//double headwayNew =  (this.pConfig.getDriverRestTime() + route.getStops().get(route.getStops().size() - 1).getDepartureOffset()) / newPlan.getNVehicles();
			double headwayNew = (2 * this.pConfig.getDriverRestTime() + routeBack.getStops().get(routeBack.getStops().size() - 1).getDepartureOffset().seconds() +
					routeForth.getStops().get(routeForth.getStops().size() - 1).getDepartureOffset().seconds()) / newPlan.getNVehicles();
			double vehiclesPerHourNew = 3600 / headwayNew;

			newPlan.setHeadway(headwayNew);
			
			double demandRatioOld;
			if(vehiclesPerHourOld < 1)	{
				demandRatioOld = vehiclesPerHourOld;
			}
			else if	(vehiclesPerHourOld > 6)	{
				demandRatioOld = 2.2 * Math.log10(6) + 1;
			}
			else	{
				demandRatioOld = 2.2 * Math.log10(vehiclesPerHourOld) + 1;
			}
			
			double demandRatioNew;
			if(vehiclesPerHourNew < 1)	{
				demandRatioNew = vehiclesPerHourNew;
			}
			else if	(vehiclesPerHourNew > 6)	{
				demandRatioNew = 2.2 * Math.log10(6) + 1;
			}
			else	{
				demandRatioNew = 2.2 * Math.log10(vehiclesPerHourNew) + 1;
			}
			
 			double demandRatio = demandRatioNew / demandRatioOld;
			
			// now I can calculate the expected passenger kilometers with the new Takt
			double expectedPaxKilometer = oldPlan.getTotalPassengerKilometer() * demandRatio;
			
			double occupancyNew = expectedPaxKilometer / (oldPlan.getTotalKilometersDrivenPerVehicle() * newPlan.getNVehicles());
			
			/*
			log.info(" ");
			log.info("Operator " + operator.getId() + " Old Plan " + oldPlan.getId());
			log.info("Old vehicle type " + pVehicleTypeOld + " number of vehicles: " +nVehicles);
			log.info("New vehicle type " + pVehicleTypeNew + " number of vehicles: " +newPlan.getNVehicles());
			log.info("Marginal Occupancy " + marginalOccupancy);
			log.info("Occupancy " + occupancy);
			log.info("Excp. Occupancy " + occupancyNew);
			log.info("Vehicles Per Hour Old " + vehiclesPerHourOld);
			log.info("PaxKilometer Old " + oldPlan.getTotalPassengerKilometer());
			log.info("Vehicles Per Hour New " + vehiclesPerHourNew);
			log.info("PaxKilometer Expected " + expectedPaxKilometer);
			*/
			
			double deltaOccupancy = occupancyNew - marginalOccupancy;
			
			// Unterscheiden zwischen Up-/Downgrade
			if(capacityOld < capacityNew)	{
				//it's an upgrade
				if (doChangeVehicleType(deltaOccupancy, true, false))	{
					// reset the old plan
					//log.info("Will change: true");
					oldPlan.setNVehicles(0);
					oldPlan.setScore(0.0);
					
					newPlan.setLine(operator.getRouteProvider().createTransitLineFromOperatorPlan(operator.getId(), newPlan));
					return newPlan;
				}
			}
			if(capacityOld > capacityNew)	{
				//it's a downgrade
				if (doChangeVehicleType((-1 * deltaOccupancy), false, true))	{
					// reset the old plan
					//log.info("Will change: true");
					oldPlan.setNVehicles(0);
					oldPlan.setScore(0.0);
					
					newPlan.setLine(operator.getRouteProvider().createTransitLineFromOperatorPlan(operator.getId(), newPlan));
					return newPlan;
				}
			}
		}
		
		newPlan.setNVehicles(0);
		return newPlan;
		
	}
	
	private boolean doChangeVehicleType( double deltaOccupancy, boolean isUpgrade, boolean isDowngrade ) {
		
		double probabilityToChange = 0.0;		
		
		if (isUpgrade)
			probabilityToChange = 1 / ( 1 + 5 * Math.exp(-deltaOccupancy / 2));
		else if (isDowngrade)
			probabilityToChange = 1 / ( 1 + 15 * Math.exp(-deltaOccupancy / 2.2));
		
		//log.info("Probability to change: " + probabilityToChange);
			
		double rndTreshold = MatsimRandom.getRandom().nextDouble();
		//log.info("Treshold: " + rndTreshold);
		if(probabilityToChange > rndTreshold)	{
			writeLine(deltaOccupancy, isUpgrade);
			return true;
		}
		
		return false;
	}	
	
	public void writeLine(final double deltaOccupancy, final boolean wasUpgrade) {
		
		this.outputDir = this.outputDir + PConstants.statsOutputFolder;
		
		if(this.writer == null){
			this.writer = IOUtils.getBufferedWriter(this.outputDir + "pChooseVehicleTypeLogger.txt");
			try {
				this.writer.write("DeltaOccupancy; IsUpgrade");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		
		
		try {
			this.writer.newLine();
			this.writer.write(deltaOccupancy + ";" + wasUpgrade);
			this.writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
		
	public void setPConfig(PConfigGroup pConfig) {
		this.pConfig = pConfig;
	}
	
	public void setOutputDir(String outputdir) {
		this.outputDir = outputdir;
	}
	
	public String getStrategyName() {
		return ChooseVehicleType.STRATEGY_NAME;
	}

}