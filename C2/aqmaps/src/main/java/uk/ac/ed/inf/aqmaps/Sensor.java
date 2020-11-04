package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Feature;

public class Sensor {

//    private double longitude;
//    private double latitude;
    private Point position;
    private double batteryLife;
    private double minimumReliableBattery;
    private String reading;
    private String what3words;
    private String markerSymbol;
    private String markerColor;
    private String markerSize;

    public Sensor(Point position, double batteryLife, double minimumReliableBattery, String reading, 
            String what3words, String markerSymbol, String markerColor, String markerSize) {
        this.position = position;
        this.batteryLife = batteryLife;
        this.minimumReliableBattery = minimumReliableBattery;
        this.reading = reading;
        this.what3words = what3words;
        this.markerSymbol = markerSymbol; 
        this.markerColor = markerColor;
        this.markerSize = markerSize;
    }
    
    // Simplified constructor including default values for easier use
    public Sensor(Point position, double batteryLife, String reading, String what3words) {
        this(position, batteryLife, 10.0, reading, what3words, (String) null, "#aaaaaa", "medium");
    }
    
    public Point getPosition() {
        return position;
    }
    
    public void visit() {
        if (batteryLife >= minimumReliableBattery) {
            // We believe the reading to be accurate, so update according to reading
            double airQuality = Double.parseDouble(reading);
            
            if (airQuality >= 0 && airQuality < 32) {
                markerColor = "#00ff00";
                markerSymbol = "lighthouse";
            } else if (airQuality >= 32 && airQuality < 64) {
                markerColor = "#40ff00";
                markerSymbol = "lighthouse";
            } else if (airQuality >= 64 && airQuality < 96) {
                markerColor = "#80ff00";
                markerSymbol = "lighthouse";
            } else if (airQuality >= 96 && airQuality < 128) {
                markerColor = "#c0ff00";
                markerSymbol = "lighthouse";
            } else if (airQuality >= 128 && airQuality < 160) {
                markerColor = "#ffc000";
                markerSymbol = "danger";
            } else if (airQuality >= 160 && airQuality < 192) {
                markerColor = "#ff8000";
                markerSymbol = "danger";
            } else if (airQuality >= 192 && airQuality < 224) {
                markerColor = "#ff4000";
                markerSymbol = "danger";
            } else if (airQuality >= 224 && airQuality < 256) {
                markerColor = "#ff0000";
                markerSymbol = "danger";
            } else {
                // The reading should have been in one of these ranges, if not inform the user and leave marked as unvisited
                System.out.println("The air quality reading was not within an excepted range for the sensor at " + what3words 
                        + ". This sensor will not be marked as visited");
            }    
        } else {
            // We don't believe the reading to be accurate, so ignore reading and mark for battery replacement
            markerColor = "#000000";
            markerSymbol = "cross";
        }  
    }
    
    public Feature toGeojsonFeature() {
        Feature asGeoJson = Feature.fromGeometry(position);
        asGeoJson.addStringProperty("marker-size", markerSize);
        asGeoJson.addStringProperty("location", what3words);
        asGeoJson.addStringProperty("rgb-string", markerColor);
        asGeoJson.addStringProperty("marker-color", markerColor);
        if (markerSymbol != null) {
            asGeoJson.addStringProperty("marker-symbol", markerSymbol);
        }
        return asGeoJson;
    }   
}
