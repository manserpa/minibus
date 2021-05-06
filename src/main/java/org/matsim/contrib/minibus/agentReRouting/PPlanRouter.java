package org.matsim.contrib.minibus.agentReRouting;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.vehicles.Vehicle;

import java.util.List;

/**
 * {@link PlanAlgorithm} responsible for routing all trips of a plan.
 * Activity times are not updated, even if the previous trip arrival time
 * is after the activity end time.
 *
 * @author thibautd
 */
public class PPlanRouter implements PlanAlgorithm, PersonAlgorithm {
    private final TripRouter routingHandler;
    private final ActivityFacilities facilities;

    private final static Logger log = Logger.getLogger(PPlanRouter.class);

    /**
     * Initialises an instance.
     * @param routingHandler the {@link TripRouter} to use to route individual trips
     * @param facilities the {@link ActivityFacilities} to which activities are refering.
     * May be <tt>null</tt>: in this case, the router will be given facilities wrapping the
     * origin and destination activity.
     */
    public PPlanRouter(
            final TripRouter routingHandler,
            final ActivityFacilities facilities) {
        this.routingHandler = routingHandler;
        this.facilities = facilities;
    }

    /**
     * Short for initialising without facilities.
     */
    public PPlanRouter(
            final TripRouter routingHandler) {
        this( routingHandler , null );
    }

    /**
     * Gives access to the {@link TripRouter} used
     * to compute routes.
     *
     * @return the internal TripRouter instance.
     */
    @Deprecated // get TripRouter out of injection instead. kai, feb'16
    public TripRouter getTripRouter() {
        return routingHandler;
    }

    @Override
    public void run(final Plan plan) {
        final List<Trip> trips = TripStructureUtils.getTrips( plan );

        for (Trip oldTrip : trips) {
            boolean hasParaLeg = hasParaLeg(oldTrip);
            final String routingMode = TripStructureUtils.identifyMainMode( oldTrip.getTripElements() );

            if(hasParaLeg) {
                final List<? extends PlanElement> newTrip =
                        routingHandler.calcRoute(
                                routingMode,
                                FacilitiesUtils.toFacility( oldTrip.getOriginActivity(), facilities ),
                                FacilitiesUtils.toFacility( oldTrip.getDestinationActivity(), facilities ),
                                calcEndOfActivity( oldTrip.getOriginActivity() , plan, routingHandler.getConfig() ),
                                plan.getPerson() );
                putVehicleFromOldTripIntoNewTripIfMeaningful(oldTrip, newTrip);
                TripRouter.insertTrip(
                        plan,
                        oldTrip.getOriginActivity(),
                        newTrip,
                        oldTrip.getDestinationActivity());
            }
        }
    }

    private static boolean hasParaLeg(Trip trip) {
        for(Leg leg: trip.getLegsOnly())  {
            if(leg.getRoute() instanceof TransitRoute)  {
                TransitRoute route = (TransitRoute) leg.getRoute();
                if(route.getId().toString().contains("para")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * If the old trip had vehicles set in its network routes, and it used a single vehicle,
     * and if the new trip does not come with vehicles set in its network routes,
     * then put the vehicle of the old trip into the network routes of the new trip.
     * @param oldTrip The old trip
     * @param newTrip The new trip
     */
    private static void putVehicleFromOldTripIntoNewTripIfMeaningful(Trip oldTrip, List<? extends PlanElement> newTrip) {
        Id<Vehicle> oldVehicleId = getUniqueVehicleId(oldTrip);
        if (oldVehicleId != null) {
            for (Leg leg : TripStructureUtils.getLegs(newTrip)) {
                if (leg.getRoute() instanceof NetworkRoute) {
                    if (((NetworkRoute) leg.getRoute()).getVehicleId() == null) {
                        ((NetworkRoute) leg.getRoute()).setVehicleId(oldVehicleId);
                    }
                }
            }
        }
    }

    private static Id<Vehicle> getUniqueVehicleId(Trip trip) {
        Id<Vehicle> vehicleId = null;
        for (Leg leg : trip.getLegsOnly()) {
            if (leg.getRoute() instanceof NetworkRoute) {
                if (vehicleId != null && (!vehicleId.equals(((NetworkRoute) leg.getRoute()).getVehicleId()))) {
                    return null; // The trip uses several vehicles.
                }
                vehicleId = ((NetworkRoute) leg.getRoute()).getVehicleId();
            }
        }
        return vehicleId;
    }

    @Override
    public void run(final Person person) {
        for (Plan plan : person.getPlans()) {
            run( plan );
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // helpers
    // /////////////////////////////////////////////////////////////////////////

    public static double calcEndOfActivity(
            final Activity activity,
            final Plan plan,
            final Config config ) {
        // yyyy similar method in PopulationUtils.  TripRouter.calcEndOfPlanElement in fact uses it.  However, this seems doubly inefficient; calling the
        // method in PopulationUtils directly would probably be faster.  kai, jul'19

        if (activity.getEndTime().isDefined())
            return activity.getEndTime().seconds();

        // no sufficient information in the activity...
        // do it the long way.
        // XXX This is inefficient! Using a cache for each plan may be an option
        // (knowing that plan elements are iterated in proper sequence,
        // no need to re-examine the parts of the plan already known)
        double now = 0;

        for (PlanElement pe : plan.getPlanElements()) {
            now = TripRouter.calcEndOfPlanElement(now, pe, config);
            if (pe == activity) return now;
        }

        throw new RuntimeException( "activity "+activity+" not found in "+plan.getPlanElements() );
    }
}

