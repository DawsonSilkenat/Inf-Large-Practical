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
    
    private static final double DRONE_MOVE_DISTANCE = 0.0003;
    private static final double READ_DISTANCE = 0.0002;
    private static final double ENDING_DISTANCE = 0.0003;
    private static final int MAX_MOVES = 150;
    private static final double MINIMUM_LONGITUDE = -3.192473;
    private static final double MAXIMUM_LONGITUDE = -3.184319;
    private static final double MINIMUM_LATITUDE = 55.942617;
    private static final double MAXIMUM_LATITUDE = 55.946233;
    
    public static void main(String[] args) throws IOException, InterruptedException {
        var startTime = System.currentTimeMillis();
        
        var day = args[0];
        var month = args[1];
        var year = args[2];
        double droneLng = 0;
        double droneLat = 0;
        try {
            droneLng = Double.parseDouble(args[4]);
            droneLat = Double.parseDouble(args[3]);
        } catch (Exception e) {
            //TODO Add error message
            System.exit(1);
        }
        // 5th arg is the seed for any randomness. We don't use this
        var webserver = "http://localhost:" + args[6];
        
        var sensors = getSensors(day, month, year, webserver);
        var noFlyZones = getNoFlyZones(day, month, year, webserver);
        
        var readTime = System.currentTimeMillis();
        
        Drone drone = new Drone(Point.fromLngLat(droneLng, droneLat), DRONE_MOVE_DISTANCE, READ_DISTANCE, ENDING_DISTANCE, MAX_MOVES, 
                MINIMUM_LONGITUDE, MAXIMUM_LONGITUDE, MINIMUM_LATITUDE, MAXIMUM_LATITUDE);
        drone.visitSensors(sensors, noFlyZones);
        
        var droneTime = System.currentTimeMillis();
        
        writeReadings(sensors, drone, noFlyZones, day, month, year);
        writeFlightPath(drone, day, month, year);
        
        var writeTime = System.currentTimeMillis();
        
        System.out.println((readTime - startTime)/1000.0);
        System.out.println((droneTime - readTime)/1000.0);
        System.out.println((writeTime - droneTime)/1000.0);
        System.out.println((writeTime - startTime)/1000.0);
    }
    
    private static List<Sensor> getSensors(String day, String month, String year, String webserver) throws IOException, InterruptedException {
        var mapEntries = getMapData(day, month, year, webserver);
        var client = HttpClient.newHttpClient();
        var sensors = new ArrayList<Sensor>();
        
        for (var entry : mapEntries) {
            var request = HttpRequest.newBuilder().uri(URI.create(
                    webserver + "/words/" + entry.getLocation().replace('.', '/') + "/details.json")).build();
            var responce = client.send(request, BodyHandlers.ofString());
            
            if (responce.statusCode() != 200) {
                // TODO make ok error code, right now this isn't correct  
//                System.out.println("Error: unable to connect to " + webserver + " at port " + args[6] + ".\n"
//                        + "HTTP status code: " + responce.statusCode() + "\nThis entry will be skipped.");
            } else {
                What3WordsData sensorInfo = new Gson().fromJson(responce.body(), What3WordsData.class);
//                sensors.add(new Sensor(sensorInfo.getLng(), sensorInfo.getLat(), 
//                        entry.getBattery(), entry.getReading(), entry.getLocation()));
                sensors.add(new Sensor(Point.fromLngLat(sensorInfo.getLng(), sensorInfo.getLat()), 
                        entry.getBattery(), entry.getReading(), entry.getLocation()));
            }
        }
        
        return sensors;
    }
    
    private static List<MapData> getMapData(String day, String month, String year, String webserver) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder().uri(URI.create(
                webserver + "/maps/" + year + "/" + month + "/" + day + "/air-quality-data.json")).build(); 
        var responce = client.send(request, BodyHandlers.ofString());
        
        // Informing the user if there is a problem reading the specified map
        if (responce.statusCode() != 200) {
            // TODO make ok error code, right now this isn't correct 
            System.out.println("Error: unable to connect to " + webserver + ".\n"
                    + "HTTP status code: " + responce.statusCode() + "\nExiting");
            System.exit(1);
        }
        
        Type listType = new TypeToken<List<MapData>>() {}.getType();
//        List<MapData> mapEntries = new Gson().fromJson(responce.body(), listType);
//        return mapEntries;
        return new Gson().fromJson(responce.body(), listType);
    }
    
    private static List<Polygon> getNoFlyZones(String day, String month, String year, String webserver) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder().uri(URI.create(webserver + "/buildings/no-fly-zones.geojson")).build();
        var responce = client.send(request, BodyHandlers.ofString());
        List<Polygon> noFlyZones = new ArrayList<Polygon>();
        if (responce.statusCode() != 200) {
            // TODO make ok error code, right now this isn't correct  
//            System.out.println("Error: unable to connect to " + webserver + " at port " + args[6] + ".\n"
//                    + "HTTP status code: " + responce.statusCode() + "\nThis entry will be skipped.");
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
        
        return noFlyZones;
    }
    
    private static void writeReadings(List<Sensor> sensors, Drone drone, List<Polygon> noFlyZones, 
            String day, String month, String year) throws IOException {
        var output = new FileWriter("readings-" + day + "-" + month + "-" + year + ".geojson");
        var geojson = new ArrayList<Feature>();
        
        var dronePath = new ArrayList<Point>();
        dronePath.add(drone.getStartPosition());
        for (Path path : drone.getVisitPaths()) {
            dronePath.addAll(path.getPositions());
        }
        geojson.add(Feature.fromGeometry(LineString.fromLngLats(dronePath)));
        
        for (Sensor sensor : sensors) {
            geojson.add(sensor.toGeojsonFeature());
        }
        
        for (Polygon zone : noFlyZones) {
            var asFeature = Feature.fromGeometry(zone);
            asFeature.addStringProperty("fill", "#ff0000");
            geojson.add(asFeature);
//            for (int i = 0; i < zone.outer().coordinates().size() - 1; i++) {
//                var p = Feature.fromGeometry(zone.outer().coordinates().get(i));
//                p.addStringProperty("marker-color", "#ff0000");
//                geojson.add(p);
//            }
        }
        
        output.write(FeatureCollection.fromFeatures(geojson).toJson());
        output.close();
    }
    
    private static void writeFlightPath(Drone drone, String day, String month, String year) throws IOException {
        var output = new FileWriter("flightpath-" + day + "-" + month + "-" + year + ".txt");   
        var n = 1;
        var previousPosition = drone.getStartPosition();
        var paths = drone.getVisitPaths();
        var sensors = drone.getVisitOrder();
        
        for (int i = 0; i < paths.size(); i++) {
            // These two lists always have the same size
            var positions = paths.get(i).getPositions();
            var angles = paths.get(i).getMoveAngles();
            
            for (int j = 0; j < positions.size(); j++) {
                output.write(n + "," + previousPosition.longitude() + "," + previousPosition.latitude() + "," + angles.get(j)
                    + "," + positions.get(j).longitude() + "," + positions.get(j).latitude());
                if (j == positions.size() - 1 && i != paths.size() - 1) {
                    output.write("," + sensors.get(i).getWhatThreeWords() + "\n");
                } else {
                    output.write(",null\n");
                }
                n++;
                previousPosition = positions.get(j);
            }
        }
        
        output.close();
    }
}
