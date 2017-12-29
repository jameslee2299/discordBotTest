//

package JavaTutorials.TutorialJavaBot;

import JavaTutorials.TutorialJavaBot.keyChain;

import javax.security.auth.login.LoginException;
import java.util.List;
import java.util.stream.IntStream;

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
import net.rithms.riot.api.endpoints.static_data.dto.Champion;
import net.rithms.riot.api.endpoints.summoner.dto.Summoner;
import net.rithms.riot.api.endpoints.champion_mastery.dto.ChampionMastery;
import net.rithms.riot.constant.Platform;

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
                IntStream.range(0, 3).forEachOrdered(n -> {
                    ChampionMastery currentChampion = masteryList.get(n);
                    objChannel.sendMessage("Rank: " + (n + 1)).queue();
                    try {
                        objChannel.sendMessage("Champion Name: " +
                                getChampionData(currentChampion.getChampionId()).getName()).queue();
                    } catch (RiotApiException ex) {
                        System.err.println("Connection RiotAPIException: " + ex.getMessage());
                    }

                    objChannel.sendMessage("Mastery Level: " + currentChampion.getChampionLevel()).queue();
                    objChannel.sendMessage("Mastery Points: " + currentChampion.getChampionPoints()).queue();
                    //objChannel.sendMessage(" ").queue();
                });
            } catch (RiotApiException ex) {
                System.err.println("Connection RiotAPIException: " + ex.getMessage());
            }
        }

        // If user gives !profile command, grab all info and lay out in Discord's embed format using JDA methods
        if (userInputs[0].equals("!profile")) {
            EmbedBuilder profileBox = new EmbedBuilder(); // Use JDA method to use EmbedBuilder
            profileBox.setTitle("Here is a summary of your LoL stats:"); // Set Title of EmbedBuilder

            StringBuilder championMasteryField = new StringBuilder(); // Initialize StringBuilder to save memory

            try {
                Summoner summoner = getSummonerInfo(userInputs[1]); // Get summoner info and place into object
                profileBox.setAuthor("Profile: " + summoner.getName()); // Set top line to summoner name from object

                // Get Mastery List and place into object
                List<ChampionMastery> masteryList = getMasteryBySummonerId(summoner.getId());

                // For loop 3 times for the top 3 champion mastery info
                IntStream.range(0, 3).forEachOrdered(n -> {
                    try {
                        // Set current champion to current position
                        ChampionMastery currentChampion = masteryList.get(n);
                        // Build string
                        championMasteryField.append("[").append(currentChampion.getChampionLevel()).append("] ")
                                .append(n+1).append(". ").append(getChampionData(currentChampion.getChampionId()).
                                getName()).append(": ").append(currentChampion.getChampionPoints()).append("\n");
                    } catch (RiotApiException ex) {
                        System.err.println("Connection RiotAPIException: " + ex.getMessage());
                    }
                });
            } catch (RiotApiException ex) {
                System.err.println("Connection RiotAPIException: " + ex.getMessage());
            }

            // Turn built string into an actual string and add to Field for Embed
            profileBox.addField("Top Champions:", championMasteryField.toString(), true);

            // Build the Embed box and send message
            objChannel.sendMessage(profileBox.build()).queue();
            }
        }

    // Get summoner info for a given summoner name
    private Summoner getSummonerInfo(String s) throws RiotApiException {
        return api.getSummonerByName(Platform.NA,s);
    }

    // Get full list of champion mastery for a given summoner id
    private List<ChampionMastery> getMasteryBySummonerId(long l) throws RiotApiException {
        return api.getChampionMasteriesBySummoner(Platform.NA,l);
    }

    // Get full list of champion mastery for a given summoner name
    private List<ChampionMastery> getMasteryBySummonerName(String s) throws RiotApiException {
        return api.getChampionMasteriesBySummoner(Platform.NA,getSummonerInfo(s).getId());
    }

    // Get static champion data by champion ID - try/catch statement is inside method because why not
    private Champion getChampionData(int n) throws RiotApiException {
        return api.getDataChampion(Platform.NA,n);
    }
}
;