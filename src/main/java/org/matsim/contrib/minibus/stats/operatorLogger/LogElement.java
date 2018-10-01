/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package org.matsim.contrib.minibus.stats.operatorLogger;

import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.minibus.PConstants.OperatorState;
import org.matsim.contrib.minibus.operator.Operator;
import org.matsim.contrib.minibus.operator.PPlan;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * Simple storage class for one log entry
 * 
 * @author aneumann
 *
 */
public final class LogElement {
	
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger(LogElement.class);

	final static String COMMENTTAG = "#";
	final static String DELIMITER = "\t";
	final static String NOVALUE = "=====";
	
	private int iteration;
	private Id<Operator> operatorId;
	private OperatorState status;
	private Id<PPlan> planId;
	private String creatorId;
	private String vehicleType;
	private double distanceDriven;
	private double timeDriven;
	private double passengerKilometer;
	private int subsidizedTrips;
	private double totalAmountOfSubsidies;
	private Id<PPlan> parentId;
	private int nVeh;
	private double headway;
	private int nPax;
	private double score;
	private double budget;
	private double startTime;
	private double endTime;
	private ArrayList<Id<TransitStopFacility>> stopsToBeServed;
	private ArrayList<Id<Link>> linksServed;

	
	public String getUniquePlanIdentifier() {
		return this.operatorId + "_" + this.planId;
	}
	
	public int getIteration() {
		return iteration;
	}
	public void setIteration(int iteration) {
		this.iteration = iteration;
	}
	public Id<Operator> getOperatorId() {
		return operatorId;
	}
	public void setOperatorId(Id<Operator> operatorId) {
		this.operatorId = operatorId;
	}
	public OperatorState getStatus() {
		return status;
	}
	public void setStatus(OperatorState operatorState) {
		this.status = operatorState;
	}
	public Id<PPlan> getPlanId() {
		return planId;
	}
	public void setPlanId(Id<PPlan> planId) {
		this.planId = planId;
	}
	public String getCreatorId() {
		return creatorId;
	}
	public void setCreatorId(String creatorId) {
		this.creatorId = creatorId;
	}
	public String getVehicleType() {
		return vehicleType;
	}
	public void setVehicleType(String vehicleType) {
		this.vehicleType = vehicleType;
	}
	public double getHeadway() {
		return this.headway;
	}
	public void setHeadway(double headway) {
		this.headway = headway;
	}
	public double getDistanceDriven() {
		return this.distanceDriven;
	}
	public void setDistanceDriven(double distanceDriven) {
		this.distanceDriven = distanceDriven;
	}
	public double getTimeDriven() {
		return this.timeDriven;
	}
	public void setTimeDriven(double timeDriven) {
		this.timeDriven = timeDriven;
	}
	public double getPassengerKilometer() {
		return this.passengerKilometer;
	}
	public void setPassengerKilometer(double passengerKilometer) {
		this.passengerKilometer = passengerKilometer;
	}
	public void setSubsidizedTrips(int subsidizedTrips2) {
		this.subsidizedTrips = subsidizedTrips2;
	}
	public void setAmountOfSubsidies(double amountOfSubsidies) {
		this.totalAmountOfSubsidies = amountOfSubsidies;
	}
	public Id<PPlan> getParentId() {
		return parentId;
	}
	public void setParentId(Id<PPlan> parentId) {
		this.parentId = parentId;
	}
	public int getnVeh() {
		return nVeh;
	}
	public void setnVeh(int nVeh) {
		this.nVeh = nVeh;
	}
	public int getnPax() {
		return nPax;
	}
	public void setnPax(int nPax) {
		this.nPax = nPax;
	}
	public double getScore() {
		return score;
	}
	public void setScore(double score) {
		this.score = score;
	}
	public double getBudget() {
		return budget;
	}
	public void setBudget(double budget) {
		this.budget = budget;
	}
	public double getStartTime() {
		return startTime;
	}
	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}
	public double getEndTime() {
		return endTime;
	}
	public void setEndTime(double endTime) {
		this.endTime = endTime;
	}
	public ArrayList<Id<TransitStopFacility>> getStopsToBeServed() {
		return stopsToBeServed;
	}
	public void setStopsToBeServed(ArrayList<Id<TransitStopFacility>> stopsServed) {
		this.stopsToBeServed = stopsServed;
	}
	public ArrayList<Id<Link>> getLinksServed() {
		return linksServed;
	}
	public void setLinksServed(ArrayList<Id<Link>> linksServed) {
		this.linksServed = linksServed;
	}
	
	static String getHeaderLine(){
		StringBuffer strB = new StringBuffer();
		strB.append(COMMENTTAG + " ");
		strB.append("iteration");
		strB.append(DELIMITER).append("operator");
		strB.append(DELIMITER).append("status");
		strB.append(DELIMITER).append("plan id");
		strB.append(DELIMITER).append("creator");
		strB.append(DELIMITER).append("parent");
		strB.append(DELIMITER).append("vehicletype");
		strB.append(DELIMITER).append("headway");
		strB.append(DELIMITER).append("vehicle");
		strB.append(DELIMITER).append("distance driven");
		strB.append(DELIMITER).append("time driven");
		strB.append(DELIMITER).append("passenger kilometer");
		strB.append(DELIMITER).append("pax");
		strB.append(DELIMITER).append("subidized pax");
		strB.append(DELIMITER).append("tot subsidies");
		strB.append(DELIMITER).append("score");
		strB.append(DELIMITER).append("budget");
		strB.append(DELIMITER).append("start time");
		strB.append(DELIMITER).append("end time");
		strB.append(DELIMITER).append("important stops");
		strB.append(DELIMITER).append("links");
		return strB.toString();
	}
	
	public String toString(){
		StringBuffer strB = new StringBuffer();
		strB.append(this.iteration);
		strB.append(DELIMITER).append(this.operatorId.toString());
		strB.append(DELIMITER).append(this.status);
		strB.append(DELIMITER).append(this.planId);
		strB.append(DELIMITER).append(this.creatorId);
		strB.append(DELIMITER).append(this.parentId);
		strB.append(DELIMITER).append(this.vehicleType);
		strB.append(DELIMITER).append(this.headway);
		strB.append(DELIMITER).append(this.nVeh);
		strB.append(DELIMITER).append(this.distanceDriven);
		strB.append(DELIMITER).append(this.timeDriven);
		strB.append(DELIMITER).append(this.passengerKilometer);
		strB.append(DELIMITER).append(this.nPax);
		strB.append(DELIMITER).append(this.subsidizedTrips);
		strB.append(DELIMITER).append(this.totalAmountOfSubsidies);
		strB.append(DELIMITER).append(this.score);
		strB.append(DELIMITER).append(this.budget);
		strB.append(DELIMITER).append(Time.writeTime(this.startTime));
		strB.append(DELIMITER).append(Time.writeTime(this.endTime));
		strB.append(DELIMITER).append(this.stopsToBeServed);
		strB.append(DELIMITER).append(this.linksServed);
		return strB.toString();
	}
	
	String getTotalString(){
		StringBuffer strB = new StringBuffer();
		strB.append(this.iteration);
		strB.append(DELIMITER).append(this.operatorId.toString());
		strB.append(DELIMITER).append(this.status);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(nVeh);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(nPax);
		strB.append(DELIMITER).append(score);
		strB.append(DELIMITER).append(budget);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(NOVALUE);
		strB.append(DELIMITER).append(NOVALUE);
		return strB.toString();
	}
}
