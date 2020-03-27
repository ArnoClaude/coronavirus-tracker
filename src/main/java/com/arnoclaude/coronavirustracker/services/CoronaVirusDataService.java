package com.arnoclaude.coronavirustracker.services;

import com.arnoclaude.coronavirustracker.models.LocationStats;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Service
public class CoronaVirusDataService {

    private static String VIRUS_DATA_URL = "https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_confirmed_global.csv";
    private static String DEATHS_DATA_URL = "https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_deaths_global.csv";
    private List<LocationStats> allStats = new ArrayList<>();
    private boolean sorting = true; //true=nach total cases; false=nach absolutem anstieg

    @PostConstruct //runs this method as soon as object gets created
    @Scheduled(cron = "* * 1 * * *") //second minute hour day month year    //this will update first hour of every day
    public void fetchVirusData() throws IOException, InterruptedException {
        List<LocationStats> newStats = new ArrayList<>();   //newStats for concurrency reasons. The method takes some time to populate newStats
        //and then newStats get copied to allStats. That way if someone calls allStats while newStats
        //is getting populated, they don't get an error
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VIRUS_DATA_URL))
                .build();
        HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        //System.out.println(httpResponse.body());

        StringReader csvBodyReader = new StringReader(httpResponse.body());
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(csvBodyReader);

        //
        HttpClient client2 = HttpClient.newHttpClient();
        HttpRequest request2 = HttpRequest.newBuilder()
                .uri(URI.create(DEATHS_DATA_URL))
                .build();
        HttpResponse<String> httpResponse2 = client2.send(request2, HttpResponse.BodyHandlers.ofString());
        StringReader csvBodyReader2 = new StringReader(httpResponse2.body());
        Iterable<CSVRecord> records2 = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(csvBodyReader2);

        Iterator<CSVRecord> it1 = records.iterator();
        Iterator<CSVRecord> it2 = records2.iterator();
        CSVRecord dummy1;
        CSVRecord dummy2;

        while (it1.hasNext() && it2.hasNext()) {
            dummy1 = it1.next();
            dummy2 = it2.next();
            LocationStats locationStat = new LocationStats();
            locationStat.setState(dummy1.get("Province/State"));
            locationStat.setCountry(dummy1.get("Country/Region"));
            int latestCases = 0;
            if (!dummy1.get(dummy1.size() - 1).equals("")) {
                latestCases = Integer.parseInt(dummy1.get(dummy1.size() - 2));
            }
            int prevDayCases = 0;
            if (!dummy1.get(dummy1.size() - 2).equals("")) {
                prevDayCases = Integer.parseInt(dummy1.get(dummy1.size() - 3));
            }
            locationStat.setLatestTotalCases(latestCases);
            locationStat.setDiffFromPrevDay(latestCases - prevDayCases);
            double diffFromPrevDayPercentage = 0.0;
            if (prevDayCases != 0) {
                diffFromPrevDayPercentage = (double) latestCases / (double) prevDayCases;
            }
            diffFromPrevDayPercentage -= 1;
            diffFromPrevDayPercentage *= 100;
            diffFromPrevDayPercentage = (double) Math.round(diffFromPrevDayPercentage * 100d) / 100d;
            if (diffFromPrevDayPercentage == -100.0) diffFromPrevDayPercentage = 0; //to avoid bug when no changes
            locationStat.setDiffFromPrevDayPercentage(diffFromPrevDayPercentage);
            System.out.println(dummy2.size()-1);
            locationStat.setLatestTotalDeaths(Integer.parseInt(dummy2.get(dummy2.size() - 1)));
            newStats.add(locationStat);
        }
        //
        /*for (CSVRecord record : records) {
            LocationStats locationStat = new LocationStats();
            locationStat.setState(record.get("Province/State"));
            locationStat.setCountry(record.get("Country/Region"));
            int latestCases = 0;
            if (!record.get(record.size() - 1).equals("")) {
                latestCases = Integer.parseInt(record.get(record.size() - 2));
            }
            int prevDayCases = 0;
            if (!record.get(record.size() - 2).equals("")) {
                prevDayCases = Integer.parseInt(record.get(record.size() - 3));
            }
            locationStat.setLatestTotalCases(latestCases);
            locationStat.setDiffFromPrevDay(latestCases - prevDayCases);
            double diffFromPrevDayPercentage = 0.0;
            if (prevDayCases != 0) {
                diffFromPrevDayPercentage = (double) latestCases / (double) prevDayCases;
            }
            diffFromPrevDayPercentage -= 1;
            diffFromPrevDayPercentage *= 100;
            diffFromPrevDayPercentage = (double) Math.round(diffFromPrevDayPercentage * 100d) / 100d;
            if (diffFromPrevDayPercentage == -100.0) diffFromPrevDayPercentage = 0; //to avoid bug when no changes
            locationStat.setDiffFromPrevDayPercentage(diffFromPrevDayPercentage);
            newStats.add(locationStat);
        }*/
        this.allStats = newStats;
        if (sorting == true) {
            Collections.sort(allStats, (LocationStats a1, LocationStats a2) -> a2.getLatestTotalCases() - a1.getLatestTotalCases());
        } else if (sorting == false) {
            Collections.sort(allStats, (LocationStats a1, LocationStats a2) -> a2.getDiffFromPrevDay() - a1.getDiffFromPrevDay());
        }
    }

    public List<LocationStats> getAllStats() {
        return allStats;
    }

    public void swapSort() throws IOException, InterruptedException {
        System.out.println("swapSort called");
        if (sorting == true) sorting = false;
        else if (sorting == false) sorting = true;
        fetchVirusData();
    }

}
