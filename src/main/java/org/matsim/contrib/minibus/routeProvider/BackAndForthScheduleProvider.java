package org.matsim.contrib.minibus.routeProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.minibus.PConfigGroup.PVehicleSettings;
import org.matsim.contrib.minibus.genericUtils.GridNode;
import org.matsim.contrib.minibus.operator.Operator;
import org.matsim.contrib.minibus.operator.PPlan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.filter.NetworkLinkFilter;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.Dijkstra;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

/**
 * Back-And-Forth route provider. Purpose: creates to routes for each plan -> one for the route forth and one
 * for the route back
 * The algorithm has been tested and applied for the Zurich scenario. However, we expect that changes are necessary
 * to run it in a different scenario
 *  
 * @author manserpa
 *
 */

final class BackAndForthScheduleProvider implements PRouteProvider{

	private final static Logger log = Logger.getLogger(BackAndForthScheduleProvider.class);
	public final static String NAME = "BackAndForthScheduleProvider";
	
	private final Network net;
	private final LeastCostPathCalculator routingAlgo;
	private final TransitSchedule scheduleWithStopsOnly;
	private final RandomStopProvider randomStopProvider;
	private final RandomPVehicleProvider randomPVehicleProvider;
	private final LinkedHashMap<Id<Link>, TransitStopFacility> linkId2StopFacilityMap;
	private final double vehicleMaximumVelocity;
	private final double planningSpeedFactor;
	private final double driverRestTime;
	
	private final TimeAwareComplexCircleScheduleProviderHandler handler;
	private final String transportMode;
	private final Collection<PVehicleSettings> pVehicleSettings;
	private PPlan pOperatorPlan;
	private ArrayList<TransitStopFacility> stopsServedBackPattern;
	
	public BackAndForthScheduleProvider(TransitSchedule scheduleWithStopsOnly, Network network, RandomStopProvider randomStopProvider, RandomPVehicleProvider randomPVehicleProvider, double vehicleMaximumVelocity, double planningSpeedFactor, double driverRestTime, String pIdentifier, EventsManager eventsManager, final String transportMode, Collection<PVehicleSettings> pVehicleSettings) {

		this.net = network;
		
		this.pVehicleSettings = pVehicleSettings;
		this.randomPVehicleProvider = randomPVehicleProvider;
		this.scheduleWithStopsOnly = scheduleWithStopsOnly;
		

		FreespeedTravelTimeAndDisutility tC = new FreespeedTravelTimeAndDisutility(-6.0, 0.0, 0.0); // Here, it may make sense to use the variable cost parameters given in the config. Ihab/Daniel may'14
		this.routingAlgo = new DijkstraFactory().createPathCalculator(this.net, tC, tC);
		@SuppressWarnings("serial")
		Set<String> modes =  new HashSet<String>(){{
			// this is the networkmode and explicitly not the transportmode
			add(TransportMode.car);
//			add(TransportMode.pt);
			}};
		((Dijkstra)this.routingAlgo).setModeRestriction(modes);
		
		// register all stops by their corresponding link id
		this.linkId2StopFacilityMap = new LinkedHashMap<>();
		for (TransitStopFacility stop : this.scheduleWithStopsOnly.getFacilities().values()) {
			if (stop.getLinkId() == null) {
				log.warn("There is a potential paratransit stop without a corresponding link id. Shouldn't be possible. Check stop " + stop.getId());
			} else {
				this.linkId2StopFacilityMap.put(stop.getLinkId(), stop);
			}
		}
		
		this.randomStopProvider = randomStopProvider;
		this.vehicleMaximumVelocity = vehicleMaximumVelocity;
		this.planningSpeedFactor = planningSpeedFactor;
		this.driverRestTime = driverRestTime;
		this.handler = new TimeAwareComplexCircleScheduleProviderHandler(pIdentifier);
		eventsManager.addHandler(this.handler);
		this.transportMode = transportMode;
	}
	
	@Override
	public TransitLine createTransitLineFromOperatorPlan(Id<Operator> operatorId, PPlan plan){
		this.pOperatorPlan = plan;
		return this.createTransitLine(Id.create(operatorId, TransitLine.class), plan.getStartTime(), plan.getEndTime(), plan.getNVehicles(), plan.getStopsToBeServed(), plan.getPVehicleType(), Id.create(plan.getId(), TransitRoute.class), plan.getId());
	}
	
