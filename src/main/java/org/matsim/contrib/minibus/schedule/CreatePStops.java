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

package org.matsim.contrib.minibus.schedule;

import com.vividsolutions.jts.geom.*;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.*;
import org.matsim.contrib.minibus.PConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Create one TransitStopFacility for each car mode link of the network
 * 
 * @author aneumann, droeder, manserpa
 *
 */
public final class CreatePStops{
	
	private final static Logger log = Logger.getLogger(CreatePStops.class);
	
	private Network net;
	private final PConfigGroup pConfigGroup;
	private TransitSchedule transitSchedule;

	private Geometry include;
	private Geometry exclude;
	private final GeometryFactory factory;

	private final LinkedHashMap<Id<Link>, TransitStopFacility> linkId2StopFacilityMap;
	private final HashSet<Link> pNetworkLinks = new HashSet<>();
	private final HashSet<Link> wayBack = new HashSet<>();
	private final HashSet<Link> linksToRemove = new HashSet<>();
	private final HashSet<Link> linksToAdd = new HashSet<>();
	
	public static TransitSchedule createPStops(Network network, PConfigGroup pConfigGroup){
		return createPStops(network, pConfigGroup, null);
	}

	public static TransitSchedule createPStops(Network network, PConfigGroup pConfigGroup, TransitSchedule realTransitSchedule) {
		CreatePStops cS = new CreatePStops(network, pConfigGroup, realTransitSchedule);
		cS.run();
		new NetworkWriter(pConfigGroup.getPNetwork()).write("pNetwork.xml.gz");
		new NetworkWriter(network).write("totNetwork.xml.gz");
		return cS.getTransitSchedule();
	}
	
	/**
	 * Creates PStops in two ways. First, if a serviceAreaFile is defined in the config and this file exists, the file is used.
	 * Second, the (default) min/max-x/y-values are used.
	 * 
	 * Following FileTypes are supported:
	 * <ul>
	 * 	<li>Shapefiles with polygons. If one ore more attributes are defined, the last one is parsed 
	 *	 	to Boolean and used to get include- and exclude-areas.</li>
	 * 	<li>Textfile, containing a List of x/y-pairs per row, divided by semicolon. The first and the last coordinate should be equal
	 * 		to get a closed and well defined Geometry.</li>
	 * </ul>
	 * @param net
	 * @param pConfigGroup
	 * @param realTransitSchedule
	 */
    private CreatePStops(Network net, PConfigGroup pConfigGroup, TransitSchedule realTransitSchedule) {
		this.net = net;
		this.pConfigGroup = pConfigGroup;
		this.factory = new GeometryFactory();
		
		this.linkId2StopFacilityMap = new LinkedHashMap<>();
		
		Set<Id<TransitStopFacility>> stopsWithoutLinkIds = new TreeSet<>();

		int warnCounter = 10;
		
		if (realTransitSchedule != null) {
			for (TransitStopFacility stopFacility : realTransitSchedule.getFacilities().values()) {
				if (stopFacility.getLinkId() != null) {
					if (this.linkId2StopFacilityMap.get(stopFacility.getLinkId()) != null) {
						if (warnCounter > 0) {
							log.warn("There is more than one stop registered on link " + stopFacility.getLinkId() + ". "
									+ this.linkId2StopFacilityMap.get(stopFacility.getLinkId()).getId() + " stays registered as paratransit stop. Will ignore stop " + stopFacility.getId());
							warnCounter--;
						} if (warnCounter == 0) {
							log.warn("Future occurences of this logging statement are suppressed.");
							warnCounter--;
						}
					} else {
						//this.linkId2StopFacilityMap.put(stopFacility.getLinkId(), stopFacility);
					}
				} else {
					stopsWithoutLinkIds.add(stopFacility.getId());
				}
			}
		}
		
		this.exclude = this.factory.buildGeometry(new ArrayList<Geometry>());
		
		if(!new File(pConfigGroup.getServiceAreaFile()).exists()){
			log.warn("file " + this.pConfigGroup.getServiceAreaFile() + " not found. Falling back to min/max serviceArea parameters.");
			createServiceArea(pConfigGroup.getMinX(), pConfigGroup.getMaxX(), pConfigGroup.getMinY(), pConfigGroup.getMaxY());
		}else{
			log.warn("using " + this.pConfigGroup.getServiceAreaFile() + " for servicearea. x/y-values defined in the config are not used.");
			createServiceArea(pConfigGroup.getServiceAreaFile());
		}
		
		if (stopsWithoutLinkIds.size() > 0) {
			log.warn("There are " + stopsWithoutLinkIds.size() + " stop facilities without a link id, namely: " + stopsWithoutLinkIds.toString());
		}
		
		//this.topoTypesForStops = this.pConfigGroup.getTopoTypesForStops();
		//if(!(this.topoTypesForStops == null)){
		//	this.networkCalcTopoType = new NetworkCalcTopoType();
		//	this.networkCalcTopoType.run(net);
		//}
	}

