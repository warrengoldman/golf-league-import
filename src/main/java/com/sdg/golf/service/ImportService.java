package com.sdg.golf.service;

import io.micrometer.common.util.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

public abstract class ImportService {
    private static final Logger logger = LoggerFactory.getLogger(ImportService.class);
    static final String WEEKLY_MATCHUPS_SHEET_NAME ="Weekly Matchups";
    static final String WEEKS_IMPORT_FILE = "files/week-%s.txt";
    static final String MATCH_IMPORT_FILE = "files/match-%s.txt";
    static final String TEAM_IMPORT_FILE = "files/team-%s.txt";
    static final String ROUNDS_IMPORT_FILE = "files/round-%s.txt";
    static final String WEEK_EXTRACT_FILE = "files/week-extract-%s.txt";
    static final String PLAYER_EXTRACT_FILE = "files/player-extract-%s.txt";
    static final String MATCH_EXTRACT_FILE = "files/match-extract-%s.txt";
    public static class TwoTeam {
        private String team1;
        private String team2;
        public String getTeam1() {
            return team1;
        }
        public String getTeam2() {
            return team2;
        }

        public void addTeam(String team) {
            if (StringUtils.isBlank(team1)) {
                team1 = team;
            } else {
                team2 = team;
            }
        }
        public boolean isFull() {
            return StringUtils.isNotBlank(team1) && StringUtils.isNotBlank(team2);
        }
    }

    Path getPath(String fileName) throws FileNotFoundException {
        var path = Path.of(fileName);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File does not exist: " + fileName);
        }
        return path;
    }

    List<Date> getDates(Path path) throws IOException {
        List<Date> dates = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(path.toFile()); Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheet(WEEKLY_MATCHUPS_SHEET_NAME);
            for (Row row : sheet) {
                Date date = getDateOfTeeTimes(row);
                if (date != null) {
                    dates.add(date);
                }
            }
        }

        logger.debug("Found {} dates", dates.size());
        Collections.sort(dates);
        return dates;
    }

    Date getDateOfTeeTimes(Row row) {
        Cell cell = row.getCell(0);
        if (cell != null && Objects.requireNonNull(cell.getCellType()) == CellType.NUMERIC) {
            Date date = cell.getDateCellValue();
            if (dateOfSeason(date)) {
                return date;
            }
        }
        return null;
    }

    void writeStringsToFile(Path outPath, String header, List<String> strs) throws IOException {
        String newLine = "";
        try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
            if (!header.isEmpty()) {
                fos.write(header.getBytes());
                newLine = System.lineSeparator();
            }
            for (String str: strs) {
                if (newLine.isEmpty()) {
                    newLine = System.lineSeparator();
                } else {
                    fos.write(newLine.getBytes());
                }
                fos.write(str.getBytes());
            }
        }
    }

    Map<Date, Integer> getWeekDateToIdMap(int year) throws Exception {
        List<String> lines = readAllLines(String.format(WEEK_EXTRACT_FILE, year + ""));
        Map<Date, Integer> map = new HashMap<>();
        for (String line : lines) {
            String[] weekExtractLine = line.split(",");
            Integer weekId = Integer.valueOf(weekExtractLine[0]);
            Date weekDate = new SimpleDateFormat("yyyy-MM-dd").parse(weekExtractLine[1]);
            map.put(weekDate, weekId);
        }
        return map;
    }

    boolean dateOfSeason(Date date) {
        return getLocalDate(date).getYear() > 2000;
    }

    LocalDate getLocalDate(Date date) {
        return LocalDate.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
    }

    List<String> readAllLines(String filePath) throws IOException {
        return Files.readAllLines(Paths.get(filePath));
    }
    public record TeamNameAndTime(String teamName, String teamTime) {}


    List<PlayerExtract> getPlayerExtract(int year) throws IOException {
        List<String> lines = readAllLines(String.format(PLAYER_EXTRACT_FILE, year));
        return lines.stream().map(this::createPlayerExtract).toList();
    }

    private PlayerExtract createPlayerExtract(String line) {
        String[] lineTokens = line.split(",");
        return new PlayerExtract(Integer.parseInt(lineTokens[0]), lineTokens[1], lineTokens[2], lineTokens[3],
                Double.parseDouble(lineTokens[4]), lineTokens[5], Integer.parseInt(lineTokens[6]));
    }

    Map<String, List<PlayerExtract>> createPlayerExtractMap(List<PlayerExtract> playerExtracts) {
        Map<String, List<PlayerExtract>> map = new HashMap<>();
        for (PlayerExtract playerExtract : playerExtracts) {
            map.computeIfAbsent(playerExtract.firstName().toLowerCase(), k -> new ArrayList<>()).add(playerExtract);
        }
        return map;
    }

    public PlayerExtract getPlayerExtract(String[] playerNameToken, Map<String, List<PlayerExtract>> playerExtractMap) throws Exception {
        String fname = filterName(playerNameToken[0]);
        List<PlayerExtract> playerRowsOnExtract = playerExtractMap.get(fname.toLowerCase());

        if (playerRowsOnExtract != null && !playerRowsOnExtract.isEmpty()) {
            if (playerRowsOnExtract.size() > 1 && playerNameToken.length == 2) {
                for (PlayerExtract playerRow : playerRowsOnExtract) {
                    if (playerNameToken[1].equalsIgnoreCase(playerRow.lastName.substring(0, 1))) {
                        return playerRow;
                    }
                }
            } else {
                return playerRowsOnExtract.getFirst();
            }
        }
        throw new Exception("No player on player extract file for %s".formatted(fname));
    }

    private String filterName(String fname) {
        return "baby".equalsIgnoreCase(fname) ? "Brien" : fname;
    }
    public record PlayerExtract(int playerId, String firstName, String lastName, String email, double handicap, String phone, int teamId) {}
}