	private TransitLine createTransitLine(Id<TransitLine> pLineId, double startTime, double endTime, int numberOfVehicles, ArrayList<TransitStopFacility> stopsToBeServed, String pVehicleType, Id<TransitRoute> routeId, Id<PPlan> planId){
		
		// initialize
		TransitLine line = this.scheduleWithStopsOnly.getFactory().createTransitLine(pLineId);	
		this.stopsServedBackPattern = new ArrayList<>();
		
		Id<TransitRoute> routeIdBack = Id.create(pLineId + "-" + routeId + "-Back", TransitRoute.class);
		Id<TransitRoute> routeIdForth = Id.create(pLineId + "-" + routeId + "-Forth", TransitRoute.class);
		
		// create the first route
		TransitRoute transitRouteBack = createRoute(routeIdBack, stopsToBeServed, pVehicleType, planId, "back");
		
		// create the other route containing the stops on the other side of the road in a reversed order
		ArrayList<TransitStopFacility> stopsToBeServedReversed = new ArrayList<>();
		ListIterator<TransitStopFacility> listIterator = this.stopsServedBackPattern.listIterator(this.stopsServedBackPattern.size());
		while(listIterator.hasPrevious()) {

			String[] stopName = listIterator.previous().getId().toString().split("_");
			
			StringBuilder builder = new StringBuilder();
			for(int i = 0; i < stopName.length; i++)	{
				if(i != stopName.length - 1) {
					builder.append(stopName[i] + "_");
				} else {
					if(stopName[i].equals("A"))
						builder.append("B");
					if(stopName[i].equals("B"))
						builder.append("A");
				}
			}
			String str = builder.toString();
			
			Id<TransitStopFacility> reversedStopID = Id.create(str, TransitStopFacility.class);

			stopsToBeServedReversed.add(this.scheduleWithStopsOnly.getFacilities().get(reversedStopID));
		}
		
		TransitRoute transitRouteForth = createRoute(routeIdForth, stopsToBeServedReversed, pVehicleType, planId, "forth");
		
		// register route
		line.addRoute(transitRouteBack);
		line.addRoute(transitRouteForth);
		
		// ----------------------------------------------------------
		// manserpa: the following code schedules the departures for the back and forth approach. It assumes full cicles (back and forth)
		// !! to be honest, I don't know how MATSim handles the delays of the vehicles. If a vehicle on the way forth is delayed, I don't know if it is delayed on the way back as well
		// ----------------------------------------------------------
		
		int n = 0;
		
		// driver rest after each route
		int headway = (int) (2 * this.driverRestTime + transitRouteBack.getStops().get(transitRouteBack.getStops().size() - 1).getDepartureOffset() + 
				transitRouteForth.getStops().get(transitRouteForth.getStops().size() - 1).getDepartureOffset()) / numberOfVehicles;
		
		// possibility to introduce a maximal frequency
		// headway = Math.max(5*60, headway);
		this.pOperatorPlan.setHeadway(headway);
		for (int i = 0; i < numberOfVehicles; i++) {
			for (double j = startTime + i * headway; j <= endTime; ) {
				Departure departureBack = this.scheduleWithStopsOnly.getFactory().createDeparture(Id.create(n, Departure.class), j);
				departureBack.setVehicleId(Id.create(pLineId + "-" + routeId + "-" + n +"_" + pVehicleType + "-Back-" + i, Vehicle.class));
				transitRouteBack.addDeparture(departureBack);
				j += transitRouteBack.getStops().get(transitRouteBack.getStops().size() - 1).getDepartureOffset() + this.driverRestTime;
				
				Departure departureForth = this.scheduleWithStopsOnly.getFactory().createDeparture(Id.create(n, Departure.class), j);
				departureForth.setVehicleId(Id.create(pLineId + "-" + routeId + "-" + n +"_" + pVehicleType + "-Forth-" + i, Vehicle.class));
				transitRouteForth.addDeparture(departureForth);
				j += transitRouteForth.getStops().get(transitRouteForth.getStops().size() - 1).getDepartureOffset() + this.driverRestTime;
				n++;
			}
		}		
		
		return line;
		
	}
	
