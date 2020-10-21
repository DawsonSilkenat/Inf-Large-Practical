package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;

import uk.ac.ed.inf.aqmaps.MapData;

public class App {
    
    public static void main(String[] args) throws IOException, InterruptedException {
        // Reading specified map 
        var webserver = "http://localhost:" + args[6];
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder().uri(URI.create(
                webserver + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json")).build(); 
        var responce = client.send(request, BodyHandlers.ofString());
        
        // Informing the user if there is a problem reading the specified map
        if (responce.statusCode() != 200) {
            
            System.out.println("Error: unable to connect to " + webserver + " at port " + args[6] + ".\n"
                    + "HTTP status code: " + responce.statusCode() + "\nExiting");
            
            System.exit(1);
        }
        
        // Parsing the json and reading additional information about the sensors 
        Type listType = new TypeToken<ArrayList<MapData>>() {}.getType();
        ArrayList<MapData> mapEntries = new Gson().fromJson(responce.body(), listType);
        
        for (var entry : mapEntries) {
            request = HttpRequest.newBuilder().uri(URI.create(
                    webserver + "/words/" + entry.getLocation().replace('.', '/') + "details.json")).build();
            responce = client.send(request, BodyHandlers.ofString());
            
            if (responce.statusCode() != 200) {
                System.out.println("Error: unable to connect to " + webserver + " at port " + args[6] + ".\n"
                        + "HTTP status code: " + responce.statusCode() + "\nThis entry will be skipped.");
            } else {
                What3WordsData sensorInfo = new Gson().fromJson(responce.body(), What3WordsData.class);
                
            }
        }
        
    }
}
