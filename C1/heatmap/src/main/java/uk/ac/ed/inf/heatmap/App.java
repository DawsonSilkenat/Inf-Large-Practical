package uk.ac.ed.inf.heatmap;

import com.mapbox.geojson.*;
import java.util.Scanner;


public class App {
    
    
    public static void main(String[] args) { 
        Scanner input = new Scanner(args[0]);
        input.useDelimiter(",");
    }
    
    // Maps an air quality estimate to its intended color code
    private static String getColor(int airQuality) {
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