	private TransitRoute createRoute(Id<TransitRoute> routeID, ArrayList<TransitStopFacility> stopsToBeServed, String pVehicleType, Id<PPlan> planId, String routePattern){
		
		ArrayList<TransitStopFacility> tempStopsToBeServed = new ArrayList<>();
		HashSet<String> gridStopHashSet = new HashSet<>();
		
		// locks all grids in which stop is located
		// and puts these gridIds into a HashSet
		for (TransitStopFacility transitStopFacility : stopsToBeServed) {
			tempStopsToBeServed.add(transitStopFacility);

			// TODO: hard-coded
			String gridNodeId = GridNode.getGridNodeIdForCoord(transitStopFacility.getCoord(), 300);
			gridStopHashSet.add(gridNodeId);
		}
		// this would be the last stop, not necessary anymore
		// tempStopsToBeServed.add(stopsToBeServed.get(0));
		
		// create links - network route		
		Id<Link> startLinkId = null;
		Id<Link> lastLinkId = null;
		
		List<Link> links = new LinkedList<>();
		// for each stop
		for (TransitStopFacility stop : tempStopsToBeServed) {
			if(startLinkId == null){
				startLinkId = stop.getLinkId();
			}
			if(lastLinkId != null){
				links.add(this.net.getLinks().get(lastLinkId));
				Path path = this.routingAlgo.calcLeastCostPath(this.net.getLinks().get(lastLinkId).getToNode(), this.net.getLinks().get(stop.getLinkId()).getFromNode(), 0.0, null, null);

				for (Link link : path.links) {
					links.add(link);
				}
			}
			lastLinkId = stop.getLinkId();
		}
		links.remove(0);
		NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(startLinkId, NetworkUtils.getLinkIds(links), lastLinkId);
		//route.setLinkIds(startLinkId, NetworkUtils.getLinkIds(links), lastLinkId);

		// get stops at Route
		List<TransitRouteStop> stops = new LinkedList<>();
		double runningTime = 60.0;
		
		
		// get capacity of the vehicle
		double capacity = 0.0;
		
		for (PVehicleSettings pVS : this.pVehicleSettings) {
			
            if (pVehicleType.equals(pVS.getPVehicleName())) {
            	// manserpa: !!attention: the factor 10 is because of the downscaled scenario. Not really nice because it is hard-coded
            	capacity = pVS.getCapacityPerVehicle() * 10;
            }
        }
		
		// first stop
		TransitRouteStop routeStop;
		routeStop = this.scheduleWithStopsOnly.getFactory().createTransitRouteStop(tempStopsToBeServed.get(0), runningTime, runningTime);
		routeStop.setAwaitDepartureTime(true);
		stops.add(routeStop);
		
		
		// now the iteration process begins
		ArrayList<TransitStopFacility> tempStopsToBeServedNew = new ArrayList<>();
		tempStopsToBeServedNew.add(tempStopsToBeServed.get(0));
		
		int k = 1;
		
		// problem: with the new code -> the stop sequence is probably not the same for the same transitroute
		// thus, the operator can not rely on the times from the last iteration
		boolean isSameStopSequenceAsLastIteration = true;
		
		// additional stops
		for (Link link : links) {
			
			// accumulate the scheduled running time
			runningTime += (link.getLength() / (Math.min(this.vehicleMaximumVelocity, link.getFreespeed()) * this.planningSpeedFactor));
			
			// is there any stop facility on that link?
			if(this.linkId2StopFacilityMap.get(link.getId()) == null){
				continue;
			}
			
			// this is true if the operator really wants to serve a stop on this link
			if (tempStopsToBeServed.get(k).getLinkId().equals(link.getId()))	{

				if(isSameStopSequenceAsLastIteration)	{
					if(tempStopsToBeServed.get(k).equals(this.handler.getServedStopsInLastIteration(routeID, stops.size())))	{
						double runningTimeMod = modifyRunningTimeAccordingToTheLastIterationIfPossible(runningTime, 
								this.handler.getOffsetForRouteAndStopNumber(routeID, stops.size()));
						if (runningTimeMod > runningTime)
							runningTime = runningTimeMod;
					}
					else	{
						isSameStopSequenceAsLastIteration = false;
					}
				}
				
				routeStop = this.scheduleWithStopsOnly.getFactory().createTransitRouteStop(tempStopsToBeServed.get(k), 
						(int) runningTime, ((int) runningTime) + getMinStopTime(capacity));
				runningTime += getMinStopTime(capacity);
				
				tempStopsToBeServedNew.add(tempStopsToBeServed.get(k));
				
				routeStop.setAwaitDepartureTime(true);
				stops.add(routeStop);
				
				k++;
				
			}
			// now this thing provides information about stops passed by anyway
			
			else if(routePattern.equals("back"))	{
				
				String gridNode = GridNode.getGridNodeIdForCoord(this.linkId2StopFacilityMap.get(link.getId()).getCoord(), 300);
				
				if(gridStopHashSet.contains(gridNode))	{
					continue;
				}
				
				// hier muss jetzt geprüft werden, ob die Anzahl Aktivitäten überdurchschnittlich hoch sind
				if(this.randomStopProvider.hasHighNumberOfActivitiesInGrid(gridNode))	{

					if(isSameStopSequenceAsLastIteration)	{
						if(this.linkId2StopFacilityMap.get(link.getId()).equals(this.handler.getServedStopsInLastIteration(routeID, stops.size())))	{
							double runningTimeMod = modifyRunningTimeAccordingToTheLastIterationIfPossible(runningTime, 
									this.handler.getOffsetForRouteAndStopNumber(routeID, stops.size()));
							if (runningTimeMod > runningTime)
								runningTime = runningTimeMod;
						}
						else	{
							isSameStopSequenceAsLastIteration = false;
						}
					}
					
					routeStop = this.scheduleWithStopsOnly.getFactory().createTransitRouteStop(this.linkId2StopFacilityMap.get(link.getId()), 
							(int) runningTime, ((int) runningTime) + getMinStopTime(capacity));
					runningTime += getMinStopTime(capacity);
					
					tempStopsToBeServedNew.add(this.linkId2StopFacilityMap.get(link.getId()));
					
					routeStop.setAwaitDepartureTime(true);
					stops.add(routeStop);
					
					gridStopHashSet.add(gridNode);
				}
			}
			
		}
		
		// last stop
		
		runningTime += (this.net.getLinks().get(tempStopsToBeServed.get(tempStopsToBeServed.size()-1).getLinkId()).getLength() / (Math.min(this.vehicleMaximumVelocity, this.net.getLinks().get(tempStopsToBeServed.get(tempStopsToBeServed.size()-1).getLinkId()).getFreespeed()) * this.planningSpeedFactor));

		if(isSameStopSequenceAsLastIteration)	{
			if(tempStopsToBeServed.get(tempStopsToBeServed.size()-1).equals(this.handler.getServedStopsInLastIteration(routeID, stops.size())))	{
				double runningTimeMod = modifyRunningTimeAccordingToTheLastIterationIfPossible(runningTime, this.handler.getOffsetForRouteAndStopNumber(routeID, stops.size()));
				if (runningTimeMod > runningTime)
					runningTime = runningTimeMod;
			}
		}
		
		routeStop = this.scheduleWithStopsOnly.getFactory().createTransitRouteStop(tempStopsToBeServed.get(tempStopsToBeServed.size()-1),
				(int) runningTime, ((int) runningTime) + getMinStopTime(capacity));
		routeStop.setAwaitDepartureTime(true);
		stops.add(routeStop);
		
		tempStopsToBeServedNew.add(tempStopsToBeServed.get(tempStopsToBeServed.size()-1));
		if(routePattern.equals("back"))
			this.stopsServedBackPattern = tempStopsToBeServedNew;
		
		TransitRoute transitRoute = this.scheduleWithStopsOnly.getFactory().createTransitRoute(routeID, route, stops, this.transportMode);
		return transitRoute;
	}
	
