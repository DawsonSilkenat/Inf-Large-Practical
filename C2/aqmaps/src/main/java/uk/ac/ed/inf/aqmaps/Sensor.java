package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Feature;

public class Sensor {

    private double longitude;
    private double latitude;
    private double batteryLife;
    private String reading;
    private String what3words;
    private String markerSymbol;
    private String markerColor;
    private String markerSize;

    public Sensor(double longitude, double latitude, double batteryLife, String reading, 
            String what3words, String markerSymbol, String markerColor, String markerSize) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.batteryLife = batteryLife;
        this.reading = reading;
        this.what3words = what3words;
        this.markerSymbol = markerSymbol; 
        this.markerColor = markerColor;
        this.markerSize = markerSize;
    }
    
    // Simplified constructor including default values for easier use
    public Sensor(double longitude, double latitude, String what3words, double batteryLife, String reading) {
        this(longitude, latitude, batteryLife, reading, what3words, (String) null, "#aaaaaa", "medium");
    }
    
    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }
    
    public double getBatteryLife() {
        return batteryLife;
    }

    public String getReading() {
        return reading;
    }
    
    public void setMarkerSymbol(String markerSymbol) {
        this.markerSymbol = markerSymbol;
    }

    public void setMarkerColor(String markerColor) {
        this.markerColor = markerColor;
    }
    
    // Returns the geojson feature representing this sensor
    public Feature toGeojsonFeature() {
        Feature asGeoJson = Feature.fromGeometry(Point.fromLngLat(longitude, latitude));
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
