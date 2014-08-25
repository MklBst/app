package com.brainydroid.daydreaming.db;

import com.google.gson.JsonObject;

import java.lang.String;
import java.util.ArrayList;

public class ServerParametersJson {

    @SuppressWarnings("UnusedDeclaration")
    private static String TAG = "ServerParametersJson";

    public String expId = "not given";
    public int expDuration = -1;
    public String urlBackendApi = "";
    public String urlResultsPage = "";

    public static String DEFAULT_PARAMETERS_VERSION = "-1";
    public static int DEFAULT_N_SLOTS_PER_PROBE = -1;
    public static int DEFAULT_SCHEDULING_MEAN_DELAY = -1;
    public static int DEFAULT_SCHEDULING_MIN_DELAY = -1;

    public String version = DEFAULT_PARAMETERS_VERSION;
    public int nSlotsPerProbe = DEFAULT_N_SLOTS_PER_PROBE;
    public int schedulingMeanDelay = DEFAULT_SCHEDULING_MEAN_DELAY;
    public int schedulingMinDelay = DEFAULT_SCHEDULING_MIN_DELAY;

    ArrayList<Question> questions = new ArrayList<Question>();
    public String welcomeText = "not given";
    public String descriptionText = "not given";

    public FirstLaunch firstLaunch = null;

    public synchronized ArrayList<Question> getQuestionsArrayList() {
        return questions;
    }

    public synchronized String getVersion() {
        return version;
    }

    public synchronized String getWelcomeText() {
        return welcomeText;
    }

    public synchronized String getDescriptionText() {
        return descriptionText;
    }


    public synchronized int getExpDuration() {
        return expDuration;
    }

    public synchronized String getExpId() {
        return expId;
    }

    public synchronized String getUrlBackendApi() {
        return urlBackendApi;
    }

    public synchronized String getUrlResultsPage() {
        return urlResultsPage;
    }



    public synchronized int getNSlotsPerProbe() {
        return nSlotsPerProbe;
    }

    public synchronized int getSchedulingMeanDelay() {
        return schedulingMeanDelay;
    }

    public synchronized int getSchedulingMinDelay() {
        return schedulingMinDelay;
    }

    public synchronized FirstLaunch getFirstLaunch() {
        return firstLaunch;
    }


}