	public int getMinStopTime(double capacity){
		// TODO: add more flexibility
		int minStopTime = (int) (0.2 * capacity);
		return minStopTime;
	}

	@Override
	public TransitStopFacility getRandomTransitStop(int currentIteration){
		return this.randomStopProvider.getRandomTransitStop(currentIteration);
	}
	
	@Override
	public TransitStopFacility drawRandomStopFromList(List<TransitStopFacility> choiceSet) {
		return this.randomStopProvider.drawRandomStopFromList(choiceSet);
	}

	@Override
	public TransitLine createEmptyLineFromOperator(Id<Operator> id) {
		return this.scheduleWithStopsOnly.getFactory().createTransitLine(Id.create(id, TransitLine.class));
	}

	@Override
	public Collection<TransitStopFacility> getAllPStops() {
		return this.scheduleWithStopsOnly.getFacilities().values();
	}
	
	private double modifyRunningTimeAccordingToTheLastIterationIfPossible(double runningTime, double offsetFromLastIteration){
		if (offsetFromLastIteration != -Double.MAX_VALUE) {
			runningTime = offsetFromLastIteration;
		}
		return runningTime;
	}

	@Override
	public String getRandomPVehicle() {
		return this.randomPVehicleProvider.getRandomPVehicle();
	}

	@Override
	public String getSmallestPVehicle() {
		return this.randomPVehicleProvider.getSmallestPVehicle();
	}	
}