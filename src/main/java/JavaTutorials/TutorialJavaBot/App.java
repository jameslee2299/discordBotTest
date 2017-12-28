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
                Summoner userName = getSummonerInfo(userInputs[1]);
                objChannel.sendMessage("Name: " + userName.getName()).queue();
                objChannel.sendMessage("Summoner ID: " + Long.toString(userName.getId())).queue();
                objChannel.sendMessage("Account ID: " + Long.toString(userName.getAccountId())).queue();
                objChannel.sendMessage("Summoner Level: " + Long.toString(userName.getSummonerLevel())).queue();
                objChannel.sendMessage("Profile Icon ID: " + Long.toString(userName.getProfileIconId())).queue();
            } catch (RiotApiException ex) {
                System.err.println("Connection RiotAPIException: " + ex.getMessage());
            }
        }

        // If user gives !mstats command, grab the List of champion mastery for the username and send the first three
        // champion mastery info (Riot automatically orders by descending)
        if (userInputs[0].equals("!mstats")) {
            try {
                List<ChampionMastery> userName = getMasteryBySummonerName(userInputs[1]);
                IntStream.range(0, 3).forEachOrdered(n -> {
                    ChampionMastery currentChampion = userName.get(n);
                    objChannel.sendMessage("Rank: " + (n + 1)).queue();
                    objChannel.sendMessage("Champion Name: " +
                            getChampionData(currentChampion.getChampionId()).getName()).queue();
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
            EmbedBuilder profileBox = new EmbedBuilder();
            profileBox.setAuthor("Profile: " + userInputs[1]);
            profileBox.setTitle("Here is a summary of your LoL stats:");

            StringBuilder championMasteryField = new StringBuilder();

            try {
                List<ChampionMastery> userName = getMasteryBySummonerName(userInputs[1]);
                IntStream.range(0, 3).forEachOrdered(n -> {
                    ChampionMastery currentChampion = userName.get(n);
                    championMasteryField.append("[").append(currentChampion.getChampionLevel()).append("] ")
                    .append(n+1).append(". ").append(getChampionData(currentChampion.getChampionId()).getName())
                    .append(": ").append(currentChampion.getChampionPoints()).append("\n");
                });
            } catch (RiotApiException ex) {
                System.err.println("Connection RiotAPIException: " + ex.getMessage());
            }

            profileBox.addField("Top Champions:", championMasteryField.toString(), true);

            objChannel.sendMessage(profileBox.build()).queue();
            }
        }

    // Get summoner info for a given summoner name
    private Summoner getSummonerInfo(String s) throws RiotApiException {
        ApiConfig config = new ApiConfig().setKey(riotApiKey);
        RiotApi api = new RiotApi(config);

        return api.getSummonerByName(Platform.NA,s);
        //System.out.println("Name: " + summoner.getName());
        //System.out.println("Summoner ID: " + summoner.getId());
        //System.out.println("Account ID: " + summoner.getAccountId());
        //System.out.println("Summoner Level: " + summoner.getSummonerLevel());
        //System.out.println("Profile Icon ID: " + summoner.getProfileIconId());

    }

    // Get full list of champion mastery for a given summoner id
    private List<ChampionMastery> getMasteryBySummonerId(long l) throws RiotApiException {
        ApiConfig config = new ApiConfig().setKey(riotApiKey);
        RiotApi api = new RiotApi(config);

        return api.getChampionMasteriesBySummoner(Platform.NA,l);
    }

    // Get full list of champion mastery for a given summoner name
    private List<ChampionMastery> getMasteryBySummonerName(String s) throws RiotApiException {
        ApiConfig config = new ApiConfig().setKey(riotApiKey);
        RiotApi api = new RiotApi(config);

        return api.getChampionMasteriesBySummoner(Platform.NA,getSummonerInfo(s).getId());
    }

    // Get static champion data by champion ID - try/catch statement is inside method because why not
    private Champion getChampionData(int n) {
        try {
            ApiConfig config = new ApiConfig().setKey(riotApiKey);
            RiotApi api = new RiotApi(config);
            return api.getDataChampion(Platform.NA,n);
        }
        catch (RiotApiException ex) {
            System.err.println("Connection RiotAPIException: " + ex.getMessage());
            return null;
        }
    }
}
;