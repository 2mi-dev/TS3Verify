package de.web.moritz.mikus.TSVerify;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class EmptyTabCompleter implements TabCompleter {

    public List<String> onTabComplete(CommandSender arg0, Command arg1, String arg2, String[] arg3) {
        return new ArrayList<String>();
    }
}