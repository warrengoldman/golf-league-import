package com.sdg.golf.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class ImportRoundService extends ImportService {
    private static final Logger logger = LoggerFactory.getLogger(ImportRoundService.class);

    public void importRounds(String fileName, int seasonId, int year) throws Exception {
        Map<Date, Integer> weekDateToIdMap = getWeekDateToIdMap(year);
        List<PlayerExtract> playerExtract = getPlayerExtract(year);
        Map<Date, List<MatchPlayers>> seasonMatchMap = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(getPath(fileName).toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Date roundDate = getDateFromSheetName(sheet.getSheetName());
                if (roundDate != null) {
                    List<MatchPlayers> matchesForDate = new ArrayList<>();
                    MatchPlayers match = new MatchPlayers();

                    for (Row row : sheet) {
                        RoundPlayer roundPlayer = getRoundPlayer(row);
                        if (roundPlayer != null) {
                            match.playersInMatch.add(roundPlayer);
                            if (match.isRoundFull()) {
                                matchesForDate.add(match);
                                match = new MatchPlayers();
                            }
                        }
                    }
                    seasonMatchMap.put(roundDate, matchesForDate);
                }
            }
        }

        SeasonRoundResult srr = getRounds(seasonMatchMap, playerExtract, weekDateToIdMap, year);
        if (srr.errors.isEmpty()) {
            String header = "player_id,match_id,team_id,handicap";
            List<String> rounds = getRoundsRows(srr.rounds);
            writeStringsToFile(Path.of(String.format(ROUNDS_IMPORT_FILE, year)), header, rounds);
        } else {
            logger.error(String.join(System.lineSeparator(), srr.errors));
        }
    }

    private List<String> getRoundsRows(List<Round> rounds) {
        return rounds.stream()
                .map(round -> round.playerId + "," + round.matchId + "," + round.teamId + "," + round.handicap)
                .toList();
    }

    private SeasonRoundResult getRounds(Map<Date, List<MatchPlayers>> seasonMatchMap, List<PlayerExtract> playerExtract, Map<Date, Integer> weekDateToIdMap, int year) throws IOException {
        Map<String, List<PlayerExtract>> playerExtractMap = createPlayerExtractMap(playerExtract);
        Map<Integer, List<Match>> weekIdToMatch = getWeekIdToMatchMap(year);
        SeasonRoundResult srr = new SeasonRoundResult();

        for (Date roundDate : seasonMatchMap.keySet()) {
            Integer weekId = weekDateToIdMap.get(roundDate);
            if (weekId != null) {
                List<MatchPlayers> aWeeksMatches = seasonMatchMap.get(roundDate);
                for (MatchPlayers match : aWeeksMatches) {
                    for (RoundPlayer playerInMatch : match.playersInMatch) {
                        try {
                            PlayerExtract matchingPlayerExtract = findMatchingPlayerExtract(playerInMatch, playerExtractMap, roundDate);
                            int playerId = matchingPlayerExtract.playerId();
                            int teamId = playerInMatch.teamIdForRound > 0 ? playerInMatch.teamIdForRound : matchingPlayerExtract.teamId();
                            int matchId = getMatchId(weekIdToMatch.get(weekId), teamId, roundDate, matchingPlayerExtract);
                            double handicap = playerInMatch.handicapOnCard;
                            srr.rounds.add(new Round(playerId, matchId, teamId, handicap));
                        } catch (Exception e) {
                            srr.errors.add(e.getMessage());
                        }
                    }
                }
            }
        }
        return srr;
    }

    private PlayerExtract findMatchingPlayerExtract(RoundPlayer playerInMatch, Map<String, List<PlayerExtract>> playerExtractMap, Date roundDate) throws Exception {
        String[] playerOnCardToken = playerInMatch.nameOnCard.split(" ");

        try {
            return getPlayerExtract(playerOnCardToken, playerExtractMap);
        } catch (Exception e) {
            throw new Exception(e.getMessage() + " for round date %s".formatted(roundDate));
        }
    }

    private int getMatchId(List<Match> matchesForWeek, int teamId, Date roundDate, PlayerExtract matchingPlayerExtract) throws Exception {
        for (Match match : matchesForWeek) {
            if (match.team1Id == teamId || match.team2Id == teamId) {
                return match.id;
            }
        }
        throw new Exception(("Could not find match id for teamId: %s, roundDate: %s, playerExtract: %s").formatted(teamId, roundDate, matchingPlayerExtract));
    }

    private Map<Integer, List<Match>> getWeekIdToMatchMap(int year) throws IOException {
        List<Match> matchesForYear = readAllLines(String.format(MATCH_EXTRACT_FILE, year)).stream()
                .map(line -> {
                    String[] lineTokens = line.split(",");
                    return new Match(Integer.parseInt(lineTokens[0]), Integer.parseInt(lineTokens[1]),
                            Integer.parseInt(lineTokens[2]), Integer.parseInt(lineTokens[3]));
                }).toList();

        Map<Integer, List<Match>> weekIdToMatch = new HashMap<>();
        for (Match match : matchesForYear) {
            weekIdToMatch.computeIfAbsent(match.weekId, k -> new ArrayList<>()).add(match);
        }
        return weekIdToMatch;
    }

    private RoundPlayer getRoundPlayer(Row row) {
        if (row.getCell(0) != null) {
            String name = row.getCell(0).getStringCellValue();
            if (name != null && !name.equalsIgnoreCase("player 1") && !name.equalsIgnoreCase("player 2")) {
                Cell cell1 = row.getCell(1);
                if (cell1 != null && cell1.getCellType() == CellType.NUMERIC) {
                    int handicap = (int) cell1.getNumericCellValue();
                    RoundPlayer roundPlayer = new RoundPlayer(name, handicap);
                    Cell cellTeamIdOnRound = row.getCell(5);
                    if (cellTeamIdOnRound != null && cellTeamIdOnRound.getCellType() == CellType.NUMERIC) {
                        roundPlayer.setTeamIdForRound((int) cellTeamIdOnRound.getNumericCellValue());
                    }
                    return roundPlayer;
                }
            }
        }
        return null;
    }

    public Date getDateFromSheetName(String sheetName) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse("20" + sheetName);
        } catch (ParseException e) {
            return null;
        }
    }

    public static class RoundPlayer {
        private final String nameOnCard;
        private final int handicapOnCard;
        private int teamIdForRound;

        public RoundPlayer(String nameOnCard, int handicapOnCard) {
            this.nameOnCard = nameOnCard;
            this.handicapOnCard = handicapOnCard;
        }

        public String getNameOnCard() {
            return nameOnCard;
        }

        public int getHandicapOnCard() {
            return handicapOnCard;
        }

        public int getTeamIdForRound() {
            return teamIdForRound;
        }

        public void setTeamIdForRound(int teamIdForRound) {
            this.teamIdForRound = teamIdForRound;
        }
    }

    public record Match(int id, int weekId, int team1Id, int team2Id) {}

    public static class MatchPlayers {
        List<RoundPlayer> playersInMatch = new ArrayList<>();

        public boolean isRoundFull() {
            return playersInMatch.size() > 3;
        }
    }

    public static class SeasonRoundResult {
        List<Round> rounds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
    }

    public record Round(int playerId, int matchId, int teamId, double handicap) {}
}