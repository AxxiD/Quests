package me.blackvein.quests;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import me.blackvein.quests.prompts.RequirementsPrompt;
import me.blackvein.quests.prompts.RewardsPrompt;
import me.blackvein.quests.prompts.StagesPrompt;
import me.blackvein.quests.util.ItemUtil;
import me.blackvein.quests.util.Lang;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.conversations.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class QuestFactory implements ConversationAbandonedListener, ColorUtil {

    public Quests quests;
    Map<Player, Quest> editSessions = new HashMap<Player, Quest>();
    Map<Player, Block> selectedBlockStarts = new HashMap<Player, Block>();
    public Map<Player, Block> selectedKillLocations = new HashMap<Player, Block>();
    public Map<Player, Block> selectedReachLocations = new HashMap<Player, Block>();
    public List<String> names = new LinkedList<String>();
    ConversationFactory convoCreator;
    File questsFile;

    @SuppressWarnings("LeakingThisInConstructor")
    public QuestFactory(Quests plugin) {

        quests = plugin;
        questsFile = new File(plugin.getDataFolder(), "quests.yml");

        //Ensure to initialize convoCreator last, to ensure that 'this' is fully initialized before it is passed
        this.convoCreator = new ConversationFactory(plugin)
                .withModality(false)
                .withLocalEcho(false)
                .withFirstPrompt(new MenuPrompt())
                .withTimeout(3600)
                .thatExcludesNonPlayersWithMessage("Console may not perform this operation!")
                .addConversationAbandonedListener(this);

    }

    @Override
    public void conversationAbandoned(ConversationAbandonedEvent abandonedEvent) {

        if (abandonedEvent.getContext().getSessionData("questName") != null) {
            names.remove((String) abandonedEvent.getContext().getSessionData("questName"));
        }

        Player player = (Player) abandonedEvent.getContext().getForWhom();
        selectedBlockStarts.remove(player);
        selectedKillLocations.remove(player);
        selectedReachLocations.remove(player);

    }

    private class MenuPrompt extends FixedSetPrompt {

        public MenuPrompt() {

            super("1", "2", "3", "4");

        }

        @Override
        public String getPromptText(ConversationContext context) {

            String text =
                    GOLD + "- Quest Editor -\n"
                    + BLUE + "" + BOLD + "1" + RESET + YELLOW + " - Create a Quest\n"
                    + BLUE + "" + BOLD + "2" + RESET + YELLOW + " - Edit a Quest\n"
                    + BLUE + "" + BOLD + "3" + RESET + YELLOW + " - Delete a Quest\n"
                    + GOLD + "" + BOLD + "4" + RESET + YELLOW + " - Exit";


            return text;

        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, String input) {

            final Player player = (Player) context.getForWhom();

            if (input.equalsIgnoreCase("1")) {
                
                if (player.hasPermission("quests.editor.create")) {
                    return new QuestNamePrompt();
                } else {
                    player.sendMessage(RED + "You do not have permission to create Quests.");
                    return new MenuPrompt();
                }
                
            } else if (input.equalsIgnoreCase("2")) {
                
                if (player.hasPermission("quests.editor.edit")) {
                    return new SelectEditPrompt();
                } else {
                    player.sendMessage(RED + "You do not have permission to edit Quests.");
                    return new MenuPrompt();
                }
                
            } else if (input.equalsIgnoreCase("3")) {
                
                if (player.hasPermission("quests.editor.delete")) {
                    return new SelectDeletePrompt();
                } else {
                    player.sendMessage(RED + "You do not have permission to delete Quests.");
                    return new MenuPrompt();
                }
                
            } else if (input.equalsIgnoreCase("4")) {
                context.getForWhom().sendRawMessage(YELLOW + "Exited.");
                return Prompt.END_OF_CONVERSATION;
            }

            return null;

        }
    }

    public Prompt returnToMenu() {

        return new CreateMenuPrompt();

    }

    private class CreateMenuPrompt extends FixedSetPrompt {

        public CreateMenuPrompt() {

            super("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");

        }

        @Override
        public String getPromptText(ConversationContext context) {

            String text =
                    GOLD + "- Quest: " + AQUA + context.getSessionData("questName") + GOLD + " -\n";

            text += BLUE + "" + BOLD + "1" + RESET + YELLOW + " - Set name\n";

            if (context.getSessionData("askMessage") == null) {
                text += BLUE + "" + BOLD + "2" + RESET + RED + " - Set ask message " + DARKRED + "(Required, none set)\n";
            } else {
                text += BLUE + "" + BOLD + "2" + RESET + YELLOW + " - Set ask message (\"" + context.getSessionData("askMessage") + "\")\n";
            }


            if (context.getSessionData("finishMessage") == null) {
                text += BLUE + "" + BOLD + "3" + RESET + RED + " - Set finish message " + DARKRED + "(Required, none set)\n";
            } else {
                text += BLUE + "" + BOLD + "3" + RESET + YELLOW + " - Set finish message (\"" + context.getSessionData("finishMessage") + "\")\n";
            }


            if (context.getSessionData("redoDelay") == null) {
                text += BLUE + "" + BOLD + "4" + RESET + YELLOW + " - Set redo delay (None set)\n";
            } else {
                
                //something here is throwing an exception
                try{
                    text += BLUE + "" + BOLD + "4" + RESET + YELLOW + " - Set redo delay (" + Quests.getTime((Long) context.getSessionData("redoDelay")) + ")\n";
                }catch(Exception e){
                    e.printStackTrace();
                }
                    
                //
            }

            if (context.getSessionData("npcStart") == null && quests.citizens != null) {
                text += BLUE + "" + BOLD + "5" + RESET + YELLOW + " - Set NPC start (None set)\n";
            } else if (quests.citizens != null) {
                text += BLUE + "" + BOLD + "5" + RESET + YELLOW + " - Set NPC start (" + quests.citizens.getNPCRegistry().getById((Integer) context.getSessionData("npcStart")).getName() + ")\n";
            }

            if (context.getSessionData("blockStart") == null) {

                if (quests.citizens != null) {
                    text += BLUE + "" + BOLD + "6" + RESET + YELLOW + " - Set Block start (None set)\n";
                } else {
                    text += BLUE + "" + BOLD + "5" + RESET + YELLOW + " - Set Block start (None set)\n";
                }

            } else {

                if (quests.citizens != null) {
                    Location l = (Location) context.getSessionData("blockStart");
                    text += BLUE + "" + BOLD + "6" + RESET + YELLOW + " - Set Block start (" + l.getWorld().getName() + ", " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")\n";
                } else {
                    Location l = (Location) context.getSessionData("blockStart");
                    text += BLUE + "" + BOLD + "5" + RESET + YELLOW + " - Set Block start (" + l.getWorld().getName() + ", " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")\n";
                }

            }


            if (context.getSessionData("initialEvent") == null) {

                if (quests.citizens != null) {
                    text += BLUE + "" + BOLD + "7" + RESET + YELLOW + " - Set initial Event (None set)\n";
                } else {
                    text += BLUE + "" + BOLD + "6" + RESET + YELLOW + " - Set initial Event (None set)\n";
                }

            } else {

                if (quests.citizens != null) {
                    String s = (String) context.getSessionData("initialEvent");
                    text += BLUE + "" + BOLD + "7" + RESET + YELLOW + " - Set initial Event (" + s + ")\n";
                } else {
                    String s = (String) context.getSessionData("initialEvent");
                    text += BLUE + "" + BOLD + "6" + RESET + YELLOW + " - Set initial Event (" + s + ")\n";
                }

            }

            if (quests.citizens != null) {
                text += BLUE + "" + BOLD + "8" + RESET + DARKAQUA + " - Edit Requirements\n";
            } else {
                text += BLUE + "" + BOLD + "7" + RESET + DARKAQUA + " - Edit Requirements\n";
            }


            if (quests.citizens != null) {
                text += BLUE + "" + BOLD + "9" + RESET + PINK + " - Edit Stages\n";
            } else {
                text += BLUE + "" + BOLD + "8" + RESET + PINK + " - Edit Stages\n";
            }

            if (quests.citizens != null) {
                text += BLUE + "" + BOLD + "10" + RESET + GREEN + " - Edit Rewards\n";
            } else {
                text += BLUE + "" + BOLD + "9" + RESET + GREEN + " - Edit Rewards\n";
            }


            if (quests.citizens != null) {
                text += BLUE + "" + BOLD + "11" + RESET + GOLD + " - Save\n";
            } else {
                text += BLUE + "" + BOLD + "10" + RESET + GOLD + " - Save\n";
            }

            if (quests.citizens != null) {
                text += BLUE + "" + BOLD + "12" + RESET + RED + " - Exit\n";
            } else {
                text += BLUE + "" + BOLD + "11" + RESET + RED + " - Exit\n";
            }


            return text;

        }

        @Override
        public Prompt acceptValidatedInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase("1")) {

                return new SetNamePrompt();

            } else if (input.equalsIgnoreCase("2")) {

                return new AskMessagePrompt();

            } else if (input.equalsIgnoreCase("3")) {

                return new FinishMessagePrompt();

            } else if (input.equalsIgnoreCase("4")) {

                return new RedoDelayPrompt();

            } else if (input.equalsIgnoreCase("5")) {

                if (quests.citizens != null) {
                    return new SetNpcStartPrompt();
                } else {
                    selectedBlockStarts.put((Player) context.getForWhom(), null);
                    return new BlockStartPrompt();
                }

            } else if (input.equalsIgnoreCase("6")) {

                if (quests.citizens != null) {
                    selectedBlockStarts.put((Player) context.getForWhom(), null);
                    return new BlockStartPrompt();
                } else {
                    return new RequirementsPrompt(quests, QuestFactory.this);
                }

            } else if (input.equalsIgnoreCase("6")) {

                if (quests.citizens != null) {
                    selectedBlockStarts.put((Player) context.getForWhom(), null);
                    return new BlockStartPrompt();
                } else {
                    return new InitialEventPrompt();
                }

            } else if (input.equalsIgnoreCase("7")) {

                if (quests.citizens != null) {
                    return new InitialEventPrompt();
                } else {
                    return new RequirementsPrompt(quests, QuestFactory.this);
                }

            } else if (input.equalsIgnoreCase("8")) {

                if (quests.citizens != null) {
                    return new RequirementsPrompt(quests, QuestFactory.this);
                } else {
                    return new StagesPrompt(QuestFactory.this);
                }

            } else if (input.equalsIgnoreCase("9")) {

                if (quests.citizens != null) {
                    return new StagesPrompt(QuestFactory.this);
                } else {
                    return new RewardsPrompt(quests, QuestFactory.this);
                }

            } else if (input.equalsIgnoreCase("10")) {

                if (quests.citizens != null) {
                    return new RewardsPrompt(quests, QuestFactory.this);
                } else {
                    return new SavePrompt();
                }

            } else if (input.equalsIgnoreCase("11")) {

                if (quests.citizens != null) {
                    return new SavePrompt();
                } else {
                    return new ExitPrompt();
                }

            } else if (input.equalsIgnoreCase("12")) {

                if (quests.citizens != null) {
                    return new ExitPrompt();
                } else {
                    return new CreateMenuPrompt();
                }

            }

            return null;

        }
    }

    private class SelectEditPrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            String s = GOLD + "- Edit Quest -\n";
            for (Quest q : quests.getQuests()) {
                s += GRAY + "- " + YELLOW + q.getName() + "\n";
            }

            return s + GOLD + "Enter a Quest to edit, or \"cancel\" to return.";

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase("cancel") == false) {

                for (Quest q : quests.getQuests()) {

                    if (q.getName().equalsIgnoreCase(input) || q.getName().toLowerCase().contains(input.toLowerCase())) {
                        loadQuest(context, q);
                        return new CreateMenuPrompt();
                    }

                }

                return new SelectEditPrompt();

            } else {
                return new MenuPrompt();
            }

        }
    }

    private class QuestNamePrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            String text = GOLD + "- Create Quest -\n";
            text += AQUA + "Create new Quest " + GOLD + "- Enter a name for the Quest (Or enter \'cancel\' to return)";

            return text;

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase("cancel") == false) {

                for (Quest q : quests.quests) {

                    if (q.name.equalsIgnoreCase(input)) {

                        context.getForWhom().sendRawMessage(ChatColor.RED + "Quest already exists!");
                        return new QuestNamePrompt();

                    }

                }

                if (names.contains(input)) {

                    context.getForWhom().sendRawMessage(ChatColor.RED + "Someone is creating a Quest with that name!");
                    return new QuestNamePrompt();

                }

                if (input.contains(",")) {

                    context.getForWhom().sendRawMessage(ChatColor.RED + "Name may not contain commas!");
                    return new QuestNamePrompt();

                }

                context.setSessionData("questName", input);
                names.add(input);
                return new CreateMenuPrompt();

            } else {

                return new MenuPrompt();

            }

        }
    }

    private class SetNpcStartPrompt extends NumericPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            return ChatColor.YELLOW + "Enter NPC ID, or -1 to clear the NPC start, or -2 to cancel";

        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, Number input) {

            if (input.intValue() > -1) {

                if (quests.citizens.getNPCRegistry().getById(input.intValue()) == null) {
                    context.getForWhom().sendRawMessage(ChatColor.RED + "No NPC exists with that id!");
                    return new SetNpcStartPrompt();
                }

                context.setSessionData("npcStart", input.intValue());
                return new CreateMenuPrompt();

            } else if (input.intValue() == -1) {
                context.setSessionData("npcStart", null);
                return new CreateMenuPrompt();
            } else if (input.intValue() == -2) {
                return new CreateMenuPrompt();
            } else {
                context.getForWhom().sendRawMessage(ChatColor.RED + "No NPC exists with that id!");
                return new SetNpcStartPrompt();
            }

        }
    }

    private class BlockStartPrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            return ChatColor.YELLOW + "Right-click on a block to use as a start point, then enter \"done\" to save,\n"
                    + "or enter \"clear\" to clear the block start, or \"cancel\" to return";

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            Player player = (Player) context.getForWhom();
            if (input.equalsIgnoreCase("done") || input.equalsIgnoreCase("cancel")) {

                if (input.equalsIgnoreCase("done")) {

                    Block block = selectedBlockStarts.get(player);
                    if (block != null) {
                        Location loc = block.getLocation();
                        context.setSessionData("blockStart", loc);
                        selectedBlockStarts.remove(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "You must select a block first.");
                        return new BlockStartPrompt();
                    }

                } else {
                    selectedBlockStarts.remove(player);
                }


                return new CreateMenuPrompt();

            } else if (input.equalsIgnoreCase("clear")) {

                selectedBlockStarts.remove(player);
                context.setSessionData("blockStart", null);
                return new CreateMenuPrompt();

            }

            return new BlockStartPrompt();

        }
    }

    private class SetNamePrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            return ChatColor.YELLOW + "Enter Quest name (or \'cancel\' to return)";

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase("cancel") == false) {

                for (Quest q : quests.quests) {

                    if (q.name.equalsIgnoreCase(input)) {
                        String s = null;
                        if (context.getSessionData("edit") != null) {
                            s = (String) context.getSessionData("edit");
                        }

                        if (s != null && s.equalsIgnoreCase(input) == false) {
                            context.getForWhom().sendRawMessage(RED + "A Quest with that name already exists!");
                            return new SetNamePrompt();
                        }
                    }

                }

                if (names.contains(input)) {
                    context.getForWhom().sendRawMessage(RED + "Someone is creating/editing a Quest with that name!");
                    return new SetNamePrompt();
                }

                if (input.contains(",")) {

                    context.getForWhom().sendRawMessage(ChatColor.RED + "Name may not contain commas!");
                    return new QuestNamePrompt();

                }

                names.remove((String) context.getSessionData("questName"));
                context.setSessionData("questName", input);
                names.add(input);

            }

            return new CreateMenuPrompt();

        }
    }

    private class AskMessagePrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            return ChatColor.YELLOW + "Enter ask message (or \'cancel\' to return)";

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase("cancel") == false) {
                context.setSessionData("askMessage", input);
            }

            return new CreateMenuPrompt();

        }
    }

    private class FinishMessagePrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            return ChatColor.YELLOW + "Enter finish message (or \'cancel\' to return)";

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase("cancel") == false) {
                context.setSessionData("finishMessage", input);
            }

            return new CreateMenuPrompt();

        }
    }

    private class InitialEventPrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            String text = DARKGREEN + "- Events -\n";
            if (quests.events.isEmpty()) {
                text += RED + "- None";
            } else {
                for (Event e : quests.events) {
                    text += GREEN + "- " + e.getName() + "\n";
                }
            }

            return text + YELLOW + "Enter an Event name, or enter \"clear\" to clear the initial Event, or \"cancel\" to return";

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            Player player = (Player) context.getForWhom();

            if (input.equalsIgnoreCase("cancel") == false && input.equalsIgnoreCase("clear") == false) {

                Event found = null;

                for (Event e : quests.events) {

                    if (e.getName().equalsIgnoreCase(input)) {
                        found = e;
                        break;
                    }

                }

                if (found == null) {
                    player.sendMessage(RED + input + YELLOW + " is not a valid event name!");
                    return new InitialEventPrompt();
                } else {
                    context.setSessionData("initialEvent", found.getName());
                    return new CreateMenuPrompt();
                }

            } else if (input.equalsIgnoreCase("clear")) {
                context.setSessionData("initialEvent", null);
                player.sendMessage(YELLOW + "Initial Event cleared.");
                return new CreateMenuPrompt();
            } else {
                return new CreateMenuPrompt();
            }

        }
    }

    private class RedoDelayPrompt extends NumericPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            return ChatColor.YELLOW + "Enter amount of time (in milliseconds), or 0 to clear the redo delay, or -1 to cancel";

        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, Number input) {

            if (input.longValue() < -1) {
                context.getForWhom().sendRawMessage(ChatColor.RED + "Amount must be a positive number.");
            } else if (input.longValue() == 0) {
                context.setSessionData("redoDelay", null);
            } else if (input.longValue() != -1) {
                context.setSessionData("redoDelay", input.longValue());
            }

            return new CreateMenuPrompt();

        }
    }

    private class SavePrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            String text = GREEN
                    + "1 - Yes\n"
                    + "2 - No";
            return ChatColor.YELLOW + "Finish and save \"" + AQUA + context.getSessionData("questName") + YELLOW + "\"?\n" + text;

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase("1") || input.equalsIgnoreCase("Yes")) {

                if (context.getSessionData("askMessage") == null) {
                    context.getForWhom().sendRawMessage(RED + "You must set an ask message!");
                    return new CreateMenuPrompt();
                } else if (context.getSessionData("finishMessage") == null) {
                    context.getForWhom().sendRawMessage(RED + "You must set a finish message!");
                    return new CreateMenuPrompt();
                } else if (StagesPrompt.getStages(context) == 0) {
                    context.getForWhom().sendRawMessage(RED + "Your Quest has no Stages!");
                    return new CreateMenuPrompt();
                }

                FileConfiguration data = new YamlConfiguration();
                try {
                    data.load(new File(quests.getDataFolder(), "quests.yml"));
                    ConfigurationSection questSection = data.getConfigurationSection("quests");

                    int customNum = 1;
                    while (true) {

                        if (questSection.contains("custom" + customNum)) {
                            customNum++;
                        } else {
                            break;
                        }

                    }

                    ConfigurationSection newSection = questSection.createSection("custom" + customNum);
                    saveQuest(context, newSection);
                    data.save(new File(quests.getDataFolder(), "quests.yml"));
                    context.getForWhom().sendRawMessage(BOLD + "Quest saved! (You will need to perform a Quest reload for it to appear)");

                } catch (Exception e) {
                    e.printStackTrace();
                }

                return Prompt.END_OF_CONVERSATION;

            } else if (input.equalsIgnoreCase("2") || input.equalsIgnoreCase("No")) {
                return new CreateMenuPrompt();
            } else {
                return new SavePrompt();
            }

        }
    }

    private class ExitPrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            String text = GREEN
                    + "1 - Yes\n"
                    + "2 - No";
            return ChatColor.YELLOW + "Are you sure you want to exit without saving?\n" + text;

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase("1") || input.equalsIgnoreCase("Yes")) {

                context.getForWhom().sendRawMessage(BOLD + "" + YELLOW + "Exited.");
                return Prompt.END_OF_CONVERSATION;

            } else if (input.equalsIgnoreCase("2") || input.equalsIgnoreCase("No")) {
                return new CreateMenuPrompt();
            } else {
                return new ExitPrompt();
            }

        }
    }

    public static void saveQuest(ConversationContext cc, ConfigurationSection cs) {

        String edit = null;
        if (cc.getSessionData("edit") != null) {
            edit = (String) cc.getSessionData("edit");
        }

        if (edit != null) {

            ConfigurationSection questList = cs.getParent();

            for (String key : questList.getKeys(false)) {

                String name = questList.getString(key + ".name");
                if (name.equalsIgnoreCase(edit)) {

                    questList.set(key, null);
                    break;

                }

            }

        }

        String name = (String) cc.getSessionData("questName");
        String desc = (String) cc.getSessionData("askMessage");
        String finish = (String) cc.getSessionData("finishMessage");
        Long redo = null;
        Integer npcStart = null;
        String blockStart = null;
        String initialEvent = null;

        Integer moneyReq = null;
        Integer questPointsReq = null;
        LinkedList<ItemStack> itemReqs = null;
        LinkedList<Boolean> removeItemReqs = null;
        LinkedList<String> permReqs = null;
        LinkedList<String> questReqs = null;
        String failMessage = null;

        Integer moneyRew = null;
        Integer questPointsRew = null;
        LinkedList<String> itemRews = new LinkedList<String>();
        Integer expRew = null;
        LinkedList<String> commandRews = null;
        LinkedList<String> permRews = null;
        LinkedList<String> mcMMOSkillRews = null;
        LinkedList<Integer> mcMMOSkillAmounts = null;



        if (cc.getSessionData("redoDelay") != null) {
            redo = (Long) cc.getSessionData("redoDelay");
        }

        if (cc.getSessionData("npcStart") != null) {
            npcStart = (Integer) cc.getSessionData("npcStart");
        }

        if (cc.getSessionData("blockStart") != null) {
            blockStart = Quests.getLocationInfo((Location) cc.getSessionData("blockStart"));
        }



        if (cc.getSessionData("moneyReq") != null) {
            moneyReq = (Integer) cc.getSessionData("moneyReq");
        }

        if (cc.getSessionData("questPointsReq") != null) {
            questPointsReq = (Integer) cc.getSessionData("questPointsReq");
        }

        if (cc.getSessionData("itemReqs") != null) {
            itemReqs = (LinkedList<ItemStack>) cc.getSessionData("itemReqs");
            removeItemReqs = (LinkedList<Boolean>) cc.getSessionData("removeItemReqs");
        }

        if (cc.getSessionData("permissionReqs") != null) {
            permReqs = (LinkedList<String>) cc.getSessionData("permissionReqs");
        }

        if (cc.getSessionData("questReqs") != null) {
            questReqs = (LinkedList<String>) cc.getSessionData("questReqs");
        }

        if (cc.getSessionData("failMessage") != null) {
            failMessage = (String) cc.getSessionData("failMessage");
        }

        if (cc.getSessionData("initialEvent") != null) {
            initialEvent = (String) cc.getSessionData("initialEvent");
        }

        if (cc.getSessionData("moneyRew") != null) {
            moneyRew = (Integer) cc.getSessionData("moneyRew");
        }

        if (cc.getSessionData("questPointsRew") != null) {
            questPointsRew = (Integer) cc.getSessionData("questPointsRew");
        }

        if (cc.getSessionData("itemRews") != null) {
            for (ItemStack is : (LinkedList<ItemStack>) cc.getSessionData("itemRews")) {
                itemRews.add(ItemUtil.serialize(is));
            }
        }

        if (cc.getSessionData("expRew") != null) {
            expRew = (Integer) cc.getSessionData("expRew");
        }

        if (cc.getSessionData("commandRews") != null) {
            commandRews = (LinkedList<String>) cc.getSessionData("commandRews");
        }

        if (cc.getSessionData("permissionRews") != null) {
            permRews = (LinkedList<String>) cc.getSessionData("permissionRews");
        }

        if (cc.getSessionData("mcMMOSkillRews") != null) {
            mcMMOSkillRews = (LinkedList<String>) cc.getSessionData("mcMMOSkillRews");
            mcMMOSkillAmounts = (LinkedList<Integer>) cc.getSessionData("mcMMOSkillAmounts");
        }



        cs.set("name", name);
        cs.set("npc-giver-id", npcStart);
        cs.set("block-start", blockStart);
        cs.set("redo-delay", redo);
        cs.set("ask-message", desc);
        cs.set("finish-message", finish);
        cs.set("event", initialEvent);


        if (moneyReq != null || questPointsReq != null || itemReqs != null && itemReqs.isEmpty() == false || permReqs != null && permReqs.isEmpty() == false || questReqs != null && questReqs.isEmpty() == false) {

            ConfigurationSection reqs = cs.createSection("requirements");
            List<String> items = new LinkedList<String>();
            if(itemReqs != null){
                
                for (ItemStack is : itemReqs) {
                    items.add(ItemUtil.serialize(is));
                }
            
            }
            reqs.set("items", items);
            reqs.set("remove-items", removeItemReqs);
            reqs.set("money", moneyReq);
            reqs.set("quest-points", questPointsReq);
            reqs.set("permissions", permReqs);
            reqs.set("quests", questReqs);
            reqs.set("fail-requirement-message", failMessage);

        } else {
            cs.set("requirements", null);
        }


        ConfigurationSection stages = cs.createSection("stages");
        ConfigurationSection ordered = stages.createSection("ordered");

        String pref;

        LinkedList<Integer> breakIds;
        LinkedList<Integer> breakAmounts;

        LinkedList<Integer> damageIds;
        LinkedList<Integer> damageAmounts;

        LinkedList<Integer> placeIds;
        LinkedList<Integer> placeAmounts;

        LinkedList<Integer> useIds;
        LinkedList<Integer> useAmounts;

        LinkedList<Integer> cutIds;
        LinkedList<Integer> cutAmounts;

        Integer fish;
        Integer players;

        LinkedList<String> enchantments;
        LinkedList<Integer> enchantmentIds;
        LinkedList<Integer> enchantmentAmounts;

        LinkedList<ItemStack> deliveryItems;
        LinkedList<Integer> deliveryNPCIds;
        LinkedList<String> deliveryMessages;

        LinkedList<Integer> npcTalkIds;

        LinkedList<Integer> npcKillIds;
        LinkedList<Integer> npcKillAmounts;

        LinkedList<String> bossIds;
        LinkedList<Integer> bossAmounts;

        LinkedList<String> mobs;
        LinkedList<Integer> mobAmounts;
        LinkedList<String> mobLocs;
        LinkedList<Integer> mobRadii;
        LinkedList<String> mobLocNames;

        LinkedList<String> reachLocs;
        LinkedList<Integer> reachRadii;
        LinkedList<String> reachNames;

        LinkedList<String> tames;
        LinkedList<Integer> tameAmounts;

        LinkedList<String> shearColors;
        LinkedList<Integer> shearAmounts;

        String script;
        String event;
        Long delay;
        String delayMessage;

        for (int i = 1; i <= StagesPrompt.getStages(cc); i++) {

            pref = "stage" + i;
            ConfigurationSection stage = ordered.createSection("" + i);

            breakIds = null;
            breakAmounts = null;

            damageIds = null;
            damageAmounts = null;

            placeIds = null;
            placeAmounts = null;

            useIds = null;
            useAmounts = null;

            cutIds = null;
            cutAmounts = null;

            fish = null;
            players = null;

            enchantments = null;
            enchantmentIds = null;
            enchantmentAmounts = null;

            deliveryItems = null;
            deliveryNPCIds = null;
            deliveryMessages = null;

            npcTalkIds = null;

            npcKillIds = null;
            npcKillAmounts = null;

            bossIds = null;
            bossAmounts = null;

            mobs = null;
            mobAmounts = null;
            mobLocs = null;
            mobRadii = null;
            mobLocNames = null;

            reachLocs = null;
            reachRadii = null;
            reachNames = null;

            tames = null;
            tameAmounts = null;

            shearColors = null;
            shearAmounts = null;

            script = null;
            event = null;
            delay = null;
            delayMessage = null;



            if (cc.getSessionData(pref + "breakIds") != null) {
                breakIds = (LinkedList<Integer>) cc.getSessionData(pref + "breakIds");
                breakAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "breakAmounts");
            }

            if (cc.getSessionData(pref + "damageIds") != null) {
                damageIds = (LinkedList<Integer>) cc.getSessionData(pref + "damageIds");
                damageAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "damageAmounts");
            }

            if (cc.getSessionData(pref + "placeIds") != null) {
                placeIds = (LinkedList<Integer>) cc.getSessionData(pref + "placeIds");
                placeAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "placeAmounts");
            }

            if (cc.getSessionData(pref + "useIds") != null) {
                useIds = (LinkedList<Integer>) cc.getSessionData(pref + "useIds");
                useAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "useAmounts");
            }

            if (cc.getSessionData(pref + "cutIds") != null) {
                cutIds = (LinkedList<Integer>) cc.getSessionData(pref + "cutIds");
                cutAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "cutAmounts");
            }

            if (cc.getSessionData(pref + "fish") != null) {
                fish = (Integer) cc.getSessionData(pref + "fish");
            }

            if (cc.getSessionData(pref + "playerKill") != null) {
                players = (Integer) cc.getSessionData(pref + "playerKill");
            }

            if (cc.getSessionData(pref + "enchantTypes") != null) {
                enchantments = (LinkedList<String>) cc.getSessionData(pref + "enchantTypes");
                enchantmentIds = (LinkedList<Integer>) cc.getSessionData(pref + "enchantIds");
                enchantmentAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "enchantAmounts");
            }

            if (cc.getSessionData(pref + "deliveryItems") != null) {
                deliveryItems = (LinkedList<ItemStack>) cc.getSessionData(pref + "deliveryItems");
                deliveryNPCIds = (LinkedList<Integer>) cc.getSessionData(pref + "deliveryNPCs");
                deliveryMessages = (LinkedList<String>) cc.getSessionData(pref + "deliveryMessages");
            }

            if (cc.getSessionData(pref + "npcIdsToTalkTo") != null) {
                npcTalkIds = (LinkedList<Integer>) cc.getSessionData(pref + "npcIdsToTalkTo");
            }

            if (cc.getSessionData(pref + "npcIdsToKill") != null) {
                npcKillIds = (LinkedList<Integer>) cc.getSessionData(pref + "npcIdsToKill");
                npcKillAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "npcAmountsToKill");
            }

            if (cc.getSessionData(pref + "bossIds") != null) {
                bossIds = (LinkedList<String>) cc.getSessionData(pref + "bossIds");
                bossAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "bossAmounts");
            }

            if (cc.getSessionData(pref + "mobTypes") != null) {
                mobs = (LinkedList<String>) cc.getSessionData(pref + "mobTypes");
                mobAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "mobAmounts");
                if (cc.getSessionData(pref + "killLocations") != null) {
                    mobLocs = (LinkedList<String>) cc.getSessionData(pref + "killLocations");
                    mobRadii = (LinkedList<Integer>) cc.getSessionData(pref + "killLocationRadii");
                    mobLocNames = (LinkedList<String>) cc.getSessionData(pref + "killLocationNames");
                }
            }

            if (cc.getSessionData(pref + "tameTypes") != null) {
                tames = (LinkedList<String>) cc.getSessionData(pref + "tameTypes");
                tameAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "tameAmounts");
            }

            if (cc.getSessionData(pref + "shearColors") != null) {
                shearColors = (LinkedList<String>) cc.getSessionData(pref + "shearColors");
                shearAmounts = (LinkedList<Integer>) cc.getSessionData(pref + "shearAmounts");
            }

            if (cc.getSessionData(pref + "event") != null) {
                event = (String) cc.getSessionData(pref + "event");
            }

            if (cc.getSessionData(pref + "delay") != null) {
                delay = (Long) cc.getSessionData(pref + "delay");
                delayMessage = (String) cc.getSessionData(pref + "delayMessage");
            }

            if (cc.getSessionData(pref + "denizen") != null) {
                script = (String) cc.getSessionData(pref + "denizen");
            }

            if (breakIds != null && breakIds.isEmpty() == false) {
                stage.set("break-block-ids", breakIds);
                stage.set("break-block-amounts", breakAmounts);
            }

            if (damageIds != null && damageIds.isEmpty() == false) {
                stage.set("damage-block-ids", damageIds);
                stage.set("damage-block-amounts", damageAmounts);
            }

            if (placeIds != null && placeIds.isEmpty() == false) {
                stage.set("place-block-ids", placeIds);
                stage.set("place-block-amounts", placeAmounts);
            }

            if (useIds != null && useIds.isEmpty() == false) {
                stage.set("use-block-ids", useIds);
                stage.set("use-block-amounts", useAmounts);
            }

            if (cutIds != null && cutIds.isEmpty() == false) {
                stage.set("cut-block-ids", cutIds);
                stage.set("cut-block-amounts", cutAmounts);
            }

            stage.set("fish-to-catch", fish);
            stage.set("players-to-kill", players);
            stage.set("enchantments", enchantments);
            stage.set("enchantment-item-ids", enchantmentIds);
            stage.set("enchantment-amounts", enchantmentAmounts);
            if(deliveryItems != null && deliveryItems.isEmpty() == false){
                LinkedList<String> items = new LinkedList<String>();
                for(ItemStack is : deliveryItems)
                    items.add(ItemUtil.serialize(is));
                stage.set("items-to-deliver", items);
            }else{
                stage.set("items-to-deliver", null);
            }
            stage.set("npc-delivery-ids", deliveryNPCIds);
            stage.set("delivery-messages", deliveryMessages);
            stage.set("npc-ids-to-talk-to", npcTalkIds);
            stage.set("npc-ids-to-kill", npcKillIds);
            stage.set("npc-kill-amounts", npcKillAmounts);
            stage.set("boss-ids-to-kill", bossIds);
            stage.set("boss-amounts-to-kill", bossAmounts);
            stage.set("mobs-to-kill", mobs);
            stage.set("mob-amounts", mobAmounts);
            stage.set("locations-to-kill", mobLocs);
            stage.set("kill-location-radii", mobRadii);
            stage.set("kill-location-names", mobLocNames);
            stage.set("locations-to-reach", reachLocs);
            stage.set("reach-location-radii", reachRadii);
            stage.set("reach-location-names", reachNames);
            stage.set("mobs-to-tame", tames);
            stage.set("mob-tame-amounts", tameAmounts);
            stage.set("sheep-to-shear", shearColors);
            stage.set("sheep-amounts", shearAmounts);
            stage.set("script-to-run", script);
            stage.set("event", event);
            stage.set("delay", delay);
            stage.set("delay-message", delayMessage);

        }


        if (moneyRew != null || questPointsRew != null || itemRews != null && itemRews.isEmpty() == false || permRews != null && permRews.isEmpty() == false || expRew != null || commandRews != null && commandRews.isEmpty() == false || mcMMOSkillRews != null) {

            ConfigurationSection rews = cs.createSection("rewards");
            rews.set("items", itemRews);
            rews.set("money", moneyRew);
            rews.set("quest-points", questPointsRew);
            rews.set("exp", expRew);
            rews.set("permissions", permRews);
            rews.set("commands", commandRews);
            rews.set("mcmmo-skills", mcMMOSkillRews);
            rews.set("mcmmo-levels", mcMMOSkillAmounts);

        } else {
            cs.set("rewards", null);
        }

    }

    public static void loadQuest(ConversationContext cc, Quest q) {

        cc.setSessionData("edit", q.name);
        cc.setSessionData("questName", q.name);
        if (q.npcStart != null) {
            cc.setSessionData("npcStart", q.npcStart.getId());
        }
        cc.setSessionData("blockStart", q.blockStart);
        if (q.redoDelay != -1) {
            cc.setSessionData("redoDelay", q.redoDelay);
        }
        cc.setSessionData("askMessage", q.description);
        cc.setSessionData("finishMessage", q.finished);
        if (q.initialEvent != null) {
            cc.setSessionData("initialEvent", q.initialEvent.getName());
        }

        //Requirements
        if (q.moneyReq != 0) {
            cc.setSessionData("moneyReq", q.moneyReq);
        }
        if (q.questPointsReq != 0) {
            cc.setSessionData("questPointsReq", q.questPointsReq);
        }

        if (q.items.isEmpty() == false) {

            cc.setSessionData("itemReqs", q.items);
            cc.setSessionData("removeItemReqs", q.removeItems);

        }

        if (q.neededQuests.isEmpty() == false) {
            cc.setSessionData("questReqs", q.neededQuests);
        }

        if (q.permissionReqs.isEmpty() == false) {
            cc.setSessionData("permissionReqs", q.permissionReqs);
        }

        if (q.failRequirements != null) {
            cc.setSessionData("failMessage", q.failRequirements);
        }
        //

        //Rewards
        if (q.moneyReward != 0) {
            cc.setSessionData("moneyRew", q.moneyReward);
        }

        if (q.questPoints != 0) {
            cc.setSessionData("questPointsRew", q.questPoints);
        }

        if (q.exp != 0) {
            cc.setSessionData("expRew", q.exp);
        }

        if (q.commands.isEmpty() == false) {
            cc.setSessionData("commandRews", q.commands);
        }

        if (q.permissions.isEmpty() == false) {
            cc.setSessionData("permissionRews", q.permissions);
        }

        if (q.mcmmoSkills.isEmpty() == false) {
            cc.setSessionData("mcMMOSkillRews", q.mcmmoSkills);
            cc.setSessionData("mcMMOSkillAmounts", q.mcmmoAmounts);
        }
        //

        //Stages
        for (Stage stage : q.stages) {
            final String pref = "stage" + (q.stages.indexOf(stage) + 1);
            cc.setSessionData(pref, Boolean.TRUE);

            if (stage.blocksToBreak != null) {

                LinkedList<Integer> ids = new LinkedList<Integer>();
                LinkedList<Integer> amnts = new LinkedList<Integer>();

                for (Entry e : stage.blocksToBreak.entrySet()) {

                    ids.add(((Material) e.getKey()).getId());
                    amnts.add((Integer) e.getValue());

                }

                cc.setSessionData(pref + "breakIds", ids);
                cc.setSessionData(pref + "breakAmounts", amnts);

            }


            if (stage.blocksToDamage != null) {

                LinkedList<Integer> ids = new LinkedList<Integer>();
                LinkedList<Integer> amnts = new LinkedList<Integer>();

                for (Entry e : stage.blocksToDamage.entrySet()) {

                    ids.add(((Material) e.getKey()).getId());
                    amnts.add((Integer) e.getValue());

                }

                cc.setSessionData(pref + "damageIds", ids);
                cc.setSessionData(pref + "damageAmounts", amnts);

            }


            if (stage.blocksToPlace != null) {

                LinkedList<Integer> ids = new LinkedList<Integer>();
                LinkedList<Integer> amnts = new LinkedList<Integer>();

                for (Entry e : stage.blocksToPlace.entrySet()) {

                    ids.add(((Material) e.getKey()).getId());
                    amnts.add((Integer) e.getValue());

                }

                cc.setSessionData(pref + "placeIds", ids);
                cc.setSessionData(pref + "placeAmounts", amnts);

            }

            if (stage.blocksToUse != null) {

                LinkedList<Integer> ids = new LinkedList<Integer>();
                LinkedList<Integer> amnts = new LinkedList<Integer>();

                for (Entry e : stage.blocksToUse.entrySet()) {

                    ids.add(((Material) e.getKey()).getId());
                    amnts.add((Integer) e.getValue());

                }

                cc.setSessionData(pref + "useIds", ids);
                cc.setSessionData(pref + "useAmounts", amnts);

            }


            if (stage.blocksToCut != null) {

                LinkedList<Integer> ids = new LinkedList<Integer>();
                LinkedList<Integer> amnts = new LinkedList<Integer>();

                for (Entry e : stage.blocksToCut.entrySet()) {

                    ids.add(((Material) e.getKey()).getId());
                    amnts.add((Integer) e.getValue());

                }

                cc.setSessionData(pref + "cutIds", ids);
                cc.setSessionData(pref + "cutAmounts", amnts);

            }


            if (stage.fishToCatch != null) {
                cc.setSessionData(pref + "fish", stage.fishToCatch);
            }


            if (stage.playersToKill != null) {
                cc.setSessionData(pref + "playerKill", stage.playersToKill);
            }


            if (stage.itemsToEnchant.isEmpty() == false) {

                LinkedList<String> enchants = new LinkedList<String>();
                LinkedList<Integer> ids = new LinkedList<Integer>();
                LinkedList<Integer> amounts = new LinkedList<Integer>();

                for (Entry<Map<Enchantment, Material>, Integer> e : stage.itemsToEnchant.entrySet()) {

                    amounts.add(e.getValue());
                    for (Entry<Enchantment, Material> e2 : e.getKey().entrySet()) {

                        ids.add(e2.getValue().getId());
                        enchants.add(Quester.prettyEnchantmentString(e2.getKey()));

                    }

                }

                cc.setSessionData(pref + "enchantTypes", enchants);
                cc.setSessionData(pref + "enchantIds", ids);
                cc.setSessionData(pref + "enchantAmounts", amounts);

            }


            if (stage.itemsToDeliver.isEmpty() == false) {

                LinkedList<ItemStack> items = new LinkedList<ItemStack>();
                LinkedList<Integer> npcs = new LinkedList<Integer>();

                for (ItemStack is : stage.itemsToDeliver) {
                    items.add(is);
                }

                for (NPC n : stage.itemDeliveryTargets) {
                    npcs.add(n.getId());
                }

                cc.setSessionData(pref + "deliveryItems", items);
                cc.setSessionData(pref + "deliveryNPCs", npcs);
                cc.setSessionData(pref + "deliveryMessages", stage.deliverMessages);

            }


            if (stage.citizensToInteract.isEmpty() == false) {

                LinkedList<Integer> npcs = new LinkedList<Integer>();
                for (NPC n : stage.citizensToInteract) {
                    npcs.add(n.getId());
                }

                cc.setSessionData(pref + "npcIdsToTalkTo", npcs);

            }


            if (stage.citizensToKill.isEmpty() == false) {

                LinkedList<Integer> npcs = new LinkedList<Integer>();
                for (NPC n : stage.citizensToKill) {
                    npcs.add(n.getId());
                }

                cc.setSessionData(pref + "npcIdsToKill", npcs);
                cc.setSessionData(pref + "npcAmountsToKill", stage.citizenNumToKill);

            }


            if (stage.bossesToKill.isEmpty() == false) {

                cc.setSessionData(pref + "bossIds", stage.bossesToKill);
                cc.setSessionData(pref + "bossAmounts", stage.bossAmountsToKill);

            }


            if (stage.mobsToKill.isEmpty() == false) {

                LinkedList<String> mobs = new LinkedList<String>();
                for (EntityType et : stage.mobsToKill) {
                    mobs.add(Quester.prettyMobString(et));
                }

                cc.setSessionData(pref + "mobTypes", mobs);
                cc.setSessionData(pref + "mobAmounts", stage.mobNumToKill);

                if (stage.locationsToKillWithin.isEmpty() == false) {

                    LinkedList<String> locs = new LinkedList<String>();
                    for (Location l : stage.locationsToKillWithin) {
                        locs.add(Quests.getLocationInfo(l));
                    }

                    cc.setSessionData(pref + "killLocations", locs);
                    cc.setSessionData(pref + "killLocationRadii", stage.radiiToKillWithin);
                    cc.setSessionData(pref + "killLocationNames", stage.areaNames);

                }

            }


            if (stage.locationsToReach.isEmpty() == false) {

                LinkedList<String> locs = new LinkedList<String>();
                for (Location l : stage.locationsToReach) {
                    locs.add(Quests.getLocationInfo(l));
                }

                cc.setSessionData(pref + "reachLocations", locs);
                cc.setSessionData(pref + "reachLocationRadii", stage.radiiToReachWithin);
                cc.setSessionData(pref + "reachLocationNames", stage.locationNames);

            }


            if (stage.mobsToTame.isEmpty() == false) {

                LinkedList<String> mobs = new LinkedList<String>();
                LinkedList<Integer> amnts = new LinkedList<Integer>();

                for (Entry<EntityType, Integer> e : stage.mobsToTame.entrySet()) {

                    mobs.add(Quester.prettyMobString(e.getKey()));
                    amnts.add(e.getValue());

                }

                cc.setSessionData(pref + "tameTypes", mobs);
                cc.setSessionData(pref + "tameAmounts", amnts);

            }


            if (stage.sheepToShear.isEmpty() == false) {

                LinkedList<String> colors = new LinkedList<String>();
                LinkedList<Integer> amnts = new LinkedList<Integer>();

                for (Entry<DyeColor, Integer> e : stage.sheepToShear.entrySet()) {
                    colors.add(Quester.prettyColorString(e.getKey()));
                    amnts.add(e.getValue());
                }

                cc.setSessionData(pref + "shearColors", colors);
                cc.setSessionData(pref + "shearAmounts", amnts);

            }


            if (stage.event != null) {
                cc.setSessionData(pref + "event", stage.event.getName());
            }


            if (stage.delay != -1) {
                cc.setSessionData(pref + "delay", stage.delay);
                if (stage.delayMessage != null) {
                    cc.setSessionData(pref + "delayMessage", stage.delayMessage);
                }
            }

            if (stage.script != null) {
                cc.setSessionData(pref + "denizen", stage.script);
            }

        }
        //

    }

    private class SelectDeletePrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            String text = GOLD + "- " + "Delete Quest" + " -\n";

            for (Quest quest : quests.quests) {
                text += AQUA + quest.name + YELLOW + ",";
            }

            text = text.substring(0, text.length() - 1) + "\n";
            text += YELLOW + "Enter a Quest name, or \"cancel\" to return.";

            return text;

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase(Lang.get("cmdCancel")) == false) {

                LinkedList<String> used = new LinkedList<String>();

                for (Quest quest : quests.quests) {

                    if (quest.name.equalsIgnoreCase(input) || quest.name.toLowerCase().contains(input.toLowerCase())) {

                        for (Quest q : quests.quests) {

                            if (q.neededQuests.contains(q.name)) {
                                used.add(q.name);
                            }

                        }

                        if (used.isEmpty()) {
                            context.setSessionData("delQuest", quest.name);
                            return new DeletePrompt();
                        } else {
                            ((Player) context.getForWhom()).sendMessage(RED + "The following Quests have \"" + PURPLE + context.getSessionData("delQuest") + RED + "\" as a requirement:");
                            for (String s : used) {
                                ((Player) context.getForWhom()).sendMessage(RED + "- " + DARKRED + s);
                            }
                            ((Player) context.getForWhom()).sendMessage(RED + "You must modify these Quests so that they do not use it before deleting it.");
                            return new SelectDeletePrompt();
                        }
                    }

                }

                ((Player) context.getForWhom()).sendMessage(RED + "Quest not found!");
                return new SelectDeletePrompt();

            } else {
                return new MenuPrompt();
            }

        }
    }

    private class DeletePrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {

            String text =
                    RED + "Are you sure you want to delete the Quest " + " \"" + GOLD + (String) context.getSessionData("delQuest") + RED + "\"?\n";
            text += YELLOW + Lang.get("yes") + "/" + Lang.get("no");

            return text;

        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {

            if (input.equalsIgnoreCase(Lang.get("yes"))) {
                deleteQuest(context);
                return Prompt.END_OF_CONVERSATION;
            } else if (input.equalsIgnoreCase(Lang.get("no"))) {
                return new MenuPrompt();
            } else {
                return new DeletePrompt();
            }

        }
    }

    private void deleteQuest(ConversationContext context) {

        YamlConfiguration data = new YamlConfiguration();

        try {
            data.load(questsFile);
        } catch (Exception e) {
            e.printStackTrace();
            ((Player) context.getForWhom()).sendMessage(ChatColor.RED + "Error reading Quests file.");
            return;
        }

        String quest = (String) context.getSessionData("delQuest");
        ConfigurationSection sec = data.getConfigurationSection("quests");
        for (String key : sec.getKeys(false)) {

            if (sec.getString(key + ".name").equalsIgnoreCase(quest)) {
                sec.set(key, null);
                break;
            }

        }


        try {
            data.save(questsFile);
        } catch (Exception e) {
            ((Player) context.getForWhom()).sendMessage(ChatColor.RED + "An error occurred while saving.");
            return;
        }

        quests.reloadQuests();

        context.getForWhom().sendRawMessage(WHITE + "" + BOLD + "Quest deleted! Quests and Events have been reloaded.");


    }
}
