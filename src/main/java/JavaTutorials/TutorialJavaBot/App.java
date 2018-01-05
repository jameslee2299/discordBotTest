//

package JavaTutorials.TutorialJavaBot;

import JavaTutorials.TutorialJavaBot.keyChain;

import javax.security.auth.login.LoginException;
import java.sql.*;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import net.rithms.riot.api.ApiConfig;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.static_data.constant.Locale;
import net.rithms.riot.api.endpoints.static_data.dto.Champion;
import net.rithms.riot.api.endpoints.static_data.dto.ChampionList;
import net.rithms.riot.api.endpoints.summoner.dto.Summoner;
import net.rithms.riot.api.endpoints.champion_mastery.dto.ChampionMastery;
import net.rithms.riot.constant.Platform;

import org.sqlite.SQLiteDataSource;

public class App extends ListenerAdapter {

    // Key to be used across entire class
    public String riotApiKey = keyChain.getAPIKey();

    ApiConfig config = new ApiConfig().setKey(riotApiKey);
    RiotApi api = new RiotApi(config);

    // Build and initialize the JDA bot
    public static void main( String[] args ) throws LoginException, IllegalArgumentException, InterruptedException, RateLimitedException {

        JDA jdaBot = new JDABuilder(AccountType.BOT).setToken(keyChain.getBotToken()).buildBlocking();
        jdaBot.addEventListener(new App());
    }

    int tempCounter = 1;
    
