package com.killcollectitem;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class App extends JavaPlugin implements Listener {
    // @Override
    // public void onEnable() {
    //     getLogger().info("Hello, SpigotMC!");
    // }
    // @Override
    // public void onDisable() {
    //     getLogger().info("See you again, SpigotMC!");
    // }

    private Map<UUID, Inventory> killCollections = new HashMap<>();
    private FileConfiguration playerSettings;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("collect").setExecutor(this);
        getCommand("collectclear").setExecutor(this);
        getCommand("collectsetting").setExecutor(this);
        playerSettings = getConfig();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            // Get the player who was killed (victim)
            Player victim = (Player) event.getEntity();
            // Check the cause of death and find the killer
            EntityDamageByEntityEvent killerEvent = (EntityDamageByEntityEvent) victim.getLastDamageCause();

            if (killerEvent != null) {
                Player killer = null;
                // Check if the killer is a player
                if (killerEvent.getDamager() instanceof Player) {
                    killer = (Player) killerEvent.getDamager();
                } else if (killerEvent.getDamager() instanceof Arrow) {
                    // Check if cause of death was an arrow shot by a player
                    Arrow arrow = (Arrow) killerEvent.getDamager();
                    if (arrow.getShooter() instanceof Player) {
                        killer = (Player) arrow.getShooter();
                    }
                }

                if (killer != null) {
                    // Check if the killer already has a loot inventory
                    if (!killCollections.containsKey(killer.getUniqueId())) {
                        // Create a new collection inventory for the killer
                        killCollections.put(killer.getUniqueId(), Bukkit.createInventory(null, 54, "Kill Collection"));
                    }
                    // Transfer the items from the victim's inventory to the killer's collection inventory
                    Inventory collectionInventory = killCollections.get(killer.getUniqueId());
                    collectionInventory.addItem(victim.getInventory().getContents());
                    // Clears the victim's inventory
                    victim.getInventory().clear();

                    final UUID killerUUID = killer.getUniqueId();
                    final Inventory victimInventory = victim.getInventory();
                    
                    // Schedule a task to remove items from the collection inventory after 12 hours
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (killCollections.containsKey(killerUUID)) {
                                Inventory lootInventory = killCollections.get(killerUUID);
                                // Iterate through the items in the victim's inventory
                                for (ItemStack item : victimInventory.getContents()) {
                                    if (item != null) {
                                        // Removes the item from the loot inventory
                                        lootInventory.remove(item);
                                    }
                                }
                            }
                        }
                    }.runTaskLater(this, 20 * 60 * 60 * 12); // 20 ticks per second * 60 seconds per minute * 60 minutes per hour * 12 hours
                }
            }
        }
    }

    public void openLootInventory(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(null, 54, "Kill Collection");
    
        // Calculate the start and end index for items based on the page
        int pageSize = 45; // 45 slots for loot items
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, killCollections.get(player.getUniqueId()).getSize());
    
        // Populate the GUI with loot items for the current page
        for (int i = startIndex; i < endIndex; i++) {
            inventory.addItem(killCollections.get(player.getUniqueId()).getItem(i));
        }
    
        // Add the "Previous Page" button if not on the first page
        if (page > 1) {
            ItemStack previousPageButton = new ItemStack(Material.ARROW);
            ItemMeta previousPageButtonMeta = previousPageButton.getItemMeta();
            previousPageButtonMeta.setDisplayName("Previous Page");
            previousPageButton.setItemMeta(previousPageButtonMeta);
            inventory.setItem(45, previousPageButton);
        }
    
        // Add the "Clear" button in slot 49
        ItemStack clearButton = new ItemStack(Material.BARRIER); // Adjust material as needed
        ItemMeta clearButtonMeta = clearButton.getItemMeta();
        clearButtonMeta.setDisplayName("Clear Loot");
        clearButton.setItemMeta(clearButtonMeta);
        inventory.setItem(49, clearButton);
    
        // Add the "Next Page" button in slot 53
        ItemStack nextPageButton = new ItemStack(Material.ARROW);
        ItemMeta nextPageButtonMeta = nextPageButton.getItemMeta();
        nextPageButtonMeta.setDisplayName("Next Page");
        nextPageButton.setItemMeta(nextPageButtonMeta);
        inventory.setItem(53, nextPageButton);
    
        player.openInventory(inventory);
    }

    // Define a map to store the current page for each player
    private Map<Player, Integer> currentPageMap = new HashMap<>();

    // Function to get the current page the player is viewing
    private int getCurrentPage(Player player) {
        if (currentPageMap.containsKey(player)) {
            return currentPageMap.get(player);
        }
        return 1; // Default to page 1 if not found
    }

    // Function to calculate the total number of pages
    private int calculateTotalPages(Player player) {
        int lootSize = killCollections.get(player.getUniqueId()).getSize(); // Total number of loot items
        int itemsPerPage = 45; // Number of loot items displayed per page
        return (int) Math.ceil((double) lootSize / itemsPerPage);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedInventory != null && clickedInventory.getName().equals("Loot Collection")) {
            event.setCancelled(true); // Prevent items from being moved or taken

            // Check which button was clicked
            if (clickedItem != null) {
                if (clickedItem.getType() == Material.ARROW) {
                    // Check if it's the "Previous Page" or "Next Page" button
                    if (clickedItem.hasItemMeta()) {
                        ItemMeta itemMeta = clickedItem.getItemMeta();
                        if (itemMeta.getDisplayName() != null) {
                            if (itemMeta.getDisplayName().equals("Previous Page")) {
                                // Handle "Previous Page" button click
                                int currentPage = getCurrentPage(player);
                                if (currentPage > 1) {
                                    openLootInventory(player, currentPage - 1);
                                }
                            } else if (itemMeta.getDisplayName().equals("Next Page")) {
                                // Handle "Next Page" button click
                                int currentPage = getCurrentPage(player);
                                // You need to calculate the total number of pages based on your data.
                                int totalPages = calculateTotalPages(player);
                                if (currentPage < totalPages) {
                                    openLootInventory(player, currentPage + 1);
                                }
                            }
                        }
                    }
                } else if (clickedItem != null && clickedItem.getType() == Material.BARRIER) {
                    // Handle the clear action
                    if (killCollections.containsKey(player.getUniqueId())) {
                        // Clear the loot from the player's collection inventory
                        killCollections.get(player.getUniqueId()).clear();
                        player.sendMessage("Loot has been cleared from your collection.");
                        player.closeInventory(); // Close the inventory after clearing
                    } else {
                        player.sendMessage("There is no loot to clear.");
                    }
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("collect")) {
            // Check if the player has a loot inventory
            if (killCollections.containsKey(player.getUniqueId())) {
                 // Open the loot collection GUI for the player
                int currentPage = getCurrentPage(player);
                openLootInventory(player, currentPage);
            } else {
                player.sendMessage("You do not have any loot to collect");
            }    
        } else if (command.getName().equalsIgnoreCase("collectclear")) {
            
            // if (args.length == 1) {
            //     if (killCollections.containsKey(player.getUniqueId())) {
            //         Player targetPlayer = getServer().getPlayerExact(args[0]);

            //         if (targetPlayer != null) {
            //             // Check if targeted player has a collection inventory
            //             if (killCollections.containsKey(targetPlayer.getUniqueId())) {
            //                 killCollections.get(targetPlayer.getUniqueId()).clear();
            //                 player.sendMessage("Cleared " + args[0] + "'s loot.");
            //             } else {
            //                 player.sendMessage(args[0] + " has no items to clear.");;
            //             }
            //         } else {
            //             player.sendMessage("Player not found");
            //         }
            //     } else {
            //         player.sendMessage("You don't have access to this loot!");
            //     }
            // } else {
            //     player.sendMessage("Usage: /collectclear <player_name>");
            // }
            
            // Check's if the player has a loot inventory
            if (killCollections.containsKey(player.getUniqueId())) {
                killCollections.get(player.getUniqueId()).clear();
                player.sendMessage("Cleared all loot items.");
            } else {
                player.sendMessage("You have no loot items to clear.");
            }
        } else if(command.getName().equalsIgnoreCase("collectsetting")) {
            // Configure collected items in collection inventory
            if (args.length >= 1) {
                String setting = args[0].toLowerCase();

                if (setting.equals("add")) {
                    if (args.length >= 2) {
                        String itemName = args[1].toUpperCase();
                        // Check if the specified item name is valid
                        try {
                            Material material = Material.valueOf(itemName);
                            List<String> collectedItems = playerSettings.getStringList("collected_items." + player.getUniqueId());
                            collectedItems.add(material.toString());
                            playerSettings.set("collected_items." + player.getUniqueId(), collectedItems);
                            saveConfig();
                            player.sendMessage("Added " + material.toString() + " to your collection settings.");
                        } catch (IllegalArgumentException e) {
                            player.sendMessage("Invalid item name.");
                        }
                    } else {
                        player.sendMessage("Usage: /collectsetting add <item>");
                    }
                } else if (setting.equals("remove")) {
                    if (args.length >= 2) {
                        String itemName = args[1].toUpperCase();
                        // Check if the specified name is valid
                        try {
                            Material material = Material.valueOf(itemName);
                            List<String> collectedItems = playerSettings.getStringList("collected_items" + player.getUniqueId());
                            collectedItems.remove(material.toString());
                            saveConfig();
                            player.sendMessage("Removed " + material.toString() + " from your collection settings.");
                        } catch (IllegalArgumentException e) {
                            player.sendMessage("Invalid item name or item not found in your list");
                        } 
                    } else {
                        player.sendMessage("Usage: /collectsetting remove <item>");
                    }
                } else if (setting.equals("list")) {
                    // List the player's current loot settings
                    List<String> collectedItems = playerSettings.getStringList("collected_items." + player.getUniqueId());
                    player.sendMessage("Your collection settings: " + collectedItems.toString());
                } else {
                    player.sendMessage("Usage: /collectsetting <add/remove/list>");
                }
            } else {
                player.sendMessage("Usage: /collectsetting <add/remove/list>");
            }  
        }
        return true;
    }
}
