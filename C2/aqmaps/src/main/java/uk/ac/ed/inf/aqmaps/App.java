package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.*;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

public class App {
    
    private static double DRONE_MOVE_DISTANCE = 0.0003;
    private static double READ_DISTANCE = 0.0002;
    private static double ENDING_DISTANCE = 0.0003;
    private static int MAX_MOVES = 150;
    
    public static void main(String[] args) throws IOException, InterruptedException {
        // Reading specified map 
        var webserver = "http://localhost:" + args[6];
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder().uri(URI.create(
                webserver + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json")).build(); 
        var responce = client.send(request, BodyHandlers.ofString());
        
        // Informing the user if there is a problem reading the specified map
        if (responce.statusCode() != 200) {
            // TODO make ok error code, right now this isn't correct 
            System.out.println("Error: unable to connect to " + webserver + " at port " + args[6] + ".\n"
                    + "HTTP status code: " + responce.statusCode() + "\nExiting");
            System.exit(1);
        }
        
        // Parsing the json and reading additional information about the sensors 
//        Type listType = new TypeToken<ArrayList<MapData>>() {}.getType();    
//        ArrayList<MapData> mapEntries = new Gson().fromJson(responce.body(), listType);
        Type listType = new TypeToken<List<MapData>>() {}.getType();
        List<MapData> mapEntries = new Gson().fromJson(responce.body(), listType);
        
//        var sensors = new ArrayList<Sensor>();
        List<Sensor> sensors = new ArrayList<Sensor>();
        
        for (var entry : mapEntries) {
            request = HttpRequest.newBuilder().uri(URI.create(
                    webserver + "/words/" + entry.getLocation().replace('.', '/') + "/details.json")).build();
            responce = client.send(request, BodyHandlers.ofString());
            
            if (responce.statusCode() != 200) {
                // TODO make ok error code, right now this isn't correct  
                System.out.println("Error: unable to connect to " + webserver + " at port " + args[6] + ".\n"
                        + "HTTP status code: " + responce.statusCode() + "\nThis entry will be skipped.");
            } else {
                What3WordsData sensorInfo = new Gson().fromJson(responce.body(), What3WordsData.class);
                sensors.add(new Sensor(sensorInfo.getLng(), sensorInfo.getLat(), 
                        entry.getBattery(), entry.getReading(), entry.getLocation()));
            }
        }
        
        // Reading in the no fly zones
        request = HttpRequest.newBuilder().uri(URI.create(
                webserver + "/buildings/no-fly-zones.geojson")).build();
        responce = client.send(request, BodyHandlers.ofString());
        List<Polygon> noFlyZones = new ArrayList<Polygon>();
        if (responce.statusCode() != 200) {
            // TODO make ok error code, right now this isn't correct  
            System.out.println("Error: unable to connect to " + webserver + " at port " + args[6] + ".\n"
                    + "HTTP status code: " + responce.statusCode() + "\nThis entry will be skipped.");
        } else {
            var buildings = FeatureCollection.fromJson(responce.body()).features();
            
            for (int i = 0; i < buildings.size(); i++) {
                try {
                    noFlyZones.add((Polygon) buildings.get(i).geometry());
                } catch (Exception e) {
                    System.out.println("Error: building number " + i + " could not be interpreted. \nThis entry will be skipped");
                }
            }
        }
        
        
        // Note that the order is longitude, latitude consistent with the order for output, 
        // but the arguments for this main method are in the order latitude, longitude 
        Drone drone = new Drone(Double.parseDouble(args[4]), Double.parseDouble(args[3]), 
                DRONE_MOVE_DISTANCE, READ_DISTANCE, ENDING_DISTANCE, MAX_MOVES);
        
        
        
        // TODO TESTING DRONE MOVEMENT ALGORITHM
//        drone.visitSensors(sensors, noFlyZones);
        
        
        // TODO removing testing
        var output = new FileWriter("aqmap.geojson");
        var geojson = new ArrayList<Feature>();
        geojson.add(Feature.fromGeometry(drone.visitSensors(sensors, noFlyZones)));
        for (Sensor sensor : sensors) {
            geojson.add(sensor.toGeojsonFeature());
        }
        
        for (Polygon zone : noFlyZones) {
            var asFeature = Feature.fromGeometry(zone);
            asFeature.addStringProperty("fill", "#ff0000");
            geojson.add(asFeature);
            for (int i = 0; i < zone.outer().coordinates().size() - 1; i++) {
                var p = Feature.fromGeometry(zone.outer().coordinates().get(i));
                p.addStringProperty("marker-color", "#ff0000");
                geojson.add(p);
            }
        }
        
        output.write(FeatureCollection.fromFeatures(geojson).toJson());
        output.close();
        
    }
}
