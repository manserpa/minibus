package org.matsim.contrib.minibus.analysis;

import javafx.collections.transformation.TransformationList;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.minibus.genericUtils.CSVReader;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.io.IOException;
import java.util.*;

public class Utils {
    public static HashMap<Id<TransitLine>, Set<Id<TransitRoute>>> readRemovedLines() {
        HashMap<Id<TransitLine>, Set<Id<TransitRoute>>> removedRoutes = new HashMap<>();

        String[] columns = {"line", "route","mode"};
        try (CSVReader reader = new CSVReader(columns, "C:\\dev\\temp\\removed_lines.csv", ";")) {
            Map<String, String> row = reader.readLine(); // header
            while ((row = reader.readLine()) != null) {

                Id<TransitLine> line = Id.create(row.get("line"), TransitLine.class);
                removedRoutes.putIfAbsent(line, new HashSet<>());

                Id<TransitRoute> route = Id.create(row.get("route"), TransitRoute.class);
                removedRoutes.get(line).add(route);
            }
        }
        catch(IOException ex){
            System.out.println (ex.toString());
        }


        return removedRoutes;
    }
}
