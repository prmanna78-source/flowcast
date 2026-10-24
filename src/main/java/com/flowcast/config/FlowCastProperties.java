package com.flowcast.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowcast")
public class FlowCastProperties {
    private String tomtomServerKey = "";
    private String tomtomBrowserKey = "";
    private String tomtomSearchKey = "";
    private boolean openMeteoEnabled = true;
    private boolean trafficHistoryEnabled;
    private boolean trafficTrainingPermission;
    private double latitude = 17.6868;
    private double longitude = 83.2185;
    private String locationName = "Visakhapatnam, Andhra Pradesh";
    private String defaultCity = "Visakhapatnam";
    private int refreshIntervalSeconds = 30;
    private int collectorIntervalSeconds = 300;
    private int minTrainingSamples = 1000;
    private int maxPredictionHorizonMinutes = 60;

    public String getTomtomServerKey() { return tomtomServerKey; }
    public void setTomtomServerKey(String value) { tomtomServerKey = value == null ? "" : value; }
    public String getTomtomBrowserKey() { return tomtomBrowserKey; }
    public void setTomtomBrowserKey(String value) { tomtomBrowserKey = value == null ? "" : value; }
    public String getTomtomSearchKey() { return tomtomSearchKey; }
    public void setTomtomSearchKey(String value) { tomtomSearchKey = value == null ? "" : value; }
    public boolean isOpenMeteoEnabled() { return openMeteoEnabled; }
    public void setOpenMeteoEnabled(boolean value) { openMeteoEnabled = value; }
    public boolean isTrafficHistoryEnabled() { return trafficHistoryEnabled; }
    public void setTrafficHistoryEnabled(boolean value) { trafficHistoryEnabled = value; }
    public boolean isTrafficTrainingPermission() { return trafficTrainingPermission; }
    public void setTrafficTrainingPermission(boolean value) { trafficTrainingPermission = value; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double value) { latitude = value; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double value) { longitude = value; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String value) { locationName = value; }
    public String getDefaultCity() { return defaultCity; }
    public void setDefaultCity(String value) { defaultCity = value; }
    public int getRefreshIntervalSeconds() { return refreshIntervalSeconds; }
    public void setRefreshIntervalSeconds(int value) { refreshIntervalSeconds = value; }
    public int getCollectorIntervalSeconds() { return collectorIntervalSeconds; }
    public void setCollectorIntervalSeconds(int value) { collectorIntervalSeconds = value; }
    public int getMinTrainingSamples() { return minTrainingSamples; }
    public void setMinTrainingSamples(int value) { minTrainingSamples = value; }
    public int getMaxPredictionHorizonMinutes() { return maxPredictionHorizonMinutes; }
    public void setMaxPredictionHorizonMinutes(int value) { maxPredictionHorizonMinutes = value; }
}