	/**
	 * @param minX
	 * @param maxX
	 * @param minY
	 * @param maxY
	 */
	private void createServiceArea(double minX, double maxX, double minY, double maxY) {
		Coordinate[] c = new Coordinate[4];
		c[0] = new Coordinate(minX, minY);
		c[1] = new Coordinate(minX, maxY);
		c[2] = new Coordinate(maxX, minY);
		c[3] = new Coordinate(maxX, maxY);
		this.include = this.factory.createMultiPoint(c).convexHull();
	}

	/**
	 * @param serviceAreaFile
	 */
	private void createServiceArea(String serviceAreaFile) {
		if(serviceAreaFile.endsWith(".txt")){
			createServiceAreaTxt(serviceAreaFile);
		}else if (serviceAreaFile.endsWith(".shp")){
			createServiceAreaShp(serviceAreaFile);
		}else{
			log.warn(serviceAreaFile + ". unknown filetype. Falling back to simple x/y-values...");
			this.createServiceArea(pConfigGroup.getMinX(), pConfigGroup.getMaxX(), pConfigGroup.getMinY(), pConfigGroup.getMaxY());
		}
	}

	/**
	 * @param serviceAreaFile
	 */
	private void createServiceAreaTxt(String serviceAreaFile) {
		
		List<String> lines = new ArrayList<>();
		String line;
		try {
			BufferedReader reader = IOUtils.getBufferedReader(serviceAreaFile);
			line = reader.readLine();
			do{
				if(!(line == null)){
					if(line.contains(";")){
						lines.add(line);
					}
					line = reader.readLine();
				}
			}while(!(line == null));
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(lines.size() < 3){
			log.warn("an area needs at least 3 points, to be defined. Falling back to simple (default) x/y-values...");
			this.createServiceArea(pConfigGroup.getMinX(), pConfigGroup.getMaxX(), pConfigGroup.getMinY(), pConfigGroup.getMaxY());
			return;	
		}
		
		Coordinate[] c = new Coordinate[lines.size() + 1];
			
		double x,y;
		for(int i = 0; i < lines.size(); i++){
			x = Double.parseDouble(lines.get(i).split(";")[0]);
			y = Double.parseDouble(lines.get(i).split(";")[1]);
			c[i] = new Coordinate(x, y);
		}
		// a linear ring has to be closed, so add the first coordinate again at the end
		c[lines.size()] = c[0];
		this.include = this.factory.createPolygon(this.factory.createLinearRing(c), null);
	}

	/**
	 * @param serviceAreaFile
	 */
	private void createServiceAreaShp(String serviceAreaFile) {
		Collection<SimpleFeature> features = new ShapeFileReader().readFileAndInitialize(serviceAreaFile);
		Collection<Geometry> include = new ArrayList<>();
		Collection<Geometry> exclude = new ArrayList<>();
		
		for(SimpleFeature f: features){
			boolean incl = true;
			Geometry g = null;
			for(Object o: f.getAttributes()){
				if(o instanceof Polygon){
					g = (Geometry) o;
				}else if (o instanceof MultiPolygon){
					g = (Geometry) o;
				}
				// TODO use a better way to get the attributes, maybe directly per index.
				// Now the last attribute is used per default... 
				else if (o instanceof String){
					incl = Boolean.parseBoolean((String) o);
				}
			}
			if(! (g == null)){
				if(incl){
					include.add(g);
				}else{
					exclude.add(g);
				}
			}
		}
		this.include = this.factory.createGeometryCollection( 
				include.toArray(new Geometry[include.size()])).buffer(0);
		this.exclude = this.factory.createGeometryCollection( 
				exclude.toArray(new Geometry[exclude.size()])).buffer(0);
	}

	private void run(){
		this.transitSchedule = new TransitScheduleFactoryImpl().createTransitSchedule();
		int stopsAdded = 0;
		
		for (Link link : this.net.getLinks().values()) {
			if(link.getFreespeed() < 27.0 && link.getAllowedModes().contains(TransportMode.car)) {
				pNetworkLinks.add(link);
			}
			if(link.getAllowedModes().contains(TransportMode.car) && !this.linkId2StopFacilityMap.containsKey(link.getId())){
				if(!wayBack.contains(link))
					stopsAdded += addStopOnLink(link);
			}
		}

		for(Link link : this.linksToRemove)	{
			if (this.pNetworkLinks.contains(link))
				this.pNetworkLinks.remove(link);
		}

		for(Link link : this.linksToAdd)
			this.net.addLink(link);

		Scenario pScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		this.pConfigGroup.setPNetwork(pScenario.getNetwork());
		Network pNetwork = this.pConfigGroup.getPNetwork();
		NetworkFactory pFactory = pNetwork.getFactory();

		//add links to pNetwork
		for(Link link: this.pNetworkLinks)	{
			Node pFromNode = pNetwork.getNodes().get(link.getFromNode().getId());
			if(pFromNode == null) {
				pFromNode = pFactory.createNode(link.getFromNode().getId(), link.getFromNode().getCoord());
				pNetwork.addNode(pFromNode);
			}

			Node pToNode = pNetwork.getNodes().get(link.getToNode().getId());
			if(pToNode == null) {
				pToNode = pFactory.createNode(link.getToNode().getId(), link.getToNode().getCoord());
				pNetwork.addNode(pToNode);
			}

			//Link pLink = pFactory.createLink(Id.createLinkId("pt_" + link.getId().toString()), pFromNode, pToNode);
			Link pLink = pFactory.createLink(Id.createLinkId(link.getId().toString()), pFromNode, pToNode);
			pLink.setLength(link.getLength());
			pLink.setFreespeed(link.getFreespeed());
			pLink.setCapacity(10000);
			pLink.setNumberOfLanes(10000);
			pLink.setAllowedModes(Collections.singleton(TransportMode.car));
			pNetwork.addLink(pLink);
		}
		log.info("Added " + stopsAdded + " additional stops for paratransit services");
	}
	
	private int addStopOnLink(Link link) {
		if(link == null){
			return 0;
		}

		if(!link.getAllowedModes().contains("car")){
			return 0;
		}

		if(!linkToNodeInServiceArea(link)){
			return 0;
		}

		// manserpa: set speed limit for stops (80km/h -> 22.222222m/s)
		if (link.getFreespeed() >= 22.5) {
			return 0;
		}
		
		if (this.linkId2StopFacilityMap.get(link.getId()) != null) {
			log.warn("Link " + link.getId() + " has already a stop. This should not happen. Check code.");
			return 0;
		}
		
		// manserpa: at this point, it would be the goal to have the same Identifier for stops with the same to and from nodes (just with the difference A and B)
		
		Node fromNode = link.getFromNode();
		Node toNode = link.getToNode();
		
		// wenn ein Link existiert mit fromNode = toNode UND toNode = fromNode
		for (Link wayBack : this.net.getLinks().values())	{
			if(wayBack.getFromNode().equals(toNode) && wayBack.getToNode().equals(fromNode) && !linkId2StopFacilityMap.containsKey(wayBack.getId())
					&& !wayBack.equals(link))	{
				this.linksToRemove.add(link);
				this.linksToRemove.add(wayBack);
				this.wayBack.add(wayBack);

				Coord stopCoord = new Coord((link.getFromNode().getCoord().getX() + link.getToNode().getCoord().getX()) / 2,
						(link.getFromNode().getCoord().getY() + link.getToNode().getCoord().getY()) / 2);

				Node node = this.net.getFactory().createNode(
						Id.createNodeId(link.getFromNode().getId().toString()+"-"+link.getId().toString()), stopCoord);
				this.net.addNode(node);

				// way forth
				Link link1 = this.net.getFactory().createLink(Id.createLinkId(link.getId().toString()+"-1"),
						link.getFromNode(), node);
				link1.setLength(link.getLength() / 2);
				link1.setFreespeed(link.getFreespeed());
				link1.setCapacity(link.getCapacity());
				link1.setNumberOfLanes(link.getNumberOfLanes());
				link1.setAllowedModes(Collections.singleton(TransportMode.pt));
				this.linksToAdd.add(link1);
				this.pNetworkLinks.add(link1);

				Link link2 = this.net.getFactory().createLink(Id.createLinkId(link.getId().toString()+"-2"),
						node, link.getToNode());
				link2.setLength(link.getLength() / 2);
				link2.setFreespeed(link.getFreespeed());
				link2.setCapacity(link.getCapacity());
				link2.setNumberOfLanes(link.getNumberOfLanes());
				link2.setAllowedModes(Collections.singleton(TransportMode.pt));
				this.linksToAdd.add(link2);
				this.pNetworkLinks.add(link2);

				Id<TransitStopFacility> stopId = Id.create(this.pConfigGroup.getPIdentifier() + link.getId() + "_A", TransitStopFacility.class);
				TransitStopFacility stop = this.transitSchedule.getFactory().createTransitStopFacility(stopId, stopCoord, false);
				stop.setLinkId(link1.getId());
				stop.setName(Integer.toString(this.transitSchedule.getFacilities().size() + 1));
				this.transitSchedule.addStopFacility(stop);
				this.linkId2StopFacilityMap.put(link.getId(), stop);

				// way back
				Link blink1 = this.net.getFactory().createLink(Id.createLinkId(wayBack.getId().toString()+"-1"),
						wayBack.getFromNode(), node);
				blink1.setLength(wayBack.getLength() / 2);
				blink1.setFreespeed(wayBack.getFreespeed());
				blink1.setCapacity(wayBack.getCapacity());
				blink1.setNumberOfLanes(wayBack.getNumberOfLanes());
				blink1.setAllowedModes(Collections.singleton(TransportMode.pt));
				this.linksToAdd.add(blink1);
				this.pNetworkLinks.add(blink1);

				Link blink2 = this.net.getFactory().createLink(Id.createLinkId(wayBack.getId().toString()+"-2"),
						node, wayBack.getToNode());
				blink2.setLength(wayBack.getLength() / 2);
				blink2.setFreespeed(wayBack.getFreespeed());
				blink2.setCapacity(wayBack.getCapacity());
				blink2.setNumberOfLanes(wayBack.getNumberOfLanes());
				blink2.setAllowedModes(Collections.singleton(TransportMode.pt));
				this.linksToAdd.add(blink2);
				this.pNetworkLinks.add(blink2);

				Id<TransitStopFacility> stopIdBack = Id.create(this.pConfigGroup.getPIdentifier() + link.getId() + "_B", TransitStopFacility.class);
				TransitStopFacility stopBack = this.transitSchedule.getFactory().createTransitStopFacility(stopIdBack, stopCoord, false);
				stopBack.setLinkId(Id.createLinkId(blink1.getId().toString()));
				stopBack.setName(Integer.toString(this.transitSchedule.getFacilities().size() + 1));
				this.transitSchedule.addStopFacility(stopBack);
				this.linkId2StopFacilityMap.put(wayBack.getId(), stopBack);

				return 2;
			}
		}
		return 0;
	
			
	}

	private boolean linkToNodeInServiceArea(Link link) {
		Point pToNode = factory.createPoint(MGC.coord2Coordinate(link.getToNode().getCoord()));
		Point pFromNode = factory.createPoint(MGC.coord2Coordinate(link.getFromNode().getCoord()));
		if(this.include.contains(pToNode) && this.include.contains(pFromNode)){
			if(exclude.contains(pToNode) || exclude.contains(pFromNode)){
				return false;
			}
			return true;
		}
		return false;
	}

	private TransitSchedule getTransitSchedule() {
		return this.transitSchedule;
	}
}