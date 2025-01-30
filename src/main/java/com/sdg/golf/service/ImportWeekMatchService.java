package com.sdg.golf.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ImportWeekMatchService extends ImportService {
    private static final Logger logger = LoggerFactory.getLogger(ImportWeekMatchService.class);
    public void importMatchups(String fileName, int seasonId, int year) throws Exception {
        Map<String, Integer> nameToIdMap = getNameToIdMap(year);
        Map<Date, Integer> weekDateToIdMap = getWeekDateToIdMap(year);
        Map<Integer, Map<String, TwoTeam>> matchsKeyedByWeek = getTeeTimeMatchs(getPath(fileName), weekDateToIdMap);
        String header = "week_id,team_1,team_2";
        String outFileName = String.format(MATCH_IMPORT_FILE, year + "");
        List<String> matchTableRows = new ArrayList<>();
        for (Integer weekId : matchsKeyedByWeek.keySet()) {
            Map<String, TwoTeam> matchupTeeTimesMap = matchsKeyedByWeek.get(weekId);
            for (TwoTeam twoTeam: matchupTeeTimesMap.values()) {
                matchTableRows.add(weekId + "," + nameToIdMap.get(twoTeam.getTeam1()) + "," + nameToIdMap.get(twoTeam.getTeam2()));
            }

        }
        logger.debug("output to {}, header {}, matchTableRows {}", outFileName, header, matchTableRows);
        writeStringsToFile(Path.of(outFileName), header, matchTableRows);
        logger.warn("**** ATTENTION: AFTER LOADING THE FILE THIS PROCESS CREATES TO THE MATCH TABLE, " +
                "A MATCH-EXTRACT-YYYY.txt) IS NEEDED FOR THE IMPORT OF ROUNDS PROCESS WHERE THE EXTRACT IS" +
                "AN ACCUMULATION OF THE WEEK TABLE DATA CREATED BY THIS METHOD");
    }

    public void importWeeks(String fileName, int seasonId, int year) throws IOException {
        logger.debug("Processing file: {} for seasonId: {}", fileName, seasonId);
        AtomicInteger ctr = new AtomicInteger(1);
        List<String> rowsForWeeksTable = getDates(getPath(fileName)).stream().map(d -> "Week " + ctr.getAndIncrement() + "," + new SimpleDateFormat("yyyy-MM-dd").format(d) + "," + seasonId).toList();
        String outFileName = String.format(WEEKS_IMPORT_FILE, year + "-" + seasonId);
        String header = "name,date,season_id";
        logger.debug("output to {}, header {}, rowsForWeeksTable {}", outFileName, header, rowsForWeeksTable);
        writeStringsToFile(Path.of(outFileName), header, rowsForWeeksTable);
        logger.warn("**** ATTENTION: AFTER LOADING THE FILE THIS PROCESS CREATES TO THE WEEK TABLE, " +
                "A DATE TO WEEK_ID FILE (WEEK-EXTRACT-YYYY.txt) IS NEEDED FOR THE IMPORT OF MATCH PROCESS");
    }

    @NonNull
    private Map<String, Integer> getNameToIdMap(int year) throws IOException {
        Map<String, Integer> map = new HashMap<>();
        List<String> lines = readAllLines(String.format(TEAM_IMPORT_FILE, year + ""));
        for (String teamLine: lines) {
            if (!teamLine.equals("id,name")) {
                String[] teamLineTokens = teamLine.split(",");
                map.put(teamLineTokens[1], Integer.parseInt(teamLineTokens[0]));
            }
        }
        return map;
    }

    @NonNull
    private Map<Integer, Map<String, TwoTeam>> getTeeTimeMatchs(Path path, Map<Date, Integer> weekDateToIdMap) throws IOException {
        Map<Integer, Map<String, TwoTeam>> map = new HashMap<>();
        int EXPECTED_NBR_TEE_TIMES = 6;
        try (FileInputStream fis = new FileInputStream(path.toFile()); Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheet(WEEKLY_MATCHUPS_SHEET_NAME);
            Map<String, TwoTeam> weekMap = new HashMap<>();
            Integer weekId = null;
            for (Row row : sheet) {
                Date weekDate = getDateOfTeeTimes(row);
                if (weekDate != null) {
                    weekId = weekDateToIdMap.get(weekDate);
                }
                TeamNameAndTime tnat = getTeamNameAndTime(row);
                if (tnat != null) {
                    map.put(weekId, weekMap);
                    if (weekMap.size() == EXPECTED_NBR_TEE_TIMES &&
                            weekMap.values().stream().allMatch(TwoTeam::isFull)) {
                        weekMap = new HashMap<>();
                    }
                    TwoTeam twoTeam = weekMap.get(tnat.teamTime());
                    if (twoTeam == null) {
                        twoTeam = new TwoTeam();
                    }
                    twoTeam.addTeam(tnat.teamName());
                    weekMap.put(tnat.teamTime(), twoTeam);
                }
            }
        }
        return map;
    }

    private TeamNameAndTime getTeamNameAndTime(Row row) {
        TeamNameAndTime tnat = null;
        Cell teamNameCell = row.getCell(5);
        if (teamNameCell != null && Objects.requireNonNull(teamNameCell.getCellType()) == CellType.STRING) {
            Cell teeTimeCell = row.getCell(6);
            if (teeTimeCell != null && Objects.requireNonNull(teeTimeCell.getCellType()) == CellType.NUMERIC) {
                String teamName = teamNameCell.getStringCellValue();
                String teeTime = new SimpleDateFormat("hh:mm").format(teeTimeCell.getDateCellValue());
                tnat = new TeamNameAndTime(teamName, teeTime);
            }
        }
        return tnat;
    }
}
