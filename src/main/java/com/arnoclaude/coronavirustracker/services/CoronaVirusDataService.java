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
import java.util.List;

@Service
public class CoronaVirusDataService {

    private static String VIRUS_DATA_URL = "https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_confirmed_global.csv";
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
        System.out.println(httpResponse.body());

        StringReader csvBodyReader = new StringReader(httpResponse.body());
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(csvBodyReader);
        for (CSVRecord record : records) {
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
        }
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
