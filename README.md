# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/3.4.1/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.4.1/gradle-plugin/packaging-oci-image.html)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)

# order of database insert for a given year (2024 example)
* Manual corrections need to be made to the xls files to account for substitute players and team players subbing
* for other teams. Must add the team_id the sub is playing for in the score sheet column F (same row as player)
* There was also an entire day (6/12/24) missing from the spreadsheet that has the rounds setup.

* insert team from team-yyyy.txt - manually created file
* insert player from players-yyyy.txt, players-yyyy-sub.txt - manually created file
* insert season from season-yyyy.txt - manually created file
* insert tee_time from tee-time-yyyy.txt - manually created file  (this table may not be needed)
* run importWeeks via rest-requests.http to create week-yyyy.txt
* * import week from week-yyyy.txt
* Create a file to xref date of week to week_id must be created from week table as input into import of match
*       name: week-extract-yyyy.txt, contents: week_id, date string (yyyy-mm-dd)
* run importMatch via rest-requests-http to create match-yyyy.txt
* create match extract file (match_id, week_id, team1_id, team2_id) to be used by round import
* create player extract file (id,fname,lname,email,handicap,phone,team_id) to be used by round import
* run import of round via rest-requests-http to create round-yyyy.txt (file should have 384 rows 6x4x16=384)
* run import of score?