package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.*;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

public class App {
    
    private static final double MOVE_DISTANCE = 0.0003;
    private static final double READ_DISTANCE = 0.0002;
    private static final double ENDING_DISTANCE = 0.0003;
    private static final int MAX_MOVES = 150;
    private static final double MINIMUM_LONGITUDE = -3.192473;
    private static final double MAXIMUM_LONGITUDE = -3.184319;
    private static final double MINIMUM_LATITUDE = 55.942617;
    private static final double MAXIMUM_LATITUDE = 55.946233;
    
    public static void main(String[] args) throws IOException, InterruptedException {
        var day = args[0];
        var month = args[1];
        var year = args[2];
        var droneLng = 0.0;
        var droneLat = 0.0;
        try {
            droneLng = Double.parseDouble(args[4]);
            droneLat = Double.parseDouble(args[3]);
        } catch (Exception e) { 
            System.out.println("The drones starting position was incorrectly formated. Please enter the drones "
                    + "starting position as two decimal numbers representing the starting longitude and latitude.");
            System.exit(1);
        }
        // arg[5] is the random number seed for the application. We don't use this since everything is deterministic 
        var webserver = "http://localhost:" + args[6];
        
        // Get required information from webserver
        var sensors = getSensors(day, month, year, webserver);
        var noFlyZones = getNoFlyZones(webserver);
        
        // Initialise the drone and find a flight path
        var drone = new Drone(Point.fromLngLat(droneLng, droneLat), MOVE_DISTANCE, READ_DISTANCE, 
                ENDING_DISTANCE, MAX_MOVES, MINIMUM_LONGITUDE, MAXIMUM_LONGITUDE, MINIMUM_LATITUDE, MAXIMUM_LATITUDE);
        drone.findFlightPath(sensors, noFlyZones);
        drone.updateSensors();
        
        // Write required outputs
        writeReadings(sensors, drone, day, month, year);
        writeFlightPath(drone, day, month, year);
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
                System.out.println("Error: unable to find data for the sensor at " + entry.getLocation() + ".\n"
                        + "HTTP status code: " + responce.statusCode() + "\nThis entry will be skipped.");
            } else {
                var sensorInfo = new Gson().fromJson(responce.body(), What3WordsData.class);
                
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
            System.out.println("Error: Unable to read required data for the specified date.\n" 
                    + "Please insure the date was entered in the correct format\n"
                    + "HTTP status code: " + responce.statusCode() + "\nExiting");
            System.exit(1);
        }
        
        var listType = new TypeToken<List<MapData>>() {}.getType();
        return new Gson().fromJson(responce.body(), listType);
    }
    
    private static List<NoFlyZone> getNoFlyZones(String webserver) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder().uri(URI.create(webserver + "/buildings/no-fly-zones.geojson")).build();
        var responce = client.send(request, BodyHandlers.ofString());
        var noFlyZones = new ArrayList<NoFlyZone>();
        
        if (responce.statusCode() != 200) { 
            System.out.println("Error: A problem has occured in reading the no-fly zones from " + webserver + "\n."
                    + "HTTP status code: " + responce.statusCode() + "\n"
                    + "Attempting to run with no no-fly zones\n.");
        } else {
            var buildings = FeatureCollection.fromJson(responce.body()).features();
            
            for (int i = 0; i < buildings.size(); i++) {
                try {
                    var boundary = (Polygon) buildings.get(i).geometry();
                    noFlyZones.add(new NoFlyZone(boundary));
                } catch (Exception e) {
                    System.out.println("Error: no fly zone number " + i + " could not be interpreted. \nThis entry will be skipped");
                }
            }
        }
        
        return noFlyZones;
    }
    
    private static void writeReadings(List<Sensor> sensors, Drone drone, String day, String month, String year) throws IOException {
        var output = new FileWriter("readings-" + day + "-" + month + "-" + year + ".geojson");
        var geojson = new ArrayList<Feature>();
        
        // Add the drone flight path to the output
        var droneFlightPath = new ArrayList<Point>();
        droneFlightPath.add(drone.getStartPosition());
        for (Path path : drone.getPathsList()) {
            droneFlightPath.addAll(path.getPositions());
        }
        geojson.add(Feature.fromGeometry(LineString.fromLngLats(droneFlightPath)));
        
        // Add all the sensors to the output. Assume they have already been updated if they would be visited
        for (Sensor sensor : sensors) {
            geojson.add(sensor.toGeojsonFeature());
        }
        
        output.write(FeatureCollection.fromFeatures(geojson).toJson());
        output.close();
    }
    
    private static void writeFlightPath(Drone drone, String day, String month, String year) throws IOException {
        var output = new FileWriter("flightpath-" + day + "-" + month + "-" + year + ".txt");   
        var lineNumber = 1;
        var previousPosition = drone.getStartPosition();
        var paths = drone.getPathsList();
        var sensors = drone.getVisitedSensorsList();
        
        for (int i = 0; i < paths.size(); i++) {
            // These two lists always have the same size
            var positions = paths.get(i).getPositions();
            var angles = paths.get(i).getMoveAngles();
            
            for (int j = 0; j < positions.size(); j++) {
                // Write the line in the format specified
                output.write(lineNumber + "," + previousPosition.longitude() + "," + previousPosition.latitude() + "," + angles.get(j)
                    + "," + positions.get(j).longitude() + "," + positions.get(j).latitude());
                
                // Check which ending for the line is correct
                if (j == positions.size() - 1 && i != paths.size() - 1) {
                    output.write("," + sensors.get(i).getWhatThreeWords() + "\n");
                } else {
                    output.write(",null\n");
                }
                
                lineNumber++;
                previousPosition = positions.get(j);
            }
        }
        
        output.close();
    }
}
