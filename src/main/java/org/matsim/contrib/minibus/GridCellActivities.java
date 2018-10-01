package org.matsim.contrib.minibus;

import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.minibus.genericUtils.GridNode;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class GridCellActivities {

	// TODO (PM): remove?
	
	private FileWriter writer;
	private Scenario scenario;
	private HashMap<String, Integer> gridNodeId2ActsCountMap = new HashMap<>();
	
	private GridCellActivities()	{
		this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile("population.xml.gz");
	}
	
	
	private void run()	{
		for (Person person : scenario.getPopulation().getPersons().values()) {
			for (PlanElement pE : person.getSelectedPlan().getPlanElements()) {
				if (pE instanceof Activity) {
					Activity act = (Activity) pE;
					if(!act.getType().equals("pt interaction"))	{
						String gridNodeId = GridNode.getGridNodeIdForCoord(act.getCoord(), 300);
						gridNodeId2ActsCountMap.put(gridNodeId, gridNodeId2ActsCountMap.getOrDefault(gridNodeId, 0) + 1);
					}
				}
			}
		}
	}

	private void write()	{
		/*
		try {
			writer = new FileWriter("activities.csv");

	    CSVUtils.writeLine(writer , Arrays.asList("GridCell", "Activities"), ';');
	    for(String id: gridNodeId2ActsCountMap.keySet())	{
	    	CSVUtils.writeLine(writer, Arrays.asList(id, Integer.toString(gridNodeId2ActsCountMap.get(id))), ';');
	    }
	    writer.flush();
	    writer.close();
		} catch (Exception e) {

			e.printStackTrace();
		}
		*/
	}
	
	
	public static void main(String[] args)	{
		GridCellActivities gca = new GridCellActivities();
		gca.run();
		gca.write();
	}
}