    @Override
    public void onMessageReceived(MessageReceivedEvent e) {

        // Objects to store info about messages sent in channel
    	Message objMsg = e.getMessage();
    	MessageChannel objChannel = e.getChannel();
    	User objUser = e.getAuthor();

    	// Parse user input/commands
    	String[] userInputs = objMsg.getContentDisplay().split(" ");

    	//System.out.println(objMsg.getContentDisplay());
    	//System.out.println(userInputs[0]);
        //System.out.println(userInputs[1]);


        // If user gives !summoner command, grab summoner info and send messages to channel with info
        if (userInputs[0].equals("!summoner")) {
            try {
                Summoner summoner = getSummonerInfo(userInputs[1]);
                objChannel.sendMessage("Name: " + summoner.getName()).queue();
                objChannel.sendMessage("Summoner ID: " + Long.toString(summoner.getId())).queue();
                objChannel.sendMessage("Account ID: " + Long.toString(summoner.getAccountId())).queue();
                objChannel.sendMessage("Summoner Level: " + Long.toString(summoner.getSummonerLevel())).queue();
                objChannel.sendMessage("Profile Icon ID: " + Long.toString(summoner.getProfileIconId())).queue();
            } catch (RiotApiException ex) {
                System.err.println("Connection RiotAPIException: " + ex.getMessage());
            }
        }

        // If user gives !mstats command, grab the List of champion mastery for the username and send the first three
        // champion mastery info (Riot automatically orders by descending)
        if (userInputs[0].equals("!mstats")) {
            try {
                List<ChampionMastery> masteryList = getMasteryBySummonerName(userInputs[1]);
                for (int i = 0; i < 3; i++) {
                    ChampionMastery currentChampion = masteryList.get(i);
                    objChannel.sendMessage("Rank: " + (i + 1)).queue();
                    try {
                        objChannel.sendMessage("Champion Name: " +
                                getChampionData(currentChampion.getChampionId()).getName()).queue();
                    } catch (RiotApiException ex) {
                        System.err.println("Connection RiotAPIException: " + ex.getMessage());
                    }

                    objChannel.sendMessage("Mastery Level: " + currentChampion.getChampionLevel()).queue();
                    objChannel.sendMessage("Mastery Points: " + currentChampion.getChampionPoints()).queue();
                    //objChannel.sendMessage(" ").queue();
                }
            } catch (RiotApiException ex) {
                System.err.println("Connection RiotAPIException: " + ex.getMessage());
            }
        }

        // If user gives !profile command, grab all info and lay out in Discord's embed format using JDA methods
        if (userInputs[0].equals("!profile")) {

            EmbedBuilder profileBox = new EmbedBuilder(); // Use JDA method to use EmbedBuilder
            profileBox.setTitle("Here is a summary of your LoL stats:"); // Set Title of EmbedBuilder

            StringBuilder championMasteryField = new StringBuilder(); // Initialize StringBuilder to save memory

            Summoner summoner = null;
            try {
                summoner = getSummonerInfo(userInputs[1]); // Get summoner info and place into object
            } catch (RiotApiException ex) {
                System.err.println("Riot API Exception: " + ex.getMessage());
            }

            profileBox.setAuthor("Profile: " + summoner.getName()); // Set top line to summoner name from object

                // Get Mastery List and place into object
            List<ChampionMastery> masteryList = null;
            try {
                masteryList = getMasteryBySummonerId(summoner.getId());
            } catch (RiotApiException ex) {
                System.err.println("Riot API Exception: " + ex.getMessage());
            }

            // Connect to db and build string
            Connection conn = null;
            String queryString = null;
            PreparedStatement queryChamp = null;

            try {
                conn = dbConnect("champ_list_test.db");
                queryString = "SELECT champ_name FROM champlist WHERE champ_id = ?";
                queryChamp = conn.prepareStatement(queryString);
            } catch (SQLException s) {
                System.err.println("SQL Exception: " + s);
            }

            // For loop 3 times for the top 3 champion mastery info
            for (int i = 0; i < 3; i++) {
                // Set current champion to current position
                ChampionMastery currentChampion = masteryList.get(i);
                String champName = null;
                try {
                    queryChamp.setInt(1, currentChampion.getChampionId());

                    ResultSet rs = queryChamp.executeQuery();
                    rs.next();
                    champName = rs.getString(1);
                } catch (SQLException s) {
                    System.err.println("SQL Exception: " + s.getMessage());
                }
                // Build string
                championMasteryField.append("[").append(currentChampion.getChampionLevel()).append("] ")
                        .append(i + 1).append(". ").append(champName).append(": ")
                        .append(currentChampion.getChampionPoints()).append("\n");
            }

            // Turn built string into an actual string and add to Field for Embed
            profileBox.addField("Top Champions:", championMasteryField.toString(), true);

            // Build the Embed box and send message
            objChannel.sendMessage(profileBox.build()).queue();
        }

        if (userInputs[0].equals("!champlistdb") && tempCounter == 1) {
            try {
                ChampionList champList = getChampionList();
                Map<String, Champion> map = champList.getData();
                try {
                    Connection conn = dbConnect("champ_list_test.db");

                    String sql = "CREATE TABLE IF NOT EXISTS champlist " +
                            "(champ_id INTEGER PRIMARY KEY, champ_name text NOT NULL)";
                    Statement s = conn.createStatement();
                    s.executeUpdate(sql);

                    PreparedStatement updateTable = null;

                    String updateString = "INSERT OR IGNORE INTO champlist (champ_id, champ_name) VALUES " +
                            "(?, ?)";

                    updateTable = conn.prepareStatement(updateString);

                    for (Map.Entry<String, Champion> entry : map.entrySet()) {
                        Champion currentChamp = entry.getValue();
                        int currentChampId = currentChamp.getId();
                        String currentChampName = currentChamp.getName();
                        updateTable.setInt(1, currentChampId);
                        updateTable.setString(2, currentChampName);
                        updateTable.executeUpdate();
                    }

                    ResultSet rs = s.executeQuery("SELECT * FROM champlist");
                    while (rs.next()) {
                        System.out.println(rs.getInt(1));
                        System.out.println(rs.getString(2));
                    }
                } catch (SQLException s) {
                    System.err.println("SQL Exception: " + s.getMessage());
                }
            }
            catch (RiotApiException ex) {
                System.err.println("Connection RiotAPIException: " + ex.getMessage());
            }

            tempCounter = 0;
        }

        if (userInputs[0].equals("!printchamp")) {
            int champId = Integer.parseInt(userInputs[1]);
            try {
                Connection conn = dbConnect("champ_list_test.db");
                PreparedStatement queryChamp = null;
                String queryString = "SELECT * FROM champlist WHERE champ_id = ?";

                queryChamp = conn.prepareStatement(queryString);

                queryChamp.setInt(1, champId);
                ResultSet rs = queryChamp.executeQuery();
                if (!rs.isBeforeFirst()) {
                    objChannel.sendMessage("No champion with that ID").queue();
                }
                else {
                    while (rs.next()) {
                        objChannel.sendMessage("Name: " + rs.getString(2)).queue();
                    }
                }
            } catch (SQLException s) {
                System.err.println("SQL Exception: " + s.getMessage());
            }
        }
    }

    // Get summoner info for a given summoner name
    public Summoner getSummonerInfo(String s) throws RiotApiException {
        return api.getSummonerByName(Platform.NA,s);
    }

    // Get full list of champion mastery for a given summoner id
    public List<ChampionMastery> getMasteryBySummonerId(long l) throws RiotApiException {
        return api.getChampionMasteriesBySummoner(Platform.NA,l);
    }

    // Get full list of champion mastery for a given summoner name
    public List<ChampionMastery> getMasteryBySummonerName(String s) throws RiotApiException {
        return api.getChampionMasteriesBySummoner(Platform.NA,getSummonerInfo(s).getId());
    }

    // Get static champion data by champion ID
    public Champion getChampionData(int n) throws RiotApiException {
        return api.getDataChampion(Platform.NA,n);
    }

    public ChampionList getChampionList() throws RiotApiException {
        return api.getDataChampionList(Platform.NA, Locale.EN_US,null,true);
    }

    public Connection dbConnect(String dbName) throws SQLException {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbName);
        Connection conn = null;
        conn = ds.getConnection();
        System.out.println("Connection successful");
        return conn;
    }
}