package uk.ac.ed.inf.heatmap;

import com.mapbox.geojson.*;
import java.util.Scanner;
import java.util.List;

public class App {
    
    /* 
     * These are values our program depends on stored as variables so they can be easily changed. 
     * We have assumed our confinement area is defined by a maximum and minimum for both longitude and latitude
     */
    private static final double MINIMUM_LONGITUDE = -3.184319;
    private static final double MAXIMUM_LONGITUDE = -3.192473;
    private static final double MINIMUM_LATITUDE = 55.942617;
    private static final double MAXIMUM_LATITUDE = 55.946233;
    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 10;
    private static final double FILL_OPACITY = 0.75;
    
    public static void main(String[] args) { 
        Scanner input = new Scanner(args[0]);
        input.useDelimiter(",");
        
    }
    
    // Maps an air quality estimate to its intended color code
    private static String getAirQualityColor(int airQuality) {
        if (airQuality >= 0 && airQuality < 32) {
            return "#00ff00";
        } else if (airQuality >= 32 && airQuality < 64) {
            return "#40ff00";
        } else if (airQuality >= 64 && airQuality < 96) {
            return "#80ff00";
        } else if (airQuality >= 96 && airQuality < 128) {
            return "#c0ff00";
        } else if (airQuality >= 128 && airQuality < 160) {
            return "#ffc000";
        } else if (airQuality >= 160 && airQuality < 192) {
            return "#ff8000";
        } else if (airQuality >= 192 && airQuality < 224) {
            return "#ff4000";
        } else if (airQuality >= 224 && airQuality < 256) {
            return "#ff0000";
        }
        throw new IllegalArgumentException("Air quality was not within the excepted range");
    }
}
